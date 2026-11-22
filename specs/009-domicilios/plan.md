# plan.md · Épica 9 · Domicilios (CR-001)

| Campo | Valor |
|---|---|
| Versión | 1.0 (borrador para la Puerta 2) |
| Spec | [spec.md](spec.md) v1.0 (delta-spec) |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta 2) | Javier Steven Becerra Mantilla (PO) |

## 1. Contexto técnico

Es el mismo de los sprints 1 y 2: Java 21 y Spring Boot 4 en microservicios, Spring Cloud (Config Server, Eureka y Gateway), MongoDB Atlas con una base por servicio, Thymeleaf servido por cada servicio detrás del gateway y JWT con los permisos firmados.

A diferencia de las épicas 7 y 8, esta épica **sí agrega un microservicio** (`domicilios-service`, puerto 8088) con su base `wok_domicilios`, porque la HU-053 lo pide de forma explícita y el domicilio tiene su propio ciclo de vida. No agrega dependencias de Maven nuevas: el cliente HTTP de la API externa es el `RestClient` de Spring que ya usan los servicios.

## 2. Dónde vive cada parte

| Parte | Servicio | Por qué |
|---|---|---|
| Rol DOMICILIARIO y permisos DOMICILIOS_GESTIONAR / DOMICILIOS_REPARTIR | wok-common (`Roles`, `Permisos`, `Navegacion`); usuarios-service los sincroniza al arrancar | `Roles.SISTEMA` es la fuente única de verdad, igual que CLIENTE y ANFITRION. |
| Domicilio, cliente, estados, historial, asignación y cobro | domicilios-service, colección `domicilios` en `wok_domicilios` | Un servicio y una base por dominio (constitución 3, HU-053). Los datos personales del cliente quedan solo en esta base. |
| Pedido de cocina del domicilio | pedidos-service (`Pedido` con `origen = DOMICILIO` y sin mesa) | La cocina, la cotización con la carta vigente y el descuento de inventario ya viven ahí; el tablero de cocina no cambia de fuente. |
| Comprobante del cobro contra entrega | facturacion-service (`Factura.domicilio`) | La numeración TIQ-, el cierre de caja y los reportes de ventas ya viven ahí. |
| Lista de domiciliarios | usuarios-service (`GET /api/usuarios/internos/domiciliarios`) con token de servicio | Los usuarios viven en usuarios-service; solo entrega usuario y nombre. |
| Código QR de la ruta | domicilios-service → API externa goQR.me (ADR-005) | Se llama desde el servidor para no exponer datos del navegador a terceros sin control y poder cachear. |

## 3. ADR-004 · Microservicio de domicilios orquestando pedidos y facturación

**Estado:** propuesta para la Puerta 2 del Sprint 3 (23/11/2026). Implementada en la rama `sprint-3/epica-9-domicilios` con sus pruebas.

**Contexto.** El domicilio necesita los platos de la carta, la cocina, el inventario y la caja, que ya existen en tres servicios. La HU-053 exige una base propia.

**Decisión.**

1. `domicilios-service` es el dueño del domicilio y **orquesta** las otras partes con llamadas internas (`ClienteInterno` con token de servicio, permiso SISTEMA y claim `actor` con el usuario que originó la acción):
   - Registrar → `POST /api/pedidos/internos/domicilios`: pedidos-service crea el pedido sin mesa, lo cotiza con la carta vigente, descuenta inventario y lo envía a cocina (ENVIADO). Es idempotente por `domicilioId`. Si el domicilio no se puede guardar, se anula ese pedido (compensación).
   - Avance de cocina → `POST /api/pedidos/internos/domicilios/estados` (un solo llamado para todos los domicilios en cocina): ABIERTO/ENVIADO → RECIBIDO, EN_PREPARACION → EN_PREPARACION, LISTO → LISTO, CANCELADO → CANCELADO. Se sincroniza al abrir las pantallas y al asignar.
   - Entregar → primero valida el pago en domicilios-service (sin efectos), luego `POST /api/pedidos/internos/domicilios/{id}/entrega` (LISTO → ENTREGADO) y `POST /api/facturas/domicilios`. Las dos llamadas son idempotentes (`DOM-{domicilioId}`), así que si algo falla el domicilio sigue EN_CAMINO y se puede reintentar.
   - Cancelar o no entregado → `POST /api/pedidos/internos/domicilios/{id}/anulacion`: si cocina no empezó, cancelación normal con reversión de inventario; si ya se preparó, se anula sin factura y sin devolver inventario (RN-17).
2. Facturación calcula el total con la misma `CalculadoraCuenta` del salón (impuesto al consumo sobre los platos) y suma el valor del domicilio sin impuesto (RN-12). El comprobante queda a nombre del cajero que registró (`cajero`) y guarda quién cobró (`domicilio.domiciliario`), para el cierre de caja (RN-16).
3. Estados con `@Version` en el documento: si dos pantallas cambian el mismo domicilio, una gana y la otra recarga el estado guardado.
4. El gateway bloquea desde fuera las rutas internas nuevas (`/api/pedidos/internos/**`, `/api/facturas/domicilios/**`).
5. Arranque: `ValidacionArranque` revisa `spring.mongodb.uri` antes de crear el contexto y detiene el servicio con un mensaje que no incluye el valor (AC-053.3).

