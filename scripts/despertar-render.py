"""
Despierta los 10 servicios de Wok Control en Render antes de una demostración.

En el plan gratuito Render duerme cada servicio tras 15 minutos sin tráfico y tarda alrededor de un
minuto en despertarlo. Este script llama a la vez al health check de todos y espera a que respondan.
Uso:  python scripts/despertar-render.py [prefijo]     (prefijo por defecto: wokcontrol-exito)
"""
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

PREFIJO = sys.argv[1] if len(sys.argv) > 1 else "wokcontrol-exito"
SERVICIOS = ["config", "eureka", "usuarios", "menu", "pedidos", "inventario", "proveedores", "facturacion",
             "reportes", "gateway"]


def despertar(nombre, limite=300):
    url = f"https://{PREFIJO}-{nombre}.onrender.com/actuator/health"
    inicio = time.time()
    while time.time() - inicio < limite:
        try:
            with urllib.request.urlopen(url, timeout=90) as r:
                if b'"UP"' in r.read():
                    return f"  [OK ] {nombre:<12} {time.time() - inicio:5.0f} s"
        except Exception:
            pass
        time.sleep(5)
    return f"  [FALLA] {nombre:<12} no respondió en {limite} s ({url})"


if __name__ == "__main__":
    print(f"Despertando {len(SERVICIOS)} servicios ({PREFIJO}-*.onrender.com)...")
    with ThreadPoolExecutor(len(SERVICIOS)) as ex:
        for linea in ex.map(despertar, SERVICIOS):
            print(linea, flush=True)
    print(f"\nAplicación: https://{PREFIJO}-gateway.onrender.com  (espere ~30 s más a que el gateway vea todos los servicios)")
