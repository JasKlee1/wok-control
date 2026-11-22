# tasks.md · Épica 9 · Domicilios (CR-001)

| Campo | Valor |
|---|---|
| Versión | 1.0 (borrador para la Puerta 3) |
| Spec · Plan | [spec.md](spec.md) · [plan.md](plan.md) |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta 3) | Javier Steven Becerra Mantilla (PO) |

Marcas del taller:
- `[P]`: la tarea se puede hacer en paralelo.
- `[HU-###]`: la historia a la que pertenece.
- Entre paréntesis: la trazabilidad a criterios (AC) o requisitos (REQ/NFR).

Por la constitución 2, cada historia tiene primero su tarea de pruebas y después la de implementación. Cada línea es una tarjeta T### del tablero del Sprint 3; nacieron al aprobar la Puerta del delta (PBI-19).

`[x]` significa que el código y las pruebas están en la rama `sprint-3/epica-9-domicilios`. La tarea pasa a **Hecho** solo cuando la revisión de la otra persona del equipo la aprueba (DoD).

## Tareas

| Tarea | Responsable | Revisa | Estado |
|---|---|---|---|
| [x] T001 [HU-053] Pruebas de HU-053 antes del código (AC-053.1, AC-053.2, AC-053.3) | Yuly | Javier | En revisión |
| [x] T002 [HU-053] Nuevo domicilios-service: módulo, base `wok_domicilios`, config centralizada, gateway, scripts, compose y render.yaml (REQ-WOK-053, NFR-WOK-006) | Yuly | Javier | En revisión |
| [x] T003 [P] [HU-051] Pruebas de HU-051 antes del código (AC-051.1, AC-051.2) | Javier | Yuly | En revisión |
| [x] T004 [HU-051] Rol DOMICILIARIO, permisos DOMICILIOS_GESTIONAR / DOMICILIOS_REPARTIR, menú y seguridad (REQ-WOK-051, NFR-WOK-003) | Javier | Yuly | En revisión |
| [x] T005 [P] [HU-047] Pruebas de HU-047 antes del código (AC-047.1, AC-047.2, AC-047.3) | Javier | Yuly | En revisión |
| [x] T006 [HU-047] domicilios-service → pedidos-service · registrar, consultar, editar y cancelar; pedido sin mesa a cocina (REQ-WOK-047) | Javier | Yuly | En revisión |
| [x] T007 [P] [HU-048] Pruebas de HU-048 antes del código (AC-048.1, AC-048.2, RN-14) | Yuly | Javier | En revisión |
| [x] T008 [HU-048] domicilios-service → usuarios-service · domiciliarios disponibles y asignación (REQ-WOK-048) | Yuly | Javier | En revisión |
| [x] T009 [P] [HU-049] Pruebas de HU-049 antes del código (AC-049.1, AC-049.2, NFR-WOK-007) | Javier | Yuly | En revisión |
| [x] T010 [HU-049] «Mis entregas», en camino, QR de la ruta con la API externa goQR.me y rótulo (REQ-WOK-049, ADR-005) | Javier | Yuly | En revisión |
| [x] T011 [P] [HU-050] Pruebas de HU-050 antes del código (AC-050.1, AC-050.2) | Yuly | Javier | En revisión |
| [x] T012 [HU-050] domicilios-service → facturacion-service · cobro contra entrega, comprobante y cierre de caja (REQ-WOK-050) | Yuly | Javier | En revisión |
| [x] T013 [P] [HU-052] Pruebas de HU-052 antes del código (AC-052.1, AC-052.2) | Javier | Yuly | En revisión |
| [x] T014 [HU-052] No entregado con motivo y anulación sin factura (REQ-WOK-052) | Javier | Yuly | En revisión |
| [x] T015 Matriz de trazabilidad de la épica, docs/ de endpoints y seguridad, y guion de la demo | Yuly | Javier | En revisión |

[SUPUESTO] El responsable de cada T es el de su historia en la Pila de Producto (PBI-20 a PBI-26). T015 cierra el PBI-19, que está a cargo de Yuly.

## Evidencia de T001 a T014