**Alternativas descartadas.**
- Guardar el domicilio en pedidos-service: incumple la HU-053 (una base por dominio) y mezcla datos personales del cliente con los pedidos de mesa.
- Eventos asíncronos (cola de mensajes) entre servicios: agregan infraestructura que Render gratuito no ofrece; las llamadas idempotentes con compensación bastan para el volumen del restaurante.
- Que el domiciliario tenga caja propia: el restaurante cuadra la caja por cajero (spec, acta 3).

**Consecuencias.**
- El domicilio depende de pedidos-service para registrar y entregar. Si no responde, el cajero ve el error y nada queda a medias.
- El avance de cocina se ve al abrir o refrescar la pantalla (no en vivo), suficiente para el flujo telefónico.

## 4. ADR-005 · API externa goQR.me para el QR de la ruta

**Estado:** propuesta para la Puerta 2 del Sprint 3 (23/11/2026). Implementada en `QrRutaClient`.

**Contexto.** El domiciliario llama al cliente para preguntar cómo llegar. Un código QR con la ruta en Google Maps, en el rótulo de la bolsa y en su pantalla, resuelve eso con el celular que ya tiene.

**Decisión.**

1. La URL de la ruta es `https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=<dirección, ciudad>` (formato público de Google Maps, sin clave). La ciudad viene de `wok.domicilios.ciudad` (por defecto «Girón, Santander, Colombia»).
2. La imagen del QR se pide a la API pública **goQR.me** (`GET https://api.qrserver.com/v1/create-qr-code/?size=300x300&margin=8&ecc=M&format=png&data=<url de la ruta>`), sin registro ni clave. La llama el servidor (`RestClient`, conexión 3 s y lectura 5 s) y entrega el PNG por `GET /api/domicilios/{id}/qr-ruta` y `GET /domicilios/{id}/qr-ruta.png`.
3. Caché en memoria de hasta 200 imágenes por URL de ruta: el QR de un domicilio se pide una sola vez aunque se abra varias veces.
4. Se valida la firma PNG de la respuesta. Si la API falla, tarda o responde otra cosa, se devuelve 503 y la página muestra el enlace «Abrir ruta» (NFR-WOK-007). Se puede apagar con `WOK_QR_HABILITADO=false`.
5. Cada llamada exitosa deja en el registro `API externa api.qrserver.com: QR de la ruta del domicilio D-0001 generado (… bytes, … ms)`, sin la dirección.

**Protección de datos (Ley 1581 de 2012).** A goQR.me solo viaja la URL de Google Maps con la dirección (sin nombre ni teléfono), que es el mismo dato que se imprime en el rótulo. Antes de producción el equipo debe revisar la política de privacidad vigente de goQR.me. Si el restaurante no acepta enviar la dirección a un tercero, `WOK_QR_HABILITADO=false` deja solo el enlace.

**Alternativas descartadas.**
- Librería ZXing para generar el QR localmente: agrega una dependencia y no cumple el objetivo del curso de integrar una API externa demostrable.
- API de Google Maps (Geocoding o Static Maps): exige clave de facturación.
- Generar el QR en el navegador con JavaScript: expone la llamada desde cada celular y no se puede cachear ni registrar.

## 5. Datos

| Colección (wok_domicilios) | Campos |
|---|---|
| `domicilios` (nueva) | `numero` (D-0001), `cliente` {`nombre`, `telefono`, `direccion`, `referencia`}, `lineas` [{`productoId`, `nombre`, `cantidad`, `precioUnitario`, `subtotal`}], `observaciones`, `subtotalPlatos`, `impuestoPorcentaje`, `impuestoValor`, `valorDomicilio`, `total`, `estado`, `pedidoId` (único), `pedidoNumero`, `registradoPor(Nombre)`, `domiciliario(Nombre)`, `asignadoEn`, `enCaminoEn`, `entregadoEn`, `cerradoEn`, `cobro` {`metodo`, `recibido`, `cambio`, `facturaId`, `facturaNumero`, `referencia`, `fecha`}, `motivoNoEntrega`, `motivoCancelacion`, `historial` [{`fecha` UTC, `usuario`, `anterior`, `nuevo`, `detalle`}], `version` |
| `secuencias` | `domicilios` |

| Colección existente | Campos nuevos |
|---|---|
| `wok_pedidos.pedidos` | `origen = DOMICILIO`, `domicilioId` (índice disperso), `domicilioNumero`; `mesaId` vacío |
| `wok_facturacion.facturas` | `domicilio` {`domicilioId`, `numero`, `valorDomicilio`, `domiciliario`, `domiciliarioNombre`}; clave de idempotencia `DOM-{domicilioId}` |

