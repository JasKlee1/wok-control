# Pruebas de rendimiento de los NFR · v2.0 (PBI-28)

| NFR | Criterio | Límite | Resultado (27/11/2026) | Cumple |
|---|---|---|---|---|
| NFR-WOK-001 (HU-039) | p95 de 50 cargas de la carta del cliente con 40 platos | < 3000 ms | **57 ms** (máximo 114 ms) en el servidor | Sí |
| NFR-WOK-001 (HU-039) | Peso del HTML de la carta, sin fotos | < 60 KB | Cumple (`CartaLivianaTest`) | Sí |
| NFR-WOK-004 (HU-046) | Peor caso para que otra tablet vea el cambio de una mesa: intervalo de sondeo + p95 de huella y mapa (50 ciclos) | ≤ 5000 ms | **3013 ms** (3000 ms + 13 ms) | Sí |

No se registró ningún IMP: los dos p95 quedan dentro del límite.

## Cómo se midió

- `pedidos-service/src/test/java/.../CartaLivianaTest.ac039_1_*`: 50 peticiones `GET /cliente/carta` con una sesión de mesa y una carta de 40 platos en 5 categorías. Calcula el p95 y lo escribe en `pedidos-service/target/evidencias-rendimiento/carta-p95.txt`.
- `SalonWebTest.pbi28_laActualizacionDelMapaDeMesasLlegaEnCincoSegundosOMenos`: 50 ciclos `GET /salon/estado` + `GET /salon/vivo` con el rol ANFITRION y 12 mesas. Escribe `mesas-actualizacion.txt`.
- Las dos pruebas corren en cada `mvnw clean install`, así que una regresión de rendimiento rompe la compilación.

## Red móvil (4G) sobre Render

La parte de red se estima con el peso de la página: con el perfil «4G lento» de Chrome (1,6 Mbit/s ≈ 200 KB/s), un HTML de menos de 60 KB más la hoja de iconos tarda menos de 0,5 s en descargarse. Sumado al tiempo del servidor, el p95 queda lejos de 3 s.

La medición real se hace en la Review, con los servicios despiertos (`python scripts/despertar-render.py wokcontrol-s1`) y la URL del QR de una mesa:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\medir-carta.ps1 -Qr "https://wokcontrol-s1-gateway-lu10.onrender.com/cliente/mesa/<codigo>"
```

El script hace una carga de calentamiento (Render gratuito duerme los servicios), 50 cargas con `curl --limit-rate 200K` y muestra el promedio y el p95. Si el p95 pasa de 3 s, se registra un IMP y no se libera (AC-C02.3).
