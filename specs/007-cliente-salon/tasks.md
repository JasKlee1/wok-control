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
| [ ] T005 [P] [HU-032] Pruebas de HU-032 antes del código (AC-032.1, AC-032.2) | Javier | Yuly | Por hacer |
| [ ] T006 [HU-032] pedidos-service · edición del pedido sin enviar (REQ-WOK-032) | Javier | Yuly | Por hacer |
| [ ] T007 [P] [HU-034] Pruebas de HU-034 antes del código (AC-034.1, AC-034.2, AC-034.3) | Javier | Yuly | Por hacer |
| [ ] T008 [HU-034] pedidos-service · pedido QR en POR_CONFIRMAR y estados por plato (REQ-WOK-034) | Javier | Yuly | Por hacer |
| [ ] T009 [P] [HU-037] Pruebas de HU-037 antes del código (AC-037.1, AC-037.2) | Javier | Yuly | Por hacer |
| [ ] T010 [HU-037] pedidos-service · confirmación o rechazo del mesero (REQ-WOK-037) | Javier | Yuly | Por hacer |
| [ ] T011 [P] [HU-038] Pruebas de HU-038 antes del código (AC-038.1, AC-038.2) | Yuly | Javier | Por hacer |
| [ ] T012 [HU-038] menu-service → pedidos-service · aviso de plato agotado (REQ-WOK-038) | Yuly | Javier | Por hacer |
| [ ] T013 [P] [HU-035] Pruebas de HU-035 antes del código (AC-035.1, AC-035.2) | Yuly | Javier | Por hacer |
| [ ] T014 [HU-035] pedidos-service → facturacion-service · solicitud de la cuenta (REQ-WOK-035) | Yuly | Javier | Por hacer |
| [ ] T015 [P] [HU-039] Pruebas de HU-039 antes del código (AC-039.1, AC-039.2) | Javier | Yuly | Por hacer |
| [ ] T016 [HU-039] Capa de vista · carta liviana y responsiva (NFR-WOK-001) | Javier | Yuly | Por hacer |
| [ ] T017 Matriz de trazabilidad de la épica, docs/ de endpoints y demo en Render | Yuly | Javier | Por hacer |

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