| Tarea | Archivo de prueba | Qué comprueba |
|---|---|---|
| T001 | `domicilios-service/.../DomiciliosSecurityTest` | AC-053.1: `GET /api/domicilios/d1` con token del cajero responde el domicilio (número, estado, etiqueta y `urlRuta`); sin token, 401. |
| T001 | `DomicilioServiceTest.ac053_2_*` | AC-053.2: el historial tiene RECIBIDO → LISTO → ASIGNADO → EN_CAMINO → ENTREGADO con fecha (Instant UTC) y usuario (cocina, cajero, luis) y la auditoría central recibe 5 eventos `ESTADO_DOMICILIO` sin teléfono, dirección ni nombre del cliente. |
| T001 | `domicilios-service/.../config/ValidacionArranqueTest` | AC-053.3: sin `spring.mongodb.uri`, vacía o con un valor que no es `mongodb://` o `mongodb+srv://`, el arranque falla con el mensaje fijo y sin el valor; con una cadena válida arranca. |
| T003 | `DomicilioServiceTest.ac049_1_y_ac051_1_*`, `DomiciliosSecurityTest.ac051_1_*` | AC-051.1: Marta recibe 403 al consultar o mover el domicilio de Luis; la API responde 403. |
| T003 | `facturacion-service/.../CajaDomiciliarioTest`, `inventario-service/.../InventarioDomiciliarioTest`, `DomiciliosSecurityTest` | AC-051.2: `/caja/cuentas`, `/caja/comprobantes`, `/caja/cierres` y el cobro llevan a `/acceso-denegado`; `GET /api/cuentas/m3`, `POST /api/facturas`, `POST /api/facturas/domicilios`, `GET /api/insumos` y los movimientos responden 403. El domiciliario tampoco lista todos los domicilios, ni registra ni asigna. |
| T003 | `usuarios-service/.../RolDomiciliarioTest` | El rol DOMICILIARIO solo tiene DASHBOARD_VER y DOMICILIOS_REPARTIR; cajero y administrador tienen DOMICILIOS_GESTIONAR y el mesero ninguno; el menú del domiciliario muestra «Mis entregas» y no Domicilios, Caja, Pedidos ni Inventario. |
| T005 | `DomicilioServiceTest.ac047_*`, `DomiciliosSecurityTest.ac047_2_*` | AC-047.1: «Ana Pérez» queda RECIBIDO con platos $37.500 + impuesto $3.000 + domicilio $4.500 = $45.000 y el pedido P-0001 se pide a cocina con el id del domicilio. AC-047.2: teléfono «12345» → campo `telefono`; dirección vacía → `direccion`; no se crea pedido; la API responde 400 con `errores.telefono` y `errores.direccion`. AC-047.3: antes del despacho se edita (total $46.500 con domicilio de $6.000); EN_CAMINO → `YA_DESPACHADO`. |
| T005 | `pedidos-service/.../PedidoDomicilioTest` | El pedido del domicilio va a cocina (ENVIADO) sin mesa, con destino «Domicilio D-0001», descuenta inventario y no toca mesas; crear es idempotente; el mesero no lo cancela ni lo entrega. |
| T007 | `DomicilioServiceTest.ac048_1_y_ac048_2_*`, `rn14_*` | AC-048.2: con el pedido EN_PREPARACION → `PEDIDO_NO_LISTO`. AC-048.1: cuando cocina lo marca LISTO queda ASIGNADO a Luis Pabón. RN-14: Luis en camino no está disponible (`DOMICILIARIO_NO_DISPONIBLE`) y Marta sí. |
| T009 | `DomicilioServiceTest.ac049_*`, `DomiciliosSecurityTest.qrDeLaRuta*`, `client/QrRutaClientTest` | AC-049.1: el domiciliario consulta solo sus ASIGNADOS y EN_CAMINO. AC-049.2: ASIGNADO → ENTREGADO → `TRANSICION_INVALIDA` («márquelo primero en camino») y no se factura. ADR-005 / NFR-WOK-007: la URL de la ruta lleva la dirección y la ciudad; la llamada a api.qrserver.com lleva `size`, `format=png` y `data` codificada (el `#` viaja como `%2523`); la segunda vez sale de la caché; si la API falla o no responde un PNG, vacío; el endpoint responde `image/png` o 503. |
| T011 | `DomicilioServiceTest.ac050_*`, `facturacion-service/.../FacturaDomicilioTest` | AC-050.1: con $50.000 en efectivo el comprobante TIQ queda por $45.000 (impuesto solo sobre los platos), cambio $5.000, domicilio ENTREGADO, pedido entregado y pagado; el comprobante queda a nombre del cajero que registró (caja1) para su cierre. AC-050.2: $40.000 → `PAGO_INSUFICIENTE`, sigue EN_CAMINO y no se llama a pedidos ni a facturación. Con tarjeta se cobra el valor exacto. Cobrar es idempotente por domicilio. |
| T013 | `DomicilioServiceTest.ac052_*`, `PedidoDomicilioTest.ac052_1_*` | AC-052.1: «El cliente no contesta el teléfono» deja NO_ENTREGADO, anula el pedido sin factura, no devuelve inventario y audita `EN_CAMINO → NO_ENTREGADO · motivo`. AC-052.2: «no» → `MOTIVO_OBLIGATORIO` y sigue EN_CAMINO. Cancelar antes de que cocina empiece sí devuelve el inventario. |

