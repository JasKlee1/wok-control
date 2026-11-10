# tasks.md · Épica 7 · Cliente en el salón

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

Por la constitución 2, cada historia tiene primero su tarea de pruebas y después la de implementación. Cada línea es una tarjeta T### del tablero del Sprint 1.

`[x]` significa que el código y las pruebas están en la rama. La tarea pasa a **Hecho** solo cuando la revisión de la otra persona del equipo la aprueba (DoD).

## Tareas

| Tarea | Responsable | Revisa | Estado |
|---|---|---|---|
| [x] T001 [P] [HU-036] Pruebas de HU-036 antes del código (AC-036.1, AC-036.2) | Yuly | Javier | En revisión |
| [x] T002 [HU-036] Rol CLIENTE y sesión de mesa (ADR-002) (REQ-WOK-036, NFR-WOK-003) | Yuly | Javier | En revisión |
| [x] T003 [P] [HU-033] Pruebas de HU-033 antes del código (AC-033.1, AC-033.2) | Yuly | Javier | En revisión |
| [x] T004 [HU-033] Carta de la sesión de mesa, solo disponibles (REQ-WOK-033) | Yuly | Javier | En revisión |
| [x] T005 [P] [HU-032] Pruebas de HU-032 antes del código (AC-032.1, AC-032.2) | Javier | Yuly | En revisión |
| [x] T006 [HU-032] pedidos-service · edición del pedido sin enviar (REQ-WOK-032) | Javier | Yuly | En revisión |
| [x] T007 [P] [HU-034] Pruebas de HU-034 antes del código (AC-034.1, AC-034.2, AC-034.3) | Javier | Yuly | En revisión |
| [x] T008 [HU-034] pedidos-service · pedido QR en POR_CONFIRMAR y estados por plato (REQ-WOK-034) | Javier | Yuly | En revisión |
| [x] T009 [P] [HU-037] Pruebas de HU-037 antes del código (AC-037.1, AC-037.2) | Javier | Yuly | En revisión |
| [x] T010 [HU-037] pedidos-service · confirmación o rechazo del mesero (REQ-WOK-037) | Javier | Yuly | En revisión |
| [x] T011 [P] [HU-038] Pruebas de HU-038 antes del código (AC-038.1, AC-038.2) | Yuly | Javier | En revisión |
| [x] T012 [HU-038] menu-service → pedidos-service · aviso de plato agotado (REQ-WOK-038) | Yuly | Javier | En revisión |
| [x] T013 [P] [HU-035] Pruebas de HU-035 antes del código (AC-035.1, AC-035.2) | Yuly | Javier | En revisión |
| [x] T014 [HU-035] pedidos-service → facturacion-service · solicitud de la cuenta (REQ-WOK-035) | Yuly | Javier | En revisión |
| [x] T015 [P] [HU-039] Pruebas de HU-039 antes del código (AC-039.1, AC-039.2) | Javier | Yuly | En revisión |
| [x] T016 [HU-039] Capa de vista · carta liviana y responsiva (NFR-WOK-001) | Javier | Yuly | En revisión |
| [x] T017 Matriz de trazabilidad de la épica, docs/ de endpoints y demo en Render | Yuly | Javier | En revisión |

[SUPUESTO] El responsable de cada T es el de su historia en la Pila de Producto (PBI-03 a PBI-10). T017 cierra el PBI-02, que está a cargo de Yuly.

## Evidencia de T001 a T004

| Tarea | Archivo de prueba | Qué comprueba |
|---|---|---|
| T001 | `pedidos-service/.../SesionMesaServiceTest.java` | AC-036.2: una sesión CERRADA ya no es válida. AC-036.1: una sesión no es válida para otra mesa. El token lleva la mesa, la sesión, el rol CLIENTE y CARTA_CLIENTE. |
| T001 | `pedidos-service/.../ClienteMesaWebTest.java` | AC-036.1: la sesión de la mesa 5 recibe 403 en `/api/mesas/m7/pedidos`, `/api/mesas/m7`, `/api/pedidos/p1` y `/api/mesas`, y la web la redirige a `/acceso-denegado`. AC-036.2: con la sesión vencida responde 401, pide escanear el QR y borra la cookie. |
| T001 | `usuarios-service/.../RolClienteTest.java` | El rol CLIENTE solo tiene CARTA_CLIENTE y no se puede asignar a usuarios. |
| T001 | `menu-service/.../MenuClienteQrSecurityTest.java` | El cliente ve las fotos y recibe 403 en el resto de menu-service. |
| T003 | `ClienteMesaWebTest.java`, `SesionMesaServiceTest.java` | AC-033.1: la carta muestra nombre, precio y foto. AC-033.2: un QR inexistente, inactivo, fuera de servicio o vacío responde 404 con «Mesa no disponible, pide ayuda al personal» y no deja sesión ni cookie. |
| T003 | `menu-service/.../MenuServiciosTest.java` | AC-033.1: con 40 platos y 2 agotados, la carta muestra 38. |

