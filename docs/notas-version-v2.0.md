# Notas de versión · WokControl 2.0.0

**Fecha:** 04/12/2026 (Sprint 4) · **Versión anterior:** 1.0 (v1, 31 HU) · **Rama:** `sprint-4/cierre-v2` → `main` · **Etiqueta:** `v2.0.0`

**Product Goal v2:** que WokControl atienda a cada cliente desde que llega a la puerta, pide en su mesa o llama a domicilio hasta que paga, sin comandas en papel ni llamadas perdidas y con cada paso registrado y auditado.

## Novedades

### Épica 7 · Cliente en el salón (Sprint 1 · HU-032 a HU-039)
- Nuevo rol **CLIENTE** como sesión de mesa: se abre al escanear el QR, no tiene usuario ni contraseña y vence al cobrar la cuenta o liberar la mesa.
- Carta desde el celular con fotos y solo los platos disponibles, liviana (menos de 60 KB) y adaptada a 360 px.
- Pedido desde la mesa, confirmación o rechazo del mesero antes de cocina, seguimiento por plato, aviso de plato agotado y solicitud de la cuenta.

### Épica 8 · Anfitrión y gestión del salón (Sprint 2 · HU-040 a HU-046)
- Nuevo rol **ANFITRION**: mapa del salón con estado y capacidad, lista de espera, asignación de mesa y mesero, unión de mesas para grupos grandes.
- La mesa pasa a «por limpiar» al cobrar y el anfitrión la libera.
- El mapa se actualiza en las tablets cada 3 s sin recargar.

### Épica 9 · Domicilios, CR-001 (Sprint 3 · HU-047 a HU-053)
- Nuevo microservicio **domicilios-service** (puerto 8088, base `wok_domicilios`).
- Nuevo rol **DOMICILIARIO**: «Mis entregas», en camino, cobro contra entrega (efectivo o tarjeta) con comprobante en el cierre de caja del cajero, y «no entregado» con motivo.
- QR de la ruta en Google Maps generado con la API externa goQR.me (ADR-005), con caché y opción de apagarlo (`WOK_QR_HABILITADO=false`).

### Sprint 4 · Calidad y liberación (PBI-27 a PBI-32)
- **Seguridad:** el token CSRF se exige también cuando la petición trae la cookie de sesión (ADR-006, hallazgo heredado de la v1). La cabecera `X-Correlation-Id` se valida antes de copiarse a la respuesta y a los registros.
- **Ley 1581 de 2012:** pruebas que garantizan que el nombre, el teléfono y la dirección del cliente del domicilio no se escriben en los registros.
- **Rendimiento:** p95 de la carta = 57 ms en el servidor (límite 3 s); actualización del mapa de mesas en el peor caso = 3013 ms (límite 5 s).
- Versión **2.0.0** en todos los módulos, el `Dockerfile` y los scripts de arranque.

## Cifras

| | v1 | v2.0 |
|---|---|---|
| Historias de usuario | 31 | 53 |
| Roles | 7 | 10 |
| Microservicios de negocio | 7 | 8 |
| Pruebas automatizadas | 110 (19/10/2026) | 264 (27/11/2026) |

## Cambios que afectan la operación

- **Nueva variable** del servicio de domicilios: `WOK_QR_HABILITADO` (por defecto `true`).
- **Nuevo servicio en Render:** `wokcontrol-s1-domicilios-lu10` (o `wokcontrol-exito-domicilios` con `render.yaml`).
- **Formularios web:** cualquier formulario nuevo debe usar `th:action` para incluir el token CSRF; un POST sin token responde con la página de acceso no permitido.
- Los jar ahora se llaman `<módulo>-2.0.0.jar` (antes `-1.0.0-SNAPSHOT.jar`).

## Problemas conocidos

- Render gratuito duerme los servicios: la primera petición después de 15 minutos tarda hasta un minuto. Se despiertan con `scripts/despertar-render.py`.
- Los comprobantes son internos de venta, no facturas electrónicas DIAN.

## Retroceso

Ver [plan-retroceso.md](plan-retroceso.md).
