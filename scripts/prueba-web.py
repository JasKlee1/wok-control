"""
Prueba de humo de la interfaz web: inicia sesión por formulario (cookie HttpOnly + CSRF) con cada
rol y abre sus pantallas a través del gateway, verificando que se rendericen sin errores y que las
pantallas de otros roles redirijan a "acceso denegado".

Requiere haber ejecutado scripts/prueba-flujo.py (usa .run/credenciales-prueba.txt) y el usuario de
plataforma de .env. Uso: python scripts/prueba-web.py
"""
import http.client
import http.cookiejar
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "http://localhost:8080"
RAIZ = Path(__file__).resolve().parent.parent
FALLOS = []


class SinRedireccion(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def sesion():
    jar = http.cookiejar.CookieJar()
    return jar, urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), SinRedireccion())


def pedir(opener, ruta, datos=None):
    req = urllib.request.Request(BASE + ruta, data=urllib.parse.urlencode(datos).encode() if datos else None)
    try:
        r = opener.open(req, timeout=30)
        try:
            cuerpo = r.read()
        except http.client.IncompleteRead as e:  # la respuesta se cortó: suele indicar un error de plantilla
            return 599, e.partial.decode("utf8", "replace"), None
        return r.status, cuerpo.decode("utf8", "replace"), r.headers.get("Location")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf8", "replace"), e.headers.get("Location")


def login(usuario, clave):
    jar, op = sesion()
    st, html, _ = pedir(op, "/login")
    csrf = re.search(r'name="_csrf" value="([^"]+)"', html)
    if st != 200 or not csrf:
        raise SystemExit(f"No se obtuvo el formulario de login (status {st})")
    st, _, destino = pedir(op, "/login", {"username": usuario, "password": clave, "_csrf": csrf.group(1)})
    ok = st == 302 and any(c.name == "WOK_TOKEN" for c in jar)
    marcar(f"login web de {usuario} -> {destino}", ok)
    return op


def marcar(texto, ok, detalle=""):
    print(f"  [{'OK ' if ok else 'FALLA'}] {texto}" + (f" -> {detalle}" if detalle and not ok else ""))
    if not ok:
        FALLOS.append(texto)


def revisar(op, rutas, prohibidas=()):
    for ruta in rutas:
        st, html, destino = pedir(op, ruta)
        titulo = re.search(r"<title>(.*?)</title>", html or "", re.S)
        titulo = titulo.group(1).strip() if titulo else ""
        ok = st == 200 and not titulo.startswith("Error") and "wok-sidebar" in html
        marcar(f"{ruta:<30} {st} {titulo}", ok, destino or html[:200])
    for ruta in prohibidas:
        st, _, destino = pedir(op, ruta)
        marcar(f"{ruta:<30} denegado", st == 302 and destino and destino.endswith("/acceso-denegado"), f"{st} {destino}")