Resultado del 08/11/2026: `mvnw install` pasó con 138 pruebas, 0 fallos y 4 omitidas. Las omitidas son las de integración con Mongo, que requieren `WOK_TEST_MONGODB_URI`.

## Evidencia de T005 a T017

| Tarea | Archivo de prueba | Qué comprueba |
|---|---|---|
| T005 | `PedidoClienteServiceTest`, `ClientePedidoWebTest` | AC-032.1: cambiar la cantidad a 3 y agregar «sin cebolla» deja 3 unidades con la observación y el total pasa a $84.000. AC-032.2: quitar un plato de un pedido enviado responde «El pedido ya fue enviado; pide el cambio al mesero». AC-036.1: si el pedido es de otra mesa, 403. |
| T007 | `PedidoClienteServiceTest`, `ClientePedidoWebTest` | AC-034.1: el pedido de 3 platos queda POR_CONFIRMAR, no descuenta inventario, no llega a cocina y aparece en la bandeja del mesero. AC-034.2: cuando cocina pasa un plato a LISTO, el cliente lo ve «Listo» y el resto «En cocina». AC-034.3: sin platos, «Agrega al menos un plato». |
| T009 | `PedidoClienteServiceTest`, `ClientePedidoWebTest` | AC-037.1: al confirmar, el pedido pasa a ENVIADO (comanda) y descuenta inventario. AC-037.2: sin motivo, «Indica el motivo del rechazo»; con motivo, el cliente ve «Pedido rechazado: <motivo>». RN-01: un pedido QR no se envía a cocina sin confirmación. El cliente no puede confirmar ni rechazar. |
| T011 | `PedidoClienteServiceTest`, `ClientePedidoWebTest`, `menu-service/.../CocineroAgotaPlatoTest` | AC-038.1: el cocinero marca «Pato laqueado» agotado; el pedido sin enviar lo marca, no lo suma al total, muestra el aviso y cambia la huella para que el celular se recargue. AC-038.2: con un agotado, «Quita los platos agotados antes de enviar». |
| T013 | `PedidoClienteServiceTest`, `ClientePedidoWebTest` | AC-035.1: con todo entregado, la mesa queda como «Cuenta solicitada» con el total. AC-035.2: con 3 platos en preparación igual se registra y el cajero ve «Hay platos sin entregar». Sin pedidos no se pide la cuenta. |
| T015 | `CartaLivianaTest` | AC-039.1: 50 cargas de la carta de 40 platos; el p95 del servidor queda bajo 3 s, el HTML pesa menos de 60 KB y no carga Bootstrap; las fotos son diferidas. AC-039.2: viewport de celular, ningún ancho fijo mayor que 360 px y botones de 44 px. La medición con red 4G sobre Render se hace con `scripts/medir-carta.ps1`. |

Resultado del 09/11/2026: `mvnw install` pasó con 175 pruebas (37 nuevas), 0 fallos y 4 omitidas (las mismas de Mongo).

[SUPUESTO] La mesa todavía no tiene un mesero asignado; eso llega con la HU-042 en el Sprint 2. Por eso el pedido QR toma al mesero que ya atiende la mesa, si lo hay, y si no aparece a todos los meseros en la bandeja «Pedidos por QR por confirmar» de `/pedidos`.

Hallazgo para el Sprint 2: en las rutas web que llevan el token en la cookie, Spring Security no exige el token CSRF, porque lo trata como un token *bearer*. Viene de la v1. La cookie `SameSite=Lax` impide que otro sitio envíe esos POST, así que el riesgo es bajo, pero conviene revisarlo con el análisis estático de la PBI-29.