## 6. Contrato (rutas nuevas del Sprint 3)

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `GET /api/domicilios` | DOMICILIOS_GESTIONAR | Domicilios en curso con el avance de cocina al día |
| `POST /api/domicilios` | DOMICILIOS_GESTIONAR | 201 con el domicilio, o 400 con `errores.telefono` / `errores.direccion` |
| `GET /api/domicilios/{id}` · `/{id}/historial` | GESTIONAR, o REPARTIR si es suyo (si no, 403) | Domicilio con `urlRuta`; historial de estados |
| `GET /api/domicilios/{id}/qr-ruta` | GESTIONAR, o REPARTIR si es suyo | `image/png` (API externa) o 503 |
| `PUT /api/domicilios/{id}` · `POST /{id}/cancelacion` | DOMICILIOS_GESTIONAR | Editar o cancelar antes del despacho (`YA_DESPACHADO`) |
| `GET /api/domicilios/domiciliarios` | DOMICILIOS_GESTIONAR | Domiciliarios con `disponible`, `enCamino`, `asignados` |
| `POST /api/domicilios/{id}/asignacion` | DOMICILIOS_GESTIONAR | `PEDIDO_NO_LISTO`, `DOMICILIARIO_NO_DISPONIBLE` o `TRANSICION_INVALIDA` (422) |
| `GET /api/domicilios/mios` | DOMICILIOS_REPARTIR | Los ASIGNADOS y EN_CAMINO del domiciliario |
| `POST /api/domicilios/{id}/en-camino` · `/entrega` · `/no-entregado` | DOMICILIOS_REPARTIR (suyo) | `TRANSICION_INVALIDA`, `PAGO_INSUFICIENTE`, `MOTIVO_OBLIGATORIO` (422) |
| `GET /domicilios`, `/domicilios/nuevo`, `/domicilios/{id}`, `/{id}/editar`, `/{id}/rotulo` | DOMICILIOS_GESTIONAR | Pantallas del cajero |
| `GET /mis-entregas` y sus acciones | DOMICILIOS_REPARTIR | Pantalla del domiciliario (celular) |
| `POST /api/pedidos/internos/domicilios` · `/estados` · `/{id}/entrega` · `/{id}/anulacion` | SISTEMA (interno) | Pedido de cocina del domicilio |
| `POST /api/facturas/domicilios` | SISTEMA (interno) | Comprobante del cobro contra entrega |
| `GET /api/usuarios/internos/domiciliarios` | SISTEMA (interno) | Usuario y nombre de los domiciliarios activos |

El gateway enruta `/api/domicilios/**`, `/domicilios/**`, `/mis-entregas/**` y `/docs/domicilios/**` a domicilios-service.

## 7. Auditoría

`ESTADO_DOMICILIO` (cada cambio de estado: `ANTERIOR → NUEVO · detalle`), `EDITAR_DOMICILIO`, `CREAR_DOMICILIO`, `ENTREGAR_DOMICILIO`, `ANULAR_DOMICILIO` (pedidos) y `COBRAR_DOMICILIO` (facturación). Ninguno incluye teléfono ni dirección (RN-18). El usuario y la fecha UTC los pone la auditoría central.

## 8. Matriz requisito → componente

| Requisito | Componentes |
|---|---|
| REQ-WOK-047 | `Domicilio`, `DomicilioService.registrar/editar/cancelar/validarCliente/totales`, `DomicilioApiRestController`, `DomicilioWebController`, `domicilios/form.html`, `domicilios/detalle.html`; `PedidoService.crearDomicilio/anularDomicilio`, `PedidoDomicilioApiRestController` |
| REQ-WOK-048 | `DomicilioService.asignar/domiciliarios/sincronizarCocina`, `UsuariosClient`, `UsuarioApiRestController.domiciliarios`, `domicilios/lista.html` |
| REQ-WOK-049 | `DomicilioService.mios/obtenerPara/enCamino/entregar`, `EntregaWebController`, `entregas/lista.html`, `QrRutaClient`, `domicilios/rotulo.html` |
| REQ-WOK-050 | `DomicilioService.entregar`, `FacturacionClient`, `FacturacionService.cobrarDomicilio`, `Factura.DatosDomicilio`, `caja/comprobante.html`, `caja/cierre.html` |
| REQ-WOK-051 · NFR-WOK-003 | `Roles.DOMICILIARIO`, `Permisos.DOMICILIOS_*`, `Navegacion`, `SecurityConfig` (domicilios, facturación, pedidos, usuarios), `GatewaySecurityConfig` |
| REQ-WOK-052 | `DomicilioService.noEntregado`, `PedidoService.anularDomicilio` |
| REQ-WOK-053 · NFR-WOK-006 | `domicilios-service` (pom, `application.yml`, `config/domicilios-service.yml`), `ValidacionArranque`, `Domicilio.historial`, `DomicilioService.auditar`, `api-gateway.yml`, `render.yaml`, `compose.yaml` |
| NFR-WOK-007 | `QrRutaClient` (timeout, caché, validación PNG, respaldo), `detalle.html` y `rotulo.html` (enlace si falla) |
