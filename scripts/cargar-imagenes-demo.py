"""
Carga fotos de referencia en la carta de Wok Control (platos, combos y categorías).

Las fotos provienen de Wikimedia Commons y todas tienen licencia libre (CC0, dominio público, CC BY o
CC BY-SA). Fueron elegidas una a una para que correspondan al plato. El autor, la licencia y la página
de origen se consultan a Commons en el momento de la carga y quedan guardados junto a la foto; se
muestran bajo cada imagen y en Menú > Platos > "Créditos de las fotos", como exigen esas licencias.

Las fotos se guardan en MongoDB (GridFS de wok_menu) a través de la API del sistema.
No reemplaza fotos existentes: si un plato ya tiene foto (por ejemplo una foto propia), se respeta.
Se puede ejecutar de nuevo sin duplicar nada.

Requisitos: sistema en marcha y WOK_DEMO_PASSWORD en .env (usuario demo "admin", rol ADMINISTRADOR).
Uso:  python scripts/cargar-imagenes-demo.py
"""
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

BASE = "http://localhost:8080"
RAIZ = Path(__file__).resolve().parent.parent
ENV = {k.strip(): v.strip() for k, v in (l.split("=", 1) for l in (RAIZ / ".env").read_text(encoding="utf8").splitlines()
                                          if l and not l.startswith("#") and "=" in l)}
COMMONS = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "WokControlAcademicDemo/1.0 (student project; image credits page)"}
ANCHO = 800  # Commons genera la miniatura JPEG: ~100-200 KB, por debajo del límite de 2 MB

