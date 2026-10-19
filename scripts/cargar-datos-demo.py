"""
Carga datos de demostración de "Restaurante Éxito Chino" en Wok Control usando la API real
(a través del gateway), de modo que todo queda coherente: el inventario se descuenta con los
pedidos, los comprobantes se calculan en el servidor y cada acción queda en la auditoría.

Contenido: parámetros del restaurante, usuarios de cada rol, ~30 insumos con existencias y lotes,
5 proveedores, carta completa (Grande y 3/4) con recetas, combos (bandejas), 12 mesas, órdenes de
compra en distintos estados, movimientos de inventario (merma, ajuste), pedidos del día en todos los
estados y cobros con distintos métodos de pago.

Requisitos: sistema en marcha (scripts/iniciar.ps1) y en .env WOK_BOOTSTRAP_ADMIN_USERNAME/PASSWORD
(administrador de plataforma) y WOK_DEMO_PASSWORD (clave de los usuarios demo). Se ejecuta una sola vez; si la carta ya existe, se detiene.
Uso:  python scripts/cargar-datos-demo.py
Las credenciales de los usuarios creados se guardan en .run/credenciales-demo.txt (ignorado por Git).
"""
import json
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, timedelta
from pathlib import Path

BASE = "http://localhost:8080"
RAIZ = Path(__file__).resolve().parent.parent
ENV = {k.strip(): v.strip() for k, v in (l.split("=", 1) for l in (RAIZ / ".env").read_text(encoding="utf8").splitlines()
                                          if l and not l.startswith("#") and "=" in l)}
# La clave de los usuarios demo es un secreto local: se define en .env (WOK_DEMO_PASSWORD), nunca en Git
CLAVE_DEMO = ENV.get("WOK_DEMO_PASSWORD") or sys.exit("Defina WOK_DEMO_PASSWORD en .env antes de cargar los datos demo")


# ---------------------------------------------------------------- utilidades HTTP
def api(metodo, ruta, token=None, cuerpo=None, esperado=(200, 201, 204)):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo, headers={"Content-Type": "application/json"})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            texto = r.read().decode()
            st, res = r.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        st, res = e.code, texto
    if st not in esperado:
        raise SystemExit(f"\nError {st} en {metodo} {ruta}: {res}")
    return res


def login(usuario, clave):
    return api("POST", "/api/auth/login", None, {"username": usuario, "password": clave})["token"]


def paso(texto):
    print(f"\n== {texto}")


# ---------------------------------------------------------------- insumos
# nombre: (categoria, unidad, minimo, costo, stock inicial, perecedero)
INSUMOS = {
    "Arroz blanco": ("GRANOS", "KG", 25, 4200, 120, False),
    "Pollo entero": ("PROTEINAS", "UNIDAD", 10, 16000, 40, True),
    "Pierna pernil": ("PROTEINAS", "UNIDAD", 20, 4500, 80, True),
    "Alas de pollo": ("PROTEINAS", "UNIDAD", 30, 1200, 150, True),
    "Pechuga de pollo": ("PROTEINAS", "KG", 5, 17000, 25, True),
    "Camarón": ("PROTEINAS", "KG", 6, 48000, 8, True),
    "Mariscos mixtos": ("PROTEINAS", "KG", 4, 42000, 10, True),
    "Carne de res": ("PROTEINAS", "KG", 6, 32000, 25, True),
    "Carne de cerdo": ("PROTEINAS", "KG", 5, 22000, 18, True),
    "Costilla de cerdo": ("PROTEINAS", "KG", 4, 20000, 12, True),
    "Jamón": ("PROTEINAS", "KG", 3, 24000, 8, True),
    "Filete de pescado": ("PROTEINAS", "KG", 3, 28000, 8, True),
    "Huevos": ("PROTEINAS", "UNIDAD", 60, 550, 180, False),
    "Papa a la francesa": ("VEGETALES", "KG", 15, 6500, 50, False),
    "Espagueti": ("FIDEOS", "KG", 8, 7000, 30, False),
    "Fideo chow mein": ("FIDEOS", "KG", 8, 9000, 25, False),
    "Repollo": ("VEGETALES", "KG", 8, 2500, 20, True),
    "Zanahoria": ("VEGETALES", "KG", 6, 2800, 18, True),
    "Cebolla larga": ("VEGETALES", "KG", 4, 4500, 10, True),
    "Raíz china": ("VEGETALES", "KG", 4, 6000, 9, True),
    "Pimentón": ("VEGETALES", "KG", 3, 6000, 6, True),
    "Limón": ("VEGETALES", "KG", 2, 4000, 5, True),
    "Salsa de soya": ("SALSAS", "L", 6, 9000, 20, False),
    "Salsa agridulce": ("SALSAS", "L", 4, 14000, 10, False),
    "Salsa de tomate": ("SALSAS", "L", 4, 8000, 12, False),
    "Aceite vegetal": ("ACEITES", "L", 20, 9500, 60, False),
    "Harina para apanar": ("GRANOS", "KG", 6, 5000, 20, False),
    "Masa para lumpia": ("OTROS", "UNIDAD", 40, 600, 25, False),
    "Crema de leche": ("LACTEOS", "L", 4, 12000, 9, True),
    "Caja desechable": ("DESECHABLES", "UNIDAD", 100, 700, 400, False),
}


# ---------------------------------------------------------------- recetas (por porción Grande)
def suma(*partes):
    r = {}
    for p in partes:
        for k, v in p.items():
            r[k] = round(r.get(k, 0) + v, 4)
    return r