def main():
    env = dict(l.split("=", 1) for l in (RAIZ / ".env").read_text(encoding="utf8").splitlines() if l and not l.startswith("#") and "=" in l)
    cred = {}
    for linea in (RAIZ / ".run" / "credenciales-prueba.txt").read_text(encoding="utf8").splitlines():
        usuario, resto = linea.split(" (", 1)
        rol = resto.split(")")[0]
        cred[rol] = (usuario, resto.split(" / ")[1])

    print("Sin sesión")
    _, op = sesion()
    st, _, destino = pedir(op, "/dashboard")
    marcar("una página protegida redirige al login", st == 302 and "/login" in (destino or ""), destino)

    casos = {
        "ADMIN_PLATAFORMA": ((env["WOK_BOOTSTRAP_ADMIN_USERNAME"], env["WOK_BOOTSTRAP_ADMIN_PASSWORD"]),
                             ["/dashboard", "/plataforma/usuarios", "/plataforma/usuarios/nuevo", "/plataforma/roles",
                              "/plataforma/roles/nuevo", "/plataforma/parametros", "/plataforma/auditoria", "/perfil", "/docs-api"],
                             ["/caja/cuentas", "/menu/platos/nuevo"]),
        "ADMINISTRADOR": (cred["ADMINISTRADOR"], ["/dashboard", "/reportes", "/menu/platos", "/menu/platos/nuevo", "/menu/categorias",
                                                  "/menu/combos", "/menu/combos/nuevo", "/mesas", "/mesas/nueva", "/pedidos",
                                                  "/inventario/insumos", "/inventario/insumos/nuevo", "/inventario/movimientos",
                                                  "/inventario/alertas", "/proveedores", "/proveedores/nuevo", "/compras/ordenes",
                                                  "/compras/ordenes/nueva", "/caja/comprobantes", "/caja/cierres", "/caja/descuentos"],
                          ["/plataforma/usuarios", "/caja/cuentas", "/cocina"]),
        "MESERO": (cred["MESERO"], ["/dashboard", "/mesas", "/pedidos", "/menu/platos", "/menu/combos"],
                   ["/cocina", "/caja/cuentas", "/inventario/insumos", "/menu/platos/nuevo"]),
        "COCINERO": (cred["COCINERO"], ["/dashboard", "/cocina", "/pedidos", "/inventario/alertas"], ["/mesas/nueva", "/caja/cuentas"]),
        "CAJERO": (cred["CAJERO"], ["/dashboard", "/caja/cuentas", "/caja/comprobantes", "/caja/cierres", "/mesas"],
                   ["/caja/descuentos", "/proveedores"]),
        "INVENTARIO": (cred["INVENTARIO"], ["/dashboard", "/inventario/insumos", "/inventario/movimientos", "/inventario/alertas",
                                            "/proveedores", "/compras/ordenes", "/compras/ordenes/nueva"],
                       ["/caja/cuentas", "/pedidos", "/plataforma/usuarios"]),
    }
    for rol, ((usuario, clave), rutas, prohibidas) in casos.items():
        print(f"\nRol {rol}")
        op = login(usuario, clave)
        revisar(op, rutas, prohibidas)

    # detalle de registros reales: abre el primero de cada lista como administrador
    print("\nDetalles (administrador)")
    op = login(*cred["ADMINISTRADOR"])
    for lista, patron in [("/menu/platos", r'href="(/menu/platos/[0-9a-f]{24})"'), ("/inventario/insumos", r'href="(/inventario/insumos/[0-9a-f]{24})"'),
                          ("/proveedores", r'href="(/proveedores/[0-9a-f]{24})"'), ("/compras/ordenes", r'href="(/compras/ordenes/[0-9a-f]{24})"'),
                          ("/caja/comprobantes", r'href="(/caja/comprobantes/[0-9a-f]{24})"'), ("/caja/cierres", r'href="(/caja/cierres/[0-9a-f]{24})"'),
                          ("/pedidos?fecha=", r'href="(/pedidos/[0-9a-f]{24})"')]:
        _, html, _ = pedir(op, lista)
        m = re.search(patron, html)
        if m:
            revisar(op, [m.group(1)])
        else:
            marcar(f"{lista}: hay al menos un registro para abrir", False)
    op = login(*cred["CAJERO"])
    _, html, _ = pedir(op, "/mesas")
    m = re.search(r'/mesas/([0-9a-f]{24})/editar', html) or re.search(r'href="/mesas/([0-9a-f]{24})"', html)
    _, html_mesas, _ = pedir(login(*cred["MESERO"]), "/mesas")
    mid = re.search(r'/pedidos/nuevo\?mesaId=([0-9a-f]{24})', html_mesas)
    if mid:
        revisar(login(*cred["MESERO"]), ["/pedidos/nuevo?mesaId=" + mid.group(1)])
        revisar(op, ["/caja/cuentas/" + mid.group(1)])

    print()
    if FALLOS:
        print(f"RESULTADO: {len(FALLOS)} verificaciones fallaron")
        sys.exit(1)
    print("RESULTADO: todas las pantallas se renderizaron correctamente")


if __name__ == "__main__":
    main()