# Nombre del plato, combo o categoría (sin el tamaño "(Grande)" / "(3/4)") -> archivo de Wikimedia Commons
FOTOS = {
    'Arroz Éxito Chino': 'Chinese fried rice by stu spivack in Cleveland, OH.jpg',
    'Arroz 3 Carnes': 'Yangzhou fried rice and drinks 25-09-2019.jpg',
    'Arroz 7 Carnes': 'Special Fried Rice - Dumplings Plus AUD7 (3522091044).jpg',
    'Arroz Solo Mariscos': 'Sambal Spicy Seafood Fried Rice - Ho Chiak.jpg',
    'Arroz Solo Camarones': 'Bowl of homemade shrimp fried rice - Forsythe 0.jpg',
    'Arroz Solo Pollo y Camarones': 'Yeung Chow Fried Rice in Hong Kong Fast Food Shop.JPG',
    'Arroz Solo Pollo': 'Fried rice in home.jpg',
    'Arroz Solo Res': 'Cumin beef fried rice (1).jpg',
    'Porción Arroz Chino': 'Cantonese rice @ Restaurant Phénix @ Paris (34406404351).jpg',
    'Agridulce Éxito Chino': 'Sweet and Sour Chicken.JPG',
    'El Gran Combo 1': 'Fried Chicken (Unsplash).jpg',
    'El Gran Combo 2': 'Fried chicken drumsticks.jpg',
    'El Gran Combo 3': 'Fried Rice with Fried Chicken.jpg',
    'El Gran Combo 4': 'Fried chickens and grilled pork ribs.jpg',
    'Pollo Entero + Papa 1': 'Fried chicken (25163375255).jpg',
    'Pollo Entero + Papa 2': 'French fries and fried chicken wings, Everything with Fries, Holland Village, Singapore - 20121021.jpg',
    'Pollo Entero 1': 'Tongdak 1.jpg',
    'Pollo Entero 2': 'Fried chicken drumsticks.jpg',
    'Pollo entero': 'Tongdak.jpg',
    'Pollo a la Canasta Apanado': 'Fried Chicken (5905281949).jpg',
    'Pollo a la Canasta': 'Korean fried chicken 7 dari.jpg',
    'Valenciana': 'Fried Rice with Fried Chicken.jpg',
    'Alas Fritas (5 unidades)': 'Fried Chicken Wings (54282490120).jpg',
    'Alas Fritas': 'Home-Made-Fried-Chicken-Wings-2008.jpg',
    'Pollo Apanado (10 unidades)': 'Impossible chicken nuggets 2.jpg',
    'Chow Mein Éxito Chino': 'Chow mein 2.jpg',
    'Chow Mein Con Pollo y Camarones': 'Chow mein 1.jpg',
    'Chow Mein Con Pollo': 'Buff Chow Mein with Sauce.jpg',
    'Chow Mein Con Camarones': 'Chow mein by Aaron Gustafson in Austin, Texas.jpg',
    'Chow Mein Con Mariscos': 'Homemade Chow mein with shrimps and meat with a choy and Choung.jpg',
    'Chow Mein Con Res': 'Lo mein 07.jpg',
    'Chow Mein Solo Verduras': 'Vegetable Chow Mein - Howrah 2014-03-23 9721.JPG',
    'CHOW MEIN': 'Chow mein 6.jpg',
    'Spaguettis Éxito Chino': 'Spaghetti with Meat Sauce in Plastic Container.jpg',
    'Spaguettis Con Pollo y Camarones': 'Seafood Spaghetti, Jun 2024.jpg',
    'Spaguettis Con Pollo': 'Spaghetti with chicken.jpg',
    'Spaguettis Con Camarones': 'Spicy Seafood Spaghetti (7994894586).jpg',
    'Spaguettis Con Mariscos': 'Spaghetti vongole seafood.jpg',
    'Spaguettis Con Res': 'Spaghetti and meatballs 1.jpg',
    'Spaguettis Solo Verduras': 'Liat Portal for Foodie Disorder - Spaghetti in tomato sauce with peas.jpg',
    'Chow Suey Éxito Chino': 'Chop suey (Philippines).jpg',
    'Chow Suey Solo Pollo y Camarones': 'Chop suey made in Ankara.jpg',
    'Chow Suey Solo Pollo': 'Chopsueywithrice.jpg',
    'Chow Suey Solo Camarones': 'Noodle stir fry with chicken, shrimp, and broccoli (18471621051).jpg',
    'Chow Suey Solo Mariscos': 'HK SWH 西灣河 Sai Wan Ho SKW 筲箕灣道 33 Shau Kei Wan Road shop 麗灣大廈 Lai Wan Building 海寶漁港 Ocean Treasure Seafood Restaurant diner food 西蘭花大蝦仁 Stir-fry broccoli with peeled shrimp March 2026 N13P 03.jpg',
    'Chow Suey Solo Res': 'Stir-fried String Beans (Taiwan).jpg',
    'Chow Suey Solo Vegetales': 'Stir fried mixed vegetables.jpg',
    'Sopa de Pasta': 'Noodle Soup in Chiang Rai.jpg',
    'Sopa Éxito Chino': 'Vietnamese Shrimp and Vegetable noodle soup.jpg',
    'Sopa de Pollo': 'Chicken Vegetable Soup (8731954951).jpg',
    'Sopa de Res': 'Beef Noodles soup from Jinyi Beef Noodle Soup Inn in Shenzhen.jpg',
    'Sopa de Vegetales': 'Simple vegetable soup 2009.jpg',
    'Cazuela de Mariscos': 'Cazuela de mariscos.jpg',
    'Cazuela de Camarones': 'By Carlos Barretta stk 000327 (5514845735).jpg',
    'Cazuela de Pollo y Camarones': 'Shrimp and corn chowder.jpg',
    'Cazuela de Pollo': 'Seafood chowder with pieces of fish.JPG',
    'Crema de Pescado': 'Fishchowder.jpg',
    'Crema de Cebolla': 'Mmm...onion soup (5344349906).jpg',
    'Coctel de Camarones': 'Shrimp Cocktail at Sonoma Grille - Sarah Stierch.jpg',
    'Ensalada de Verduras': 'Mixed Green Salad (15977106804).jpg',
    'Ensalada de Pollo': 'Chicken salad with eggs.jpg',
    'Ensalada de Camarón': 'Shrimp salad from Turkey.jpg',
    'Lumpia': 'Lumpia at Teh Jawa, Purwokerto Station, Purwokerto 2015-03-20.jpg',
    'Papa Francesa': 'French Fries.JPG',
    'Pierna Pernil (2 unidades)': 'Fried chicken legs, Rostov-on-Don, Russia.jpg',
    'Costilla de Cerdo': 'Roasted Pork Ribs with Fries.jpg',
    'Combo 1 Personal': 'Impossible chicken nuggets 1.jpg',
    'Combo 2 Personal': 'Fried Chicken Wings (52769476180).jpg',
    'Combo 3 Personal': 'Crispy Fried Chicken Drumsticks Leg Piece.jpg',
    'Combo 3B Personal': 'Fried chicken and sriracha.jpg',
    'Combo 4 Personal': 'Shanghai Restaurant - August 2019 - Stierch 02.jpg',
    'Combo 5 Personal': 'Fried rice with egg.jpg',
    'Combo 6 Personal': '02021 1164 Schweineschnitzel.jpg',
    'Combo 7 Personal': 'Grilled chicken breast, Santo Domingo, La Palma.jpg',
    'Combo 8 Personal': 'Grilled Pork Chop with Rice.jpg',
    'Combo 9 Personal': 'Fried Fish Fillet with Mayonnaise.jpg',
    'Bandeja Especial Chow Mein': 'Braised Mushroom Lo Mein with Soup - CK Bistro.jpg',
    'Bandeja Especial Spaguettis': 'Chicken Fillet and Veal Sausage with Spaghetti in Tomato Sauce.JPG',
    'Bandeja Oriental': 'Fried chickens and grilled pork ribs.jpg',
    'Pollo y pasta': 'Phoenix chicken spaghetti dish Old Spaghetti Factory restaurant.JPG',
    'Rice Cooling': 'Fried rice with shrimp paste.jpg',
    'Trifasico': 'Noodles and fried rice.jpg',
    'Entradas': 'Shanghai Restaurant - August 2019 - Stierch 01.jpg',
    'Pastas': 'Lo Mein American Style.JPG',
    'Pollo': 'Fried Chicken (Unsplash).jpg',
    'Arroces': 'Yeung Chow Fried Rice in Hong Kong Fast Food Shop.JPG',
    'Arroz Oriental': 'Chinese fried rice by stu spivack in Cleveland, OH.jpg',
    'Chow Mein': 'Chow mein 2.jpg',
    'Espaguettis': 'Spaghetti with Meat Sauce in Plastic Container.jpg',
    'Chow Suey': 'Chop suey (Philippines).jpg',
    'Sopas': 'FOOD Wonton Soup.jpg',
    'Cremas, Cazuelas y Ensaladas': 'Cazuela de mariscos.jpg',
    'Pasabocas': 'Lumpia 3.jpg',
    'Combo Personal': 'Fried chickens and grilled pork ribs.jpg',
}