ARROZ = {"Arroz blanco": 0.45, "Huevos": 1, "Salsa de soya": 0.03, "Aceite vegetal": 0.03, "Cebolla larga": 0.03, "Raíz china": 0.04}
ARROZ_34 = {k: round(v * 0.75, 4) for k, v in ARROZ.items()}
PAPA = {"Papa a la francesa": 0.25, "Aceite vegetal": 0.05}
CAJA = {"Caja desechable": 1}
CHOWMEIN = {"Fideo chow mein": 0.3, "Repollo": 0.1, "Zanahoria": 0.05, "Cebolla larga": 0.03, "Salsa de soya": 0.04, "Aceite vegetal": 0.03}
SPAGUETTI = {"Espagueti": 0.3, "Salsa de tomate": 0.08, "Repollo": 0.05, "Zanahoria": 0.05, "Aceite vegetal": 0.03}
CHOWSUEY = {"Repollo": 0.2, "Zanahoria": 0.1, "Raíz china": 0.1, "Cebolla larga": 0.04, "Pimentón": 0.05, "Salsa de soya": 0.04}
POLLO_FRITO = {"Pollo entero": 1, "Harina para apanar": 0.05, "Aceite vegetal": 0.1}
PERNIL4 = {"Pierna pernil": 4, "Harina para apanar": 0.1, "Aceite vegetal": 0.08}
PROT = {
    "3 carnes": {"Pechuga de pollo": 0.08, "Carne de res": 0.08, "Carne de cerdo": 0.08},
    "exito": {"Camarón": 0.06, "Pechuga de pollo": 0.07, "Carne de res": 0.06, "Jamón": 0.05, "Pierna pernil": 1},
    "7 carnes": {"Camarón": 0.05, "Pechuga de pollo": 0.06, "Carne de res": 0.06, "Carne de cerdo": 0.05, "Jamón": 0.04,
                 "Mariscos mixtos": 0.05, "Huevos": 2},
    "mariscos": {"Mariscos mixtos": 0.2},
    "camarones": {"Camarón": 0.2},
    "pollo y camarones": {"Pechuga de pollo": 0.1, "Camarón": 0.1},
    "pollo": {"Pechuga de pollo": 0.2},
    "res": {"Carne de res": 0.2},
    "verduras": {"Repollo": 0.1, "Zanahoria": 0.08, "Pimentón": 0.04},
}


def escalar(receta, f):
    return {k: round(v * f, 4) for k, v in receta.items()}


