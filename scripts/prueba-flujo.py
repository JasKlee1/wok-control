"""
Prueba de extremo a extremo de Wok Control a través del API Gateway (http://localhost:8080).

Recorre el flujo de negocio completo con usuarios reales de cada rol:
  usuarios y roles -> insumo y entrada de inventario -> carta con receta -> mesa -> pedido ->
  cocina -> entrega -> cobro (idempotente) -> consumo de inventario -> compra a proveedor ->
  recepción -> cierre de caja -> panel de control, y verifica controles de seguridad.

Requisitos: el sistema en marcha (scripts/iniciar.ps1) y, en .env, WOK_BOOTSTRAP_ADMIN_USERNAME y
WOK_BOOTSTRAP_ADMIN_PASSWORD del administrador de plataforma. Crea datos con el sufijo de la
ejecución; las credenciales de los usuarios de prueba se guardan en .run/credenciales-prueba.txt
(carpeta ignorada por Git). Uso:  python scripts/prueba-flujo.py
"""
import json
import secrets
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

BASE = "http://localhost:8080"
RAIZ = Path(__file__).resolve().parent.parent
FALLOS = []


def env():
    datos = {}
    for linea in (RAIZ / ".env").read_text(encoding="utf8").splitlines():
        if linea and not linea.startswith("#") and "=" in linea:
            k, v = linea.split("=", 1)
            datos[k.strip()] = v.strip()
    return datos