def api(metodo, ruta, token=None, cuerpo=None, tipo="application/json"):
    datos = json.dumps(cuerpo).encode() if isinstance(cuerpo, (dict, list)) else cuerpo
    req = urllib.request.Request(BASE + ruta, data=datos, method=metodo, headers={"Content-Type": tipo})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            texto = r.read().decode()
            return json.loads(texto) if texto else None
    except urllib.error.HTTPError as e:
        raise SystemExit(f"\nError {e.code} en {metodo} {ruta}: {e.read().decode()}")


def commons(url, intentos=5):
    for i in range(intentos):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return r.read()
        except (urllib.error.URLError, TimeoutError) as e:
            espera = 10 * (i + 1)
            print(f"    Commons no respondió ({e}); reintento en {espera} s")
            time.sleep(espera)
    raise SystemExit("Wikimedia Commons no respondió; intente más tarde (el script continúa donde quedó).")


def limpiar(html):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", html or "")).strip()


_cache = {}


def foto(titulo):
    """Descarga la miniatura y su crédito (autor, licencia, origen) desde Commons."""
    if titulo not in _cache:
        q = urllib.parse.urlencode({"action": "query", "format": "json", "titles": "File:" + titulo, "prop": "imageinfo",
                                    "iiprop": "url|extmetadata", "iiurlwidth": ANCHO})
        pagina = next(iter(json.loads(commons(COMMONS + "?" + q))["query"]["pages"].values()))
        info = pagina["imageinfo"][0]
        md = info.get("extmetadata", {})
        credito = {"autor": limpiar(md.get("Artist", {}).get("value"))[:200] or "Wikimedia Commons",
                   "licencia": limpiar(md.get("LicenseShortName", {}).get("value"))[:60],
                   "licenciaUrl": md.get("LicenseUrl", {}).get("value", ""),
                   "fuenteUrl": info["descriptionurl"]}
        time.sleep(1)
        _cache[titulo] = (commons(info["thumburl"]), credito)
        time.sleep(1)
    return _cache[titulo]


def subir(token, destino, ident, datos, credito):
    limite = uuid.uuid4().hex
    partes = []
    for campo, valor in credito.items():
        if valor:
            partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="{campo}"\r\n\r\n{valor}\r\n'.encode())
    partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="archivo"; filename="foto.jpg"\r\n'
                  f'Content-Type: image/jpeg\r\n\r\n'.encode() + datos + b"\r\n")
    partes.append(f"--{limite}--\r\n".encode())
    api("PUT", f"/api/menu/{destino}/{ident}/imagen", token, b"".join(partes), f"multipart/form-data; boundary={limite}")


def archivo_para(nombre):
    base = re.sub(r"\s*\((Grande|3/4)\)$", "", nombre).strip()
    return FOTOS.get(base) or FOTOS.get(nombre)


def main():
    sys.stdout.reconfigure(encoding="utf8")
    if not ENV.get("WOK_DEMO_PASSWORD"):
        raise SystemExit("Defina WOK_DEMO_PASSWORD en .env")
    token = api("POST", "/api/auth/login", None, {"username": "admin", "password": ENV["WOK_DEMO_PASSWORD"]})["token"]
    elementos = ([("categorias", c) for c in api("GET", "/api/menu/categorias", token)]
                 + [("platos", p) for p in api("GET", "/api/menu/platos", token)]
                 + [("combos", c) for c in api("GET", "/api/menu/combos", token)])
    cargadas = existentes = sin_foto = 0
    for destino, e in elementos:
        if e.get("imagen"):
            existentes += 1
            continue
        titulo = archivo_para(e["nombre"])
        if not titulo:
            sin_foto += 1
            print(f"  (sin foto elegida) {destino}: {e['nombre']}")
            continue
        datos, credito = foto(titulo)
        subir(token, destino, e["id"], datos, credito)
        cargadas += 1
        print(f"  {destino:<10} {e['nombre']:<40} {credito['licencia']}")
    print(f"\nFotos cargadas: {cargadas} · ya tenían foto: {existentes} · sin foto elegida: {sin_foto}")


if __name__ == "__main__":
    main()