# categoría -> lista de (nombre, precio grande, precio 3/4 o None, descripción, receta grande, alérgenos, picante, minutos)
def carta():
    c = {}
    c["Arroz Oriental"] = [
        ("Arroz 3 Carnes", 42000, 32000, "Arroz chino tradicional con pollo, res y cerdo", suma(ARROZ, PROT["3 carnes"]), ["SOYA", "HUEVO"], 0, 15),
        ("Porción Arroz Chino", 20000, None, "Porción de arroz chino", escalar(ARROZ, 0.6), ["SOYA", "HUEVO"], 0, 8),
        ("Arroz Éxito Chino", 47000, 39000, "Camarón, pollo, res, jamón y pedazo de pernil", suma(ARROZ, PROT["exito"]), ["SOYA", "HUEVO", "MARISCOS"], 0, 18),
        ("Arroz 7 Carnes", 58000, 48000, "Siete carnes con huevos de codorniz", suma(ARROZ, PROT["7 carnes"]), ["SOYA", "HUEVO", "MARISCOS"], 0, 20),
        ("Arroz Solo Mariscos", 60000, 50000, "Arroz chino con mariscos", suma(ARROZ, PROT["mariscos"]), ["SOYA", "HUEVO", "MARISCOS"], 0, 18),
        ("Arroz Solo Camarones", 60000, 50000, "Arroz chino con camarones", suma(ARROZ, PROT["camarones"]), ["SOYA", "HUEVO", "MARISCOS"], 0, 18),
        ("Arroz Solo Pollo y Camarones", 60000, 50000, "Arroz chino con pollo y camarones", suma(ARROZ, PROT["pollo y camarones"]), ["SOYA", "HUEVO", "MARISCOS"], 0, 18),
        ("Arroz Solo Pollo", 50000, 40000, "Arroz chino con pollo", suma(ARROZ, PROT["pollo"]), ["SOYA", "HUEVO"], 0, 15),
        ("Arroz Solo Res", 45000, 37000, "Arroz chino con carne de res", suma(ARROZ, PROT["res"]), ["SOYA", "HUEVO"], 0, 15),
        ("Agridulce Éxito Chino", 58000, 50000, "Pollo apanado frito en salsa agridulce + 3/4 arroz + papa",
         suma({"Pechuga de pollo": 0.25, "Harina para apanar": 0.05, "Salsa agridulce": 0.08, "Aceite vegetal": 0.05}, ARROZ_34, PAPA),
         ["SOYA", "HUEVO", "GLUTEN"], 0, 20),
    ]
    c["Pollo"] = [
        ("El Gran Combo 1", 68000, 58000, "Pollo entero frito + arroz chino grande + papa francesa", suma(POLLO_FRITO, ARROZ, PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN"], 0, 25),
        ("El Gran Combo 2", 70000, 59000, "4 piernas pernil apanado + arroz grande + papa francesa", suma(PERNIL4, ARROZ, PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN"], 0, 25),
        ("El Gran Combo 3", 78000, 63000, "Pollo entero frito + arroz grande 7 carnes + papa francesa", suma(POLLO_FRITO, ARROZ, PROT["7 carnes"], PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN", "MARISCOS"], 0, 28),
        ("El Gran Combo 4", 79000, 68000, "4 piernas pernil apanado + arroz grande 7 carnes + papa francesa", suma(PERNIL4, ARROZ, PROT["7 carnes"], PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN", "MARISCOS"], 0, 28),
        ("Pollo Entero 1", 60000, None, "Pollo entero frito + 3/4 caja arroz chino + papa francesa", suma(POLLO_FRITO, ARROZ_34, PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN"], 0, 22),
        ("Pollo Entero 2", 62000, None, "4 piernas pernil apanado + 3/4 caja arroz chino + papa francesa", suma(PERNIL4, ARROZ_34, PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN"], 0, 22),
        ("Pollo Entero + Papa 1", 40000, 30000, "Pollo entero frito + papa francesa", suma(POLLO_FRITO, PAPA), ["GLUTEN"], 0, 18),
        ("Pollo Entero + Papa 2", 43000, 35000, "4 piernas pernil apanado + papa francesa", suma(PERNIL4, PAPA), ["GLUTEN"], 0, 18),
        ("Valenciana", 53000, 45000, "1/2 pollo + arroz grande o 3/4 de arroz chino", suma(escalar(POLLO_FRITO, 0.5), ARROZ), ["SOYA", "HUEVO", "GLUTEN"], 0, 20),
        ("Pollo a la Canasta", 55000, None, "3 perniles fritos + 3/4 caja arroz chino + papa francesa", suma({"Pierna pernil": 3, "Aceite vegetal": 0.06}, ARROZ_34, PAPA, CAJA), ["SOYA", "HUEVO"], 0, 20),
        ("Pollo a la Canasta Apanado", 57000, None, "3 perniles apanados + 3/4 caja arroz chino + papa francesa", suma({"Pierna pernil": 3, "Harina para apanar": 0.08, "Aceite vegetal": 0.06}, ARROZ_34, PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN"], 0, 20),
        ("Alas Fritas", 52000, None, "6 alas apanadas + 3/4 caja arroz chino + papa francesa", suma({"Alas de pollo": 6, "Harina para apanar": 0.06, "Aceite vegetal": 0.06}, ARROZ_34, PAPA, CAJA), ["SOYA", "HUEVO", "GLUTEN"], 0, 18),
    ]
    variantes = [("Éxito Chino", "exito"), ("Con Pollo", "pollo"), ("Con Camarones", "camarones"),
                 ("Con Pollo y Camarones", "pollo y camarones"), ("Con Mariscos", "mariscos"), ("Con Res", "res"), ("Solo Verduras", "verduras")]
    precios_cm = [(52000, 45000), (55000, 45000), (61000, 52000), (62000, 53000), (70000, 58000), (52000, 42000), (43000, 35000)]
    precios_sp = [(52000, 45000), (55000, 45000), (61000, 52000), (63000, 53000), (70000, 58000), (52000, 43000), (43000, 35000)]
    c["Chow Mein"] = [(f"Chow Mein {v}", g, t, "Fideo chow mein salteado al wok con verduras" + ("" if p == "verduras" else " y proteína"),
                       suma(CHOWMEIN, escalar(PROT[p], 0.8)), ["SOYA", "GLUTEN"] + (["MARISCOS"] if "Camar" in v or "Maris" in v or p == "exito" else []), 1, 15)
                      for (v, p), (g, t) in zip(variantes, precios_cm)]
    c["Espaguettis"] = [(f"Spaguettis {v}", g, t, "Spaguettis en salsa de tomate con verduras",
                         suma(SPAGUETTI, escalar(PROT[p], 0.8)), ["GLUTEN"] + (["MARISCOS"] if "Camar" in v or "Maris" in v or p == "exito" else []), 0, 15)
                        for (v, p), (g, t) in zip(variantes, precios_sp)]
    sueys = [("Chow Suey Éxito Chino", "exito", 48000, 42000), ("Chow Suey Solo Pollo", "pollo", 50000, 43000),
             ("Chow Suey Solo Pollo y Camarones", "pollo y camarones", 65000, 50000), ("Chow Suey Solo Camarones", "camarones", 67000, 52000),
             ("Chow Suey Solo Mariscos", "mariscos", 70000, 55000), ("Chow Suey Solo Res", "res", 48000, 42000),
             ("Chow Suey Solo Vegetales", "verduras", 42000, 36000)]
    c["Chow Suey"] = [(n, g, t, "Verduras salteadas al wok con raíz china", suma(CHOWSUEY, escalar(PROT[p], 0.8)),
                       ["SOYA"] + (["MARISCOS"] if p in ("exito", "camarones", "mariscos", "pollo y camarones") else []), 0, 12)
                      for n, p, g, t in sueys]
    SOPA = {"Repollo": 0.05, "Zanahoria": 0.04, "Cebolla larga": 0.02}
    c["Sopas"] = [
        ("Sopa de Pasta", 17000, None, "Sopa de pasta con verduras", suma(SOPA, {"Espagueti": 0.08}), ["GLUTEN"], 0, 10),
        ("Sopa Éxito Chino", 20000, None, "Sopa con pollo, res, camarón y verduras", suma(SOPA, escalar(PROT["exito"], 0.4)), ["MARISCOS"], 0, 12),
        ("Sopa de Pollo", 20000, None, "Sopa de pollo con verduras", suma(SOPA, {"Pechuga de pollo": 0.1}), [], 0, 10),
        ("Sopa de Res", 20000, None, "Sopa de res con verduras", suma(SOPA, {"Carne de res": 0.1}), [], 0, 10),
        ("Sopa de Vegetales", 15000, None, "Sopa de verduras", suma(SOPA, PROT["verduras"]), [], 0, 10),
    ]
    CREMA = {"Crema de leche": 0.15, "Cebolla larga": 0.03}
    c["Cremas, Cazuelas y Ensaladas"] = [
        ("Cazuela de Mariscos", 48000, None, "Cazuela cremosa de mariscos", suma(CREMA, {"Mariscos mixtos": 0.18}), ["MARISCOS", "LACTEOS"], 0, 18),
        ("Cazuela de Camarones", 48000, None, "Cazuela cremosa de camarones", suma(CREMA, {"Camarón": 0.18}), ["MARISCOS", "LACTEOS"], 0, 18),
        ("Cazuela de Pollo", 43000, None, "Cazuela cremosa de pollo", suma(CREMA, {"Pechuga de pollo": 0.2}), ["LACTEOS"], 0, 18),
        ("Cazuela de Pollo y Camarones", 48000, None, "Cazuela cremosa de pollo y camarones", suma(CREMA, PROT["pollo y camarones"]), ["MARISCOS", "LACTEOS"], 0, 18),
        ("Crema de Pescado", 45000, None, "Crema de pescado", suma(CREMA, {"Filete de pescado": 0.18}), ["PESCADO", "LACTEOS"], 0, 15),
        ("Crema de Cebolla", 32000, None, "Crema de cebolla", suma(CREMA, {"Cebolla larga": 0.15}), ["LACTEOS"], 0, 12),
        ("Coctel de Camarones", 40000, None, "Coctel de camarones con salsa rosada", {"Camarón": 0.15, "Salsa de tomate": 0.05, "Limón": 0.05}, ["MARISCOS"], 0, 10),
        ("Ensalada de Verduras", 28000, None, "Ensalada fresca de verduras", {"Repollo": 0.15, "Zanahoria": 0.08, "Pimentón": 0.04}, [], 0, 8),
        ("Ensalada de Pollo", 38000, None, "Ensalada con pollo", {"Repollo": 0.15, "Zanahoria": 0.08, "Pechuga de pollo": 0.12}, [], 0, 10),
        ("Ensalada de Camarón", 48000, None, "Ensalada con camarones", {"Repollo": 0.15, "Zanahoria": 0.08, "Camarón": 0.12}, ["MARISCOS"], 0, 10),
    ]
    c["Pasabocas"] = [
        ("Lumpia", 5000, None, "Rollo apanado relleno de verduras y carnes (unidad)", {"Masa para lumpia": 1, "Repollo": 0.03, "Carne de cerdo": 0.03, "Aceite vegetal": 0.02}, ["GLUTEN"], 0, 8),
        ("Papa Francesa", 12000, None, "Porción de papa a la francesa", {"Papa a la francesa": 0.35, "Aceite vegetal": 0.05}, [], 0, 8),
        ("Pollo Apanado (10 unidades)", 15000, None, "10 trozos de pollo apanado", {"Pechuga de pollo": 0.3, "Harina para apanar": 0.06, "Aceite vegetal": 0.06}, ["GLUTEN"], 0, 12),
        ("Alas Fritas (5 unidades)", 20000, None, "5 alas fritas", {"Alas de pollo": 5, "Aceite vegetal": 0.05}, [], 0, 12),
        ("Pierna Pernil (2 unidades)", 20000, None, "2 piernas pernil fritas", {"Pierna pernil": 2, "Aceite vegetal": 0.04}, [], 0, 12),
        ("Costilla de Cerdo", 45000, None, "Costilla de cerdo frita", {"Costilla de cerdo": 0.5, "Aceite vegetal": 0.05}, [], 0, 20),
    ]
    BASE_PERSONAL = suma(escalar(ARROZ, 0.5), {"Papa a la francesa": 0.15, "Aceite vegetal": 0.03})
    personales = [("5 unidades de pollo apanado", {"Pechuga de pollo": 0.15, "Harina para apanar": 0.03}, 21000, ["GLUTEN"]),
                  ("2 unidades de alas", {"Alas de pollo": 2}, 21000, []),
                  ("1 pierna pernil", {"Pierna pernil": 1}, 21000, []),
                  ("1 pierna pernil apanado", {"Pierna pernil": 1, "Harina para apanar": 0.03}, 22000, ["GLUTEN"]),
                  ("1 lumpia", {"Masa para lumpia": 1, "Carne de cerdo": 0.03}, 21000, ["GLUTEN"]),
                  ("2 huevos fritos", {"Huevos": 2}, 21000, ["HUEVO"]),
                  ("costilla de cerdo apanada", {"Costilla de cerdo": 0.2, "Harina para apanar": 0.03}, 21000, ["GLUTEN"]),
                  ("pechuga a la plancha", {"Pechuga de pollo": 0.18}, 21000, []),
                  ("cerdo a la plancha", {"Carne de cerdo": 0.18}, 21000, []),
                  ("pescado frito", {"Filete de pescado": 0.18, "Harina para apanar": 0.03}, 22000, ["PESCADO", "GLUTEN"])]
    nombres = ["Combo 1", "Combo 2", "Combo 3", "Combo 3B", "Combo 4", "Combo 5", "Combo 6", "Combo 7", "Combo 8", "Combo 9"]
    c["Combo Personal"] = [(f"{n} Personal", p, None, f"Arroz + papa francesa + {d}", suma(BASE_PERSONAL, r), ["SOYA", "HUEVO"] + a, 0, 12)
                           for n, (d, r, p, a) in zip(nombres, personales)]
    return c


PROVEEDORES = [
    ("900456123-1", "Avícola Santander S.A.S.", "Avícola Santander", "3157894512", "pedidos@avicolasantander.co", "Km 4 vía Girón", "Girón",
     ("Marta Rueda", "Asesora comercial", "3157894512"), ["PROTEINAS"], ["Pollo entero", "Pierna pernil", "Alas de pollo", "Pechuga de pollo", "Huevos"], 30, 300000, 1, "CREDITO"),
    ("901234567-8", "Mariscos del Pacífico Ltda.", "Mariscos del Pacífico", "3104567890", "ventas@mariscospacifico.co", "Central de Abastos bodega 12", "Bucaramanga",
     ("Jorge Mena", "Vendedor", "3104567890"), ["PROTEINAS"], ["Camarón", "Mariscos mixtos", "Filete de pescado"], 15, 400000, 2, "TRANSFERENCIA"),
    ("800987654-3", "Distribuidora de Granos del Oriente S.A.", "Granos del Oriente", "6076451230", "comercial@granosoriente.co", "Cra 15 # 45-20", "Bucaramanga",
     ("Lucía Pinzón", "Ejecutiva de cuenta", "3001237788"), ["GRANOS", "FIDEOS", "ACEITES"], ["Arroz blanco", "Espagueti", "Fideo chow mein", "Aceite vegetal", "Harina para apanar"], 30, 500000, 3, "CREDITO"),
    ("1098765432", "Fruver La Cosecha", "Fruver La Cosecha", "3209876543", "lacosecha.fruver@gmail.com", "Plaza de mercado de Girón local 8", "Girón",
     ("Pedro Gómez", "Propietario", "3209876543"), ["VEGETALES"], ["Repollo", "Zanahoria", "Cebolla larga", "Raíz china", "Pimentón", "Limón", "Papa a la francesa"], 0, 50000, 1, "CONTADO"),
    ("900111222-5", "Salsas y Condimentos Oriental S.A.S.", "Salsas Oriental", "3012223344", "ventas@salsasoriental.co", "Zona Industrial Chimitá bodega 3", "Bucaramanga",
     ("Carolina Wu", "Coordinadora de ventas", "3012223344"), ["SALSAS", "OTROS", "DESECHABLES", "LACTEOS"], ["Salsa de soya", "Salsa agridulce", "Salsa de tomate", "Masa para lumpia", "Caja desechable", "Crema de leche"], 15, 200000, 4, "TRANSFERENCIA"),
]

USUARIOS = [  # usuario, nombre, rol
    ("admin", "Wei Chen (Administrador)", "ADMINISTRADOR"),
    ("mesero1", "Laura Martínez", "MESERO"),
    ("mesero2", "Andrés Rojas", "MESERO"),
    ("cocinero1", "Li Zhang", "COCINERO"),
    ("caja1", "Diana Ortiz", "CAJERO"),
    ("bodega1", "Camilo Suárez", "INVENTARIO"),
]


def crear_usuario(tok_plataforma, usuario, nombre, rol, proveedor_id=None):
    existe = api("GET", "/api/usuarios?q=" + urllib.parse.quote(usuario), tok_plataforma)["content"]
    if any(u["username"] == usuario for u in existe):
        print(f"  {usuario} ya existe (se conserva)")
        return None
    temporal = "Temporal" + secrets.token_hex(3) + "1"
    api("POST", "/api/usuarios", tok_plataforma, {"username": usuario, "nombre": nombre, "password": temporal, "roles": [rol],
                                                  "proveedorId": proveedor_id})
    # el propio usuario cambia su clave temporal: así no se le exige cambiarla en el primer ingreso de la demo
    t = login(usuario, temporal)
    api("PUT", "/api/usuarios/yo/password", t, {"actual": temporal, "nueva": CLAVE_DEMO, "confirmacion": CLAVE_DEMO})
    print(f"  {usuario:<11} {rol}")
    return usuario


def main():
    plataforma = login(ENV["WOK_BOOTSTRAP_ADMIN_USERNAME"], ENV["WOK_BOOTSTRAP_ADMIN_PASSWORD"])

    paso("1. Parámetros del restaurante")
    api("PUT", "/api/parametros", plataforma, {
        "nombreRestaurante": "Restaurante Éxito Chino", "nit": None, "telefono": "(607) 659 8585",
        "direccion": "Frente al Parqueadero de Metro de Girón - Al lado de Av. los Caneyes",
        "impuestoConsumoPorcentaje": 8, "minutosAlertaCocina": 20,
        "mensajeComprobante": "Exquisita comida típica oriental · WhatsApp 318 859 9888 · Gracias por su visita"})

    paso("2. Usuarios del personal (clave: WOK_DEMO_PASSWORD de .env)")
    for u, n, r in USUARIOS:
        crear_usuario(plataforma, u, n, r)
    t = {u: login(u, CLAVE_DEMO) for u, _, _ in USUARIOS}
    admin, bodega, mesero, mesero2, cocina, caja = t["admin"], t["bodega1"], t["mesero1"], t["mesero2"], t["cocinero1"], t["caja1"]

    paso("3. Retirar datos de prueba técnica (sufijo aleatorio) para que no aparezcan en la demo")
    import re
    sufijo = re.compile(r" [0-9a-f]{6}$")
    for p in api("GET", "/api/menu/platos", admin):
        if sufijo.search(p["nombre"]):
            api("DELETE", f"/api/menu/platos/{p['id']}", admin)
    for c in api("GET", "/api/menu/categorias", admin):
        if sufijo.search(c["nombre"]) and c["activa"]:
            api("PUT", f"/api/menu/categorias/{c['id']}", admin, {**c, "activa": False})
    for i in api("GET", "/api/insumos", bodega):
        if sufijo.search(i["nombre"]):
            api("PATCH", f"/api/insumos/{i['id']}/estado?activo=false", bodega)
    for p in api("GET", "/api/proveedores", admin):
        if sufijo.search(p["nombreComercial"]) and p["activo"]:
            api("PATCH", f"/api/proveedores/{p['id']}/estado?activo=false", admin)
    for m in api("GET", "/api/mesas", admin):
        if m["numero"] >= 100 and m["pedidosActivos"] == 0:
            api("PUT", f"/api/mesas/{m['id']}", admin, {"numero": m["numero"], "capacidad": m["capacidad"], "zona": m["zona"], "activa": False})
    print("  listo")

    paso("4. Insumos y existencias iniciales")
    # reanudable: reutiliza por nombre lo que ya exista y solo registra movimientos de los insumos nuevos
    insumos = {i["nombre"].lower(): i["id"] for i in api("GET", "/api/insumos?incluirInactivos=true", bodega)}
    nuevos = []
    for nombre, (cat, uni, minimo, costo, stock, perecedero) in INSUMOS.items():
        if nombre.lower() in insumos:
            insumos[nombre] = insumos[nombre.lower()]
            continue
        nuevos.append(nombre)
        i = api("POST", "/api/insumos", bodega, {"nombre": nombre, "categoria": cat, "unidad": uni, "stockMinimo": minimo, "perecedero": perecedero})
        insumos[nombre] = i["id"]
        mov = {"tipo": "ENTRADA", "cantidad": stock, "costoUnitario": costo, "motivo": "Inventario inicial"}
        if perecedero:
            dias = 3 if nombre == "Camarón" else 8 if cat == "VEGETALES" else 12
            mov |= {"lote": "INI-" + nombre[:3].upper(), "fechaVencimiento": (date.today() + timedelta(days=dias)).isoformat()}
        api("POST", f"/api/insumos/{i['id']}/movimientos", bodega, mov)
    print(f"  {len(INSUMOS)} insumos ({len(nuevos)} nuevos con su entrada inicial)")
    if "Repollo" in nuevos:
        api("POST", f"/api/insumos/{insumos['Repollo']}/movimientos", bodega, {"tipo": "MERMA", "cantidad": 1.5, "motivo": "Hojas dañadas por humedad"})
        api("POST", f"/api/insumos/{insumos['Aceite vegetal']}/movimientos", bodega, {"tipo": "SALIDA", "cantidad": 2, "motivo": "Cambio de aceite de freidora"})
        api("POST", f"/api/insumos/{insumos['Huevos']}/ajustes", bodega, {"cantidadContada": 174, "motivo": "Conteo físico semanal: 6 huevos rotos"})
        print("  merma, salida y ajuste por conteo registrados")

    paso("5. Proveedores")
    por_nit = {p["nit"]: p["id"] for p in api("GET", "/api/proveedores", admin)}
    proveedores = {}
    for nit, rs, nc, tel, mail, dirc, ciudad, (cn, cc, ct), cats, ins, plazo, minimo, entrega, pago in PROVEEDORES:
        if nit in por_nit:
            proveedores[nc] = por_nit[nit]
            continue
        p = api("POST", "/api/proveedores", admin, {
            "nit": nit, "razonSocial": rs, "nombreComercial": nc, "telefono": tel, "email": mail, "direccion": dirc, "ciudad": ciudad,
            "contactoNombre": cn, "contactoCargo": cc, "contactoTelefono": ct, "categorias": cats,
            "insumoIds": [insumos[x] for x in ins], "plazoPagoDias": plazo, "pedidoMinimo": minimo,
            "tiempoEntregaDias": entrega, "formaPago": pago})
        proveedores[nc] = p["id"]
    print(f"  {len(proveedores)} proveedores")
    crear_usuario(plataforma, "proveedor1", "Marta Rueda (Avícola Santander)", "PROVEEDOR", proveedores["Avícola Santander"])
    proveedor = login("proveedor1", CLAVE_DEMO)

    paso("6. Carta: categorías, platos (Grande y 3/4) con receta, y combos")
    # reanudable: si una categoría o un plato ya existen (p. ej. creados a mano), se reutilizan sin modificarlos
    cats_existentes = {c["nombre"].lower(): c for c in api("GET", "/api/menu/categorias", admin)}
    platos_existentes = {p["nombre"].lower(): p["id"] for p in api("GET", "/api/menu/platos?incluirRetirados=true", admin)}
    platos = {}
    total = 0
    for orden, (cat, items) in enumerate(carta().items(), start=1):
        c = cats_existentes.get(cat.lower()) or api("POST", "/api/menu/categorias", admin, {
            "nombre": cat, "orden": orden, "activa": True, "descripcion": "Grande y 3/4" if any(i[2] for i in items) else None})
        for nombre, grande, tres_cuartos, desc, receta, alergenos, picante, minutos in items:
            for etiqueta, precio, factor in ([("Grande", grande, 1.0), ("3/4", tres_cuartos, 0.75)] if tres_cuartos else [(None, grande, 1.0)]):
                n = f"{nombre} ({etiqueta})" if etiqueta else nombre
                if n.lower() in platos_existentes:
                    platos[n] = platos_existentes[n.lower()]
                    continue
                p = api("POST", "/api/menu/platos", admin, {
                    "nombre": n, "descripcion": desc, "categoriaId": c["id"], "precio": precio, "disponible": True,
                    "picanteBase": picante, "alergenos": sorted(set(alergenos)), "tiempoPreparacionMin": minutos,
                    "receta": [{"insumoId": insumos[k], "cantidad": round(v * factor, 4)} for k, v in receta.items()]})
                platos[n] = p["id"]
                total += 1
        print(f"  {cat:<32} listo")
    combos = {c["nombre"]: c["id"] for c in api("GET", "/api/menu/combos?incluirRetirados=true", admin)}
    for nombre, precio, desc, items in [
        ("Bandeja Oriental", 72000, "3/4 arroz + 3/4 chow suey + agridulce",
         ["Arroz 3 Carnes (3/4)", "Chow Suey Éxito Chino (3/4)", "Agridulce Éxito Chino (3/4)"]),
        ("Rice Cooling", 65000, "3/4 arroz + 3/4 chow suey", ["Arroz 3 Carnes (3/4)", "Chow Suey Éxito Chino (3/4)"]),
        ("Bandeja Especial Spaguettis", 68000, "3/4 arroz + 3/4 spaguettis", ["Arroz 3 Carnes (3/4)", "Spaguettis Éxito Chino (3/4)"]),
        ("Bandeja Especial Chow Mein", 68000, "3/4 arroz + 3/4 chow mein", ["Arroz 3 Carnes (3/4)", "Chow Mein Éxito Chino (3/4)"])]:
        if nombre in combos:
            continue
        cb = api("POST", "/api/menu/combos", admin, {"nombre": nombre, "descripcion": desc, "precio": precio, "disponible": True,
                                                      "items": [{"platoId": platos[i], "cantidad": 1} for i in items]})
        combos[nombre] = cb["id"]
    print(f"  {total} platos nuevos y 4 bandejas como combos")

    paso("7. Mesas")
    mesas = {}
    for n in range(1, 13):
        zona, cap = ("SALON", 4) if n <= 8 else ("TERRAZA", 4) if n <= 10 else ("BARRA", 2) if n == 11 else ("PRIVADO", 10)
        existentes = {m["numero"]: m for m in api("GET", "/api/mesas?incluirInactivas=true", admin)}
        if n in existentes:
            mesas[n] = existentes[n]["id"]
            continue
        mesas[n] = api("POST", "/api/mesas", admin, {"numero": n, "capacidad": cap, "zona": zona, "activa": True})["id"]
    print("  12 mesas (salón, terraza, barra y privado)")

    paso("8. Órdenes de compra")
    if api("GET", f"/api/ordenes-compra?proveedorId={proveedores['Avícola Santander']}", bodega):
        print("  ya existen; se omite")
    else:
        cargar_compras(bodega, proveedor, proveedores, insumos)

    paso("9. Pedidos del día")
    # el pedido cancelado de la mesa 8 es el último paso: si existe, la demo del día ya se cargó completa
    hoy = api("GET", f"/api/pedidos?fecha={date.today().isoformat()}&mesaId={mesas[8]}", admin)
    if any(x["estado"] == "CANCELADO" and x["mesero"] == "mesero2" for x in hoy):
        print("  ya existen; se omite")
    else:
        cargar_pedidos(admin, mesero, mesero2, cocina, caja, mesas, platos, combos, insumos)

    (RAIZ / ".run").mkdir(exist_ok=True)
    lineas = [f"{u:<11} {r:<14} clave: {CLAVE_DEMO}" for u, _, r in USUARIOS] + [f"{'proveedor1':<11} {'PROVEEDOR':<14} clave: {CLAVE_DEMO}"]
    (RAIZ / ".run" / "credenciales-demo.txt").write_text("\n".join(lineas) + "\n", encoding="utf8")
    print("\nDatos de demostración cargados. Credenciales en .run/credenciales-demo.txt")


def cargar_compras(bodega, proveedor, proveedores, insumos):
    oc = api("POST", "/api/ordenes-compra", bodega, {"proveedorId": proveedores["Avícola Santander"], "notas": "Entrega antes de las 10 a.m.",
                                                     "fechaEntregaEsperada": date.today().isoformat(),
                                                     "items": [{"insumoId": insumos["Pollo entero"], "cantidad": 20, "costoUnitario": 16000},
                                                               {"insumoId": insumos["Pierna pernil"], "cantidad": 40, "costoUnitario": 4500}]})
    api("POST", f"/api/ordenes-compra/{oc['id']}/envio", bodega)
    api("POST", f"/api/ordenes-compra/{oc['id']}/confirmacion", proveedor)
    api("POST", f"/api/ordenes-compra/{oc['id']}/entrega", proveedor, {"fechaEntrega": date.today().isoformat(), "documento": "REM-2231",
                                                                         "observaciones": "Entregado en cadena de frío"})
    api("POST", f"/api/ordenes-compra/{oc['id']}/recepcion", bodega, {
        "lineas": [{"insumoId": insumos["Pollo entero"], "cantidadRecibida": 20, "lote": "AV-1019",
                    "fechaVencimiento": (date.today() + timedelta(days=6)).isoformat()},
                   {"insumoId": insumos["Pierna pernil"], "cantidadRecibida": 38, "lote": "AV-1019",
                    "fechaVencimiento": (date.today() + timedelta(days=6)).isoformat()}],
        "observaciones": "Llegaron 2 perniles menos"})
    oc = api("POST", "/api/ordenes-compra", bodega, {"proveedorId": proveedores["Mariscos del Pacífico"],
                                                     "fechaEntregaEsperada": (date.today() + timedelta(days=2)).isoformat(),
                                                     "items": [{"insumoId": insumos["Camarón"], "cantidad": 10, "costoUnitario": 48000},
                                                               {"insumoId": insumos["Mariscos mixtos"], "cantidad": 6, "costoUnitario": 42000}]})
    api("POST", f"/api/ordenes-compra/{oc['id']}/envio", bodega)
    api("POST", f"/api/ordenes-compra/{oc['id']}/confirmacion", bodega)
    oc = api("POST", "/api/ordenes-compra", bodega, {"proveedorId": proveedores["Granos del Oriente"],
                                                     "fechaEntregaEsperada": (date.today() + timedelta(days=3)).isoformat(),
                                                     "items": [{"insumoId": insumos["Arroz blanco"], "cantidad": 100, "costoUnitario": 4100},
                                                               {"insumoId": insumos["Fideo chow mein"], "cantidad": 20, "costoUnitario": 9000}]})
    api("POST", f"/api/ordenes-compra/{oc['id']}/envio", bodega)
    api("POST", "/api/ordenes-compra", bodega, {"proveedorId": proveedores["Salsas Oriental"], "notas": "Reponer masa de lumpia",
                                               "items": [{"insumoId": insumos["Masa para lumpia"], "cantidad": 100, "costoUnitario": 600},
                                                         {"insumoId": insumos["Salsa agridulce"], "cantidad": 6, "costoUnitario": 14000}]})
    print("  4 órdenes: recibida, confirmada, enviada y borrador")


def cargar_pedidos(admin, mesero, mesero2, cocina, caja, mesas, platos, combos, insumos):
    import time
    hoy = date.today().isoformat()

    def pedido(tok, mesa, items, obs=None, alergias=None):
        # reanudable: si la demo ya registró hoy un pedido en esa mesa, se continúa con él
        previos = [x for x in api("GET", f"/api/pedidos?fecha={hoy}&mesaId={mesas[mesa]}", admin)
                   if x["mesero"] in ("mesero1", "mesero2")]
        if previos:
            return previos[0]
        cuerpo = {"mesaId": mesas[mesa], "observaciones": obs, "alergias": alergias or [],
                  "items": [{"tipo": "COMBO" if n in combos else "PLATO", "productoId": combos.get(n) or platos[n], "cantidad": c,
                             "nivelPicante": pic, "notas": nota} for n, c, pic, nota in items]}
        return api("POST", "/api/pedidos", tok, cuerpo)

    def actual(p):
        return api("GET", f"/api/pedidos/{p['id']}", admin)

    def enviar(p, tok=None):
        for _ in range(15):
            if actual(p)["estado"] != "ABIERTO":
                return
            r = api("PATCH", f"/api/pedidos/{p['id']}/estado", tok or mesero, {"estado": "ENVIADO"}, esperado=(200, 409))
            if isinstance(r, dict):
                return
            time.sleep(3)  # 409: inventario aún procesa el consumo; el reintento es idempotente
        raise SystemExit("El pedido no pudo enviarse a cocina")

    def avanzar(p, hasta):
        enviar(p)
        orden = ["PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO"]
        objetivo = orden.index(hasta) if hasta in orden else 0
        for paso_i in range(1, objetivo + 1):
            estado = orden[paso_i]
            for it in actual(p)["items"]:
                if orden.index(it["estado"]) < paso_i:
                    api("PATCH", f"/api/pedidos/{p['id']}/items/{it['id']}/estado", mesero if estado == "ENTREGADO" else cocina,
                        {"estado": estado})

    def cobrar(mesa, pagos_fn, descuento=None):
        cuenta = api("GET", f"/api/cuentas/mesa/{mesas[mesa]}", caja)
        if not cuenta["pedidos"]:
            return None
        total_est = cuenta["total"]
        if descuento:
            base = round(cuenta["subtotal"] * (1 - descuento["porcentaje"] / 100))
            total_est = round(base * (1 + cuenta["impuestoPorcentaje"] / 100))
        return api("POST", "/api/facturas", caja, {"mesaId": mesas[mesa], "pagos": pagos_fn(total_est),
                                                    "descuento": descuento, "claveIdempotencia": str(uuid.uuid4())})

    redondeo = lambda v: ((int(v) + 9999) // 10000) * 10000
    # cobrados
    p = pedido(mesero, 1, [("Arroz Éxito Chino (Grande)", 1, 0, None), ("Lumpia", 2, 0, None)], "Pareja, sirvan las lumpias primero")
    avanzar(p, "ENTREGADO")
    cobrar(1, lambda t: [{"metodo": "EFECTIVO", "monto": redondeo(t)}])
    p = pedido(mesero2, 2, [("El Gran Combo 1 (Grande)", 1, 0, "Papa bien dorada")])
    avanzar(p, "ENTREGADO")
    cobrar(2, lambda t: [{"metodo": "TARJETA_DEBITO", "monto": t, "referencia": "AUT-558120"}])
    p = pedido(mesero, 3, [("Chow Mein Con Pollo (Grande)", 1, 2, "Bien picante"), ("Sopa Éxito Chino", 2, 0, None)], "Clientes frecuentes")
    avanzar(p, "ENTREGADO")
    codigo = api("POST", "/api/descuentos/autorizaciones", admin, {"porcentajeMaximo": 10, "motivo": "Clientes frecuentes",
                                                                   "mesaId": mesas[3], "horasVigencia": 4})["codigo"]
    cobrar(3, lambda t: [{"metodo": "TRANSFERENCIA", "monto": t, "referencia": "NEQUI-7781"}], {"porcentaje": 10, "codigoAutorizacion": codigo})
    p = pedido(mesero2, 4, [("Bandeja Oriental", 1, 0, None), ("Coctel de Camarones", 1, 0, None)], None, ["MANI"])
    avanzar(p, "ENTREGADO")
    cobrar(4, lambda t: [{"metodo": "BILLETERA_DIGITAL", "monto": 50000, "referencia": "DAVIPLATA"},
                         {"metodo": "EFECTIVO", "monto": redondeo(t - 50000)}])
    print("  4 pedidos cobrados (efectivo, débito, transferencia con descuento autorizado, mixto)")
    # en curso
    p = pedido(mesero, 5, [("Valenciana (Grande)", 1, 0, None), ("Papa Francesa", 1, 0, None)])
    avanzar(p, "ENTREGADO")
    p = pedido(mesero2, 6, [("Arroz 7 Carnes (Grande)", 1, 1, None)], "Cumpleaños: llevar vela con el plato")
    avanzar(p, "LISTO")
    p = pedido(mesero, 7, [("Spaguettis Con Camarones (3/4)", 2, 0, "Sin pimentón"), ("Alas Fritas (5 unidades)", 1, 0, None)], None, ["LACTEOS"])
    avanzar(p, "EN_PREPARACION")
    p = pedido(mesero2, 9, [("Pollo a la Canasta", 1, 0, None), ("Chow Suey Solo Pollo (3/4)", 1, 1, None)], "Terraza, para llevar lo que sobre")
    avanzar(p, "ENVIADO")
    pedido(mesero, 10, [("Combo 1 Personal", 3, 0, None), ("Lumpia", 3, 0, None)], "Almuerzo de oficina")
    p = pedido(mesero2, 8, [("Arroz Solo Res (3/4)", 1, 0, None)])
    enviar(p, mesero2)
    if actual(p)["estado"] == "ENVIADO":
        api("PATCH", f"/api/pedidos/{p['id']}/estado", mesero2, {"estado": "CANCELADO", "motivo": "El cliente se retiró antes de la preparación"})
    print("  6 pedidos en curso: por cobrar, listo, en preparación, en cocina, abierto y uno cancelado")

    paso("10. Alertas de cocina y autorización de descuento vigente")
    api("POST", f"/api/insumos/{insumos['Masa para lumpia']}/reportes-agotado", cocina, {"observacion": "Quedan muy pocas, se agotan en el almuerzo"})
    api("POST", "/api/descuentos/autorizaciones", admin, {"porcentajeMaximo": 15, "motivo": "Demora en el servicio (uso del turno)", "horasVigencia": 8})
    print("  reporte de agotado desde cocina y un código de descuento disponible para caja")


if __name__ == "__main__":
    main()