def llamar(metodo, ruta, token=None, cuerpo=None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            texto = r.read().decode()
            return r.status, json.loads(texto) if texto else None
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        try:
            return e.code, json.loads(texto) if texto else None
        except json.JSONDecodeError:
            return e.code, texto


def verificar(descripcion, condicion, detalle=""):
    estado = "OK " if condicion else "FALLA"
    print(f"  [{estado}] {descripcion}" + (f" -> {detalle}" if detalle and not condicion else ""))
    if not condicion:
        FALLOS.append(descripcion)


def exigir(metodo, ruta, token, cuerpo, esperado, descripcion):
    st, r = llamar(metodo, ruta, token, cuerpo)
    verificar(f"{descripcion} ({metodo} {ruta} -> {st})", st == esperado, r)
    if st != esperado:
        print("\nSe detiene la prueba porque un paso necesario falló.")
        sys.exit(1)
    return r


def login(usuario, clave):
    return exigir("POST", "/api/auth/login", None, {"username": usuario, "password": clave}, 200, f"login {usuario}")["token"]


def main():
    e = env()
    sufijo = secrets.token_hex(3)
    clave = "Prueba" + secrets.token_hex(4) + "9"
    print(f"Ejecución {sufijo}")

    print("\n1. Plataforma: usuarios por rol")
    plataforma = login(e["WOK_BOOTSTRAP_ADMIN_USERNAME"], e["WOK_BOOTSTRAP_ADMIN_PASSWORD"])
    roles = {"admin": "ADMINISTRADOR", "mesero": "MESERO", "cocina": "COCINERO", "caja": "CAJERO", "bodega": "INVENTARIO"}
    tokens = {}
    for alias, rol in roles.items():
        u = f"{alias}-{sufijo}"
        exigir("POST", "/api/usuarios", plataforma, {"username": u, "nombre": f"{rol.title()} prueba {sufijo}",
                                                      "password": clave, "roles": [rol]}, 201, f"crear usuario {rol}")
        tokens[alias] = login(u, clave)
    (RAIZ / ".run").mkdir(exist_ok=True)
    (RAIZ / ".run" / "credenciales-prueba.txt").write_text(
        "\n".join(f"{a}-{sufijo} ({r}) / {clave}" for a, r in roles.items()) + "\n", encoding="utf8")
    st, _ = llamar("GET", "/api/usuarios", tokens["admin"])
    verificar("el administrador operativo NO gestiona usuarios (403)", st == 403)

    print("\n2. Inventario: insumo y entrada inicial")
    arroz = exigir("POST", "/api/insumos", tokens["bodega"], {"nombre": f"Arroz jazmín {sufijo}", "categoria": "GRANOS",
                                                             "unidad": "KG", "stockMinimo": 2, "perecedero": False}, 201, "crear insumo")
    verificar("el insumo nace con existencia 0", float(arroz["cantidadDisponible"]) == 0)
    m = exigir("POST", f"/api/insumos/{arroz['id']}/movimientos", tokens["bodega"],
               {"tipo": "ENTRADA", "cantidad": 10, "costoUnitario": 4000, "motivo": "Carga inicial"}, 201, "entrada de 10 kg")
    verificar("saldo 0 -> 10 registrado en el movimiento", float(m["saldoAnterior"]) == 0 and float(m["saldoNuevo"]) == 10)
    st, r = llamar("POST", f"/api/insumos/{arroz['id']}/movimientos", tokens["bodega"], {"tipo": "SALIDA", "cantidad": 99, "motivo": "Prueba sin stock"})
    verificar("salida mayor al stock rechazada (422)", st == 422, r)
    st, _ = llamar("POST", f"/api/insumos/{arroz['id']}/movimientos", tokens["mesero"], {"tipo": "ENTRADA", "cantidad": 1})
    verificar("el mesero no registra movimientos (403)", st == 403)

    print("\n3. Carta: categoría, plato con receta y mesa")
    cat = exigir("POST", "/api/menu/categorias", tokens["admin"], {"nombre": f"Arroces {sufijo}", "orden": 1, "activa": True}, 201, "crear categoría")
    plato = exigir("POST", "/api/menu/platos", tokens["admin"], {"nombre": f"Arroz frito {sufijo}", "categoriaId": cat["id"],
                                                               "precio": 25000, "disponible": True, "picanteBase": 1, "alergenos": ["SOYA"],
                                                               "tiempoPreparacionMin": 12,
                                                               "receta": [{"insumoId": arroz["id"], "cantidad": 0.25}]}, 201, "crear plato con receta")
    verificar("la receta copia el nombre del insumo desde inventario", plato["receta"][0]["nombreInsumo"].startswith("Arroz jazmín"))
    numero_mesa = 500 + secrets.randbelow(400)
    mesa = exigir("POST", "/api/mesas", tokens["admin"], {"numero": numero_mesa, "capacidad": 4, "zona": "SALON", "activa": True}, 201, "crear mesa")

    print("\n4. Pedido: mesero -> cocina -> entrega")
    st, r = llamar("POST", "/api/pedidos", tokens["mesero"], {"mesaId": mesa["id"], "items": []})
    verificar("pedido sin productos rechazado (400)", st == 400)
    pedido = exigir("POST", "/api/pedidos", tokens["mesero"], {"mesaId": mesa["id"], "observaciones": "Cumpleaños", "alergias": ["MANI"],
                                                               "items": [{"tipo": "PLATO", "productoId": plato["id"], "cantidad": 2,
                                                                          "nivelPicante": 2, "notas": "sin cebolla"}]}, 201, "registrar pedido")
    verificar("precio copiado de la carta (2 × 25.000 = 50.000)", float(pedido["total"]) == 50000)
    st, mesas = llamar("GET", "/api/mesas", tokens["mesero"])
    verificar("la mesa queda ocupada", any(x["id"] == mesa["id"] and x["estado"] == "OCUPADA" for x in mesas))
    st, r = llamar("PATCH", f"/api/pedidos/{pedido['id']}/estado", tokens["cocina"], {"estado": "ENVIADO"})
    verificar("el cocinero no envía pedidos (403)", st == 403)
    exigir("PATCH", f"/api/pedidos/{pedido['id']}/estado", tokens["mesero"], {"estado": "ENVIADO"}, 200, "enviar a cocina")
    exigir("PATCH", f"/api/pedidos/{pedido['id']}/estado", tokens["mesero"], {"estado": "ENVIADO"}, 422, "reenviar a cocina rechazado")
    _, ins = llamar("GET", f"/api/insumos/{arroz['id']}", tokens["bodega"])
    verificar("inventario descontado una sola vez: 10 - 0,5 = 9,5 kg", float(ins["cantidadDisponible"]) == 9.5, ins["cantidadDisponible"])
    _, comandas = llamar("GET", "/api/cocina/comandas", tokens["cocina"])
    verificar("la comanda aparece en cocina", any(c["id"] == pedido["id"] for c in comandas))
    item = pedido["items"][0]["id"]
    st, _ = llamar("PATCH", f"/api/pedidos/{pedido['id']}/items/{item}/estado", tokens["cocina"], {"estado": "LISTO"})
    verificar("no se salta de PENDIENTE a LISTO (422)", st == 422)
    exigir("PATCH", f"/api/pedidos/{pedido['id']}/items/{item}/estado", tokens["cocina"], {"estado": "EN_PREPARACION"}, 200, "en preparación")
    st, _ = llamar("PATCH", f"/api/pedidos/{pedido['id']}/estado", tokens["mesero"], {"estado": "CANCELADO", "motivo": "Prueba de cancelación"})
    verificar("no se cancela un pedido en preparación (422)", st == 422)
    exigir("PATCH", f"/api/pedidos/{pedido['id']}/items/{item}/estado", tokens["cocina"], {"estado": "LISTO"}, 200, "listo")
    p = exigir("PATCH", f"/api/pedidos/{pedido['id']}/items/{item}/estado", tokens["mesero"], {"estado": "ENTREGADO"}, 200, "entregado")
    verificar("pedido ENTREGADO", p["estado"] == "ENTREGADO")

    print("\n5. Caja: cuenta, cobro idempotente, comprobante interno")
    cuenta = exigir("GET", f"/api/cuentas/mesa/{mesa['id']}", tokens["caja"], None, 200, "consultar cuenta")
    verificar("total calculado en servidor: 50.000 + 8% INC = 54.000", float(cuenta["total"]) == 54000, cuenta["total"])
    clave_cobro = str(uuid.uuid4())
    cobro = {"mesaId": mesa["id"], "pagos": [{"metodo": "EFECTIVO", "monto": 60000}], "claveIdempotencia": clave_cobro, "total": 1}
    st, r = llamar("POST", "/api/facturas", tokens["caja"], {**cobro, "descuento": {"porcentaje": 10}})
    verificar("descuento sin autorización rechazado (403)", st == 403, r)
    f1 = exigir("POST", "/api/facturas", tokens["caja"], cobro, 201, "cobrar")
    verificar("el total enviado por el cliente se ignora (total = 54.000)", float(f1["total"]) == 54000)
    verificar("cambio de 6.000 en efectivo", float(f1["cambio"]) == 6000)
    verificar("documento identificado como comprobante interno", f1["tipoDocumento"] == "COMPROBANTE_INTERNO_DE_VENTA")
    f2 = exigir("POST", "/api/facturas", tokens["caja"], cobro, 201, "repetir el mismo cobro")
    verificar("el reintento devuelve el MISMO comprobante (sin doble cobro)", f1["id"] == f2["id"])
    _, ped = llamar("GET", f"/api/pedidos/{pedido['id']}", tokens["mesero"])
    verificar("pedido marcado PAGADO por facturación", ped["estado"] == "PAGADO", ped["estado"])
    _, mesas = llamar("GET", "/api/mesas", tokens["mesero"])
    verificar("la mesa queda disponible", any(x["id"] == mesa["id"] and x["estado"] == "DISPONIBLE" for x in mesas))

    print("\n6. Abastecimiento: proveedor, orden de compra y recepción")
    nit = str(900000000 + secrets.randbelow(99999999))
    prov = exigir("POST", "/api/proveedores", tokens["admin"], {"nit": nit, "razonSocial": f"Distribuidora Oriente {sufijo} SAS",
                                                              "nombreComercial": f"Oriente {sufijo}", "telefono": "3001234567",
                                                              "insumoIds": [arroz["id"]], "categorias": ["GRANOS"]}, 201, "registrar proveedor")
    oc = exigir("POST", "/api/ordenes-compra", tokens["bodega"], {"proveedorId": prov["id"],
                                                                "items": [{"insumoId": arroz["id"], "cantidad": 20, "costoUnitario": 4600}]},
                201, "crear orden de compra")
    exigir("POST", f"/api/ordenes-compra/{oc['id']}/envio", tokens["bodega"], None, 200, "enviar orden")
    _, ins = llamar("GET", f"/api/insumos/{arroz['id']}", tokens["bodega"])
    verificar("enviar la orden no modifica inventario", float(ins["cantidadDisponible"]) == 9.5)
    r = exigir("POST", f"/api/ordenes-compra/{oc['id']}/recepcion", tokens["bodega"],
               {"lineas": [{"insumoId": arroz["id"], "cantidadRecibida": 18, "lote": f"L-{sufijo}"}], "observaciones": "Faltaron 2 kg"},
               200, "confirmar recepción parcial")
    verificar("orden RECIBIDA y sincronizada", r["estado"] == "RECIBIDA" and r["recepcion"]["sincronizadaInventario"])
    _, ins = llamar("GET", f"/api/insumos/{arroz['id']}", tokens["bodega"])
    verificar("inventario 9,5 + 18 = 27,5 kg", float(ins["cantidadDisponible"]) == 27.5, ins["cantidadDisponible"])
    _, movs = llamar("GET", f"/api/inventario/movimientos?insumoId={arroz['id']}", tokens["bodega"])
    tipos = sorted(m["tipo"] for m in movs["content"])
    verificar("movimientos trazados: ENTRADA, CONSUMO, RECEPCION_COMPRA", tipos == ["CONSUMO", "ENTRADA", "RECEPCION_COMPRA"], tipos)

    print("\n7. Cierre de caja y panel")
    pre = exigir("GET", "/api/caja/cierres/preliminar", tokens["caja"], None, 200, "preliminar de cierre")
    verificar("el sistema registra 54.000 netos en efectivo", float(pre["sistemaPorMetodo"]["EFECTIVO"]) == 54000)
    st, r = llamar("POST", "/api/caja/cierres", tokens["caja"], {"declarado": {"EFECTIVO": 50000}})
    verificar("diferencia sin observación rechazada (422)", st == 422, r)
    c = exigir("POST", "/api/caja/cierres", tokens["caja"], {"declarado": {"EFECTIVO": 54000}}, 201, "cerrar caja")
    verificar("cierre cuadrado (diferencia 0)", float(c["diferenciaTotal"]) == 0)
    st, _ = llamar("POST", f"/api/facturas/{f1['id']}/anulacion", tokens["admin"], {"motivo": "Prueba de anulación"})
    verificar("comprobante ya cerrado no se puede anular (422)", st == 422)
    d = exigir("GET", "/api/reportes/dashboard", tokens["admin"], None, 200, "panel del administrador")
    verificar("panel con ventas reales del día", d["ventas"]["datos"] is not None and float(d["ventas"]["datos"]["ventasTotales"]) >= 54000)
    d = exigir("GET", "/api/reportes/dashboard", tokens["mesero"], None, 200, "panel del mesero")
    verificar("el panel del mesero no incluye ventas", d.get("ventas") is None)

    print("\n8. Seguridad perimetral")
    st, _ = llamar("GET", "/api/menu/platos")
    verificar("API sin token -> 401", st == 401)
    st, _ = llamar("POST", "/api/inventario/consumos", tokens["admin"], {"referencia": "X", "lineas": []})
    verificar("endpoint interno bloqueado en el gateway -> 403", st == 403)
    st, _ = llamar("GET", "/api/usuarios", tokens["mesero"][:-4] + "abcd")
    verificar("token alterado -> 401", st == 401)

    print()
    if FALLOS:
        print(f"RESULTADO: {len(FALLOS)} verificaciones fallaron")
        for f in FALLOS:
            print("  - " + f)
        sys.exit(1)
    print("RESULTADO: todas las verificaciones pasaron")


if __name__ == "__main__":
    main()