Resultado del 22/11/2026: `mvnw install` pasó con 257 pruebas (44 nuevas), 0 fallos y 4 omitidas (las mismas de Mongo, que requieren `WOK_TEST_MONGODB_URI`).

**T015:** la demo local con los 11 servicios y MongoDB Atlas sigue el guion `Guion_Demo_Sprint3_27-11.md`. El código está en la rama `sprint-3/epica-9-domicilios` en GitHub, **sin desplegar**: Render despliega solo desde `main` y la rama se une después de la revisión.

## Matriz de trazabilidad de la épica 9

| HU | Requisito | Criterios | Componentes | Pruebas |
|---|---|---|---|---|
| HU-047 | REQ-WOK-047 | AC-047.1, AC-047.2, AC-047.3 | `DomicilioService.registrar/editar/cancelar`, `DomicilioApiRestController`, `DomicilioWebController`, `domicilios/form.html`, `PedidoService.crearDomicilio`, `PedidoDomicilioApiRestController` | `DomicilioServiceTest.ac047_*`; `DomiciliosSecurityTest.ac047_2_*`; `PedidoDomicilioTest` |
| HU-048 | REQ-WOK-048 | AC-048.1, AC-048.2 | `DomicilioService.asignar/domiciliarios/sincronizarCocina`, `UsuariosClient`, `UsuarioApiRestController.domiciliarios`, `domicilios/lista.html`, `domicilios/detalle.html` | `DomicilioServiceTest.ac048_*`, `rn14_*`, `elPedidoCanceladoEnCocina…` |
| HU-049 | REQ-WOK-049, NFR-WOK-007 | AC-049.1, AC-049.2 | `DomicilioService.mios/obtenerPara/enCamino`, `EntregaWebController`, `entregas/lista.html`, `QrRutaClient`, `domicilios/rotulo.html` | `DomicilioServiceTest.ac049_*`; `DomiciliosSecurityTest.qrDeLaRuta…`; `QrRutaClientTest` |
| HU-050 | REQ-WOK-050 | AC-050.1, AC-050.2 | `DomicilioService.entregar`, `FacturacionClient`, `FacturacionService.cobrarDomicilio`, `Factura.DatosDomicilio`, `caja/comprobante.html`, `caja/cierre.html` | `DomicilioServiceTest.ac050_*`; `FacturaDomicilioTest` |
| HU-051 | REQ-WOK-051, NFR-WOK-003 | AC-051.1, AC-051.2 | `Roles.DOMICILIARIO`, `Permisos.DOMICILIOS_*`, `Navegacion`, `SecurityConfig` (domicilios, facturación, pedidos, usuarios), `GatewaySecurityConfig` | `DomiciliosSecurityTest`; `CajaDomiciliarioTest`; `InventarioDomiciliarioTest`; `RolDomiciliarioTest` |
| HU-052 | REQ-WOK-052 | AC-052.1, AC-052.2 | `DomicilioService.noEntregado`, `PedidoService.anularDomicilio`, `entregas/lista.html` | `DomicilioServiceTest.ac052_*`; `PedidoDomicilioTest.ac052_1_*` |
| HU-053 | REQ-WOK-053, NFR-WOK-006 | AC-053.1, AC-053.2, AC-053.3 | `domicilios-service`, `ValidacionArranque`, `Domicilio.historial`, `DomicilioService.auditar`, `api-gateway.yml`, `render.yaml` | `DomiciliosSecurityTest.ac053_1_*`; `DomicilioServiceTest.ac053_2_*`; `ValidacionArranqueTest` |

## Pendientes

- **Despliegue en Render:** el servicio `wokcontrol-exito-domicilios` queda declarado en `render.yaml`, pero no se crea hasta unir la rama a `main` y sincronizar el Blueprint (decisión del equipo: no desplegar en este sprint sin revisión).
- **Hallazgo CSRF:** sigue para el análisis estático de la PBI-29 (Sprint 4). Las pantallas nuevas usan formularios Thymeleaf con token CSRF.
