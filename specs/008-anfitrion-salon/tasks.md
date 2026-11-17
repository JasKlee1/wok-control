# tasks.md · Épica 8 · Anfitrión y gestión del salón

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

Por la constitución 2, cada historia tiene primero su tarea de pruebas y después la de implementación. Cada línea es una tarjeta T### del tablero del Sprint 2.

`[x]` significa que el código y las pruebas están en la rama `sprint-2/epica-8-anfitrion`. La tarea pasa a **Hecho** solo cuando la revisión de la otra persona del equipo la aprueba (DoD).

## Tareas

| Tarea | Responsable | Revisa | Estado |
|---|---|---|---|
| [x] T001 [P] [HU-043] Pruebas de HU-043 antes del código (AC-043.1, AC-043.2) | Yuly | Javier | Hecho |
| [x] T002 [HU-043] Rol ANFITRION, permiso SALON_OPERAR, menú, seguridad y rutas del gateway (REQ-WOK-043, NFR-WOK-003) | Yuly | Javier | Hecho |
| [x] T003 [P] [HU-040] Pruebas de HU-040 antes del código (AC-040.1, AC-040.2) | Javier | Yuly | Hecho |
| [x] T004 [HU-040] pedidos-service · lista de espera: registrar, consultar, editar y retirar (REQ-WOK-040) | Javier | Yuly | Hecho |
| [x] T005 [P] [HU-041] Pruebas de HU-041 antes del código (AC-041.1, AC-041.2) | Yuly | Javier | Hecho |
| [x] T006 [HU-041] pedidos-service · mapa del salón con estado, capacidad y totales (REQ-WOK-041) | Yuly | Javier | Hecho |
| [x] T007 [P] [HU-042] Pruebas de HU-042 antes del código (AC-042.1, AC-042.2, RN-07) | Javier | Yuly | Hecho |
| [x] T008 [HU-042] pedidos-service → usuarios-service · asignar mesa y mesero, «Mis mesas» y pedido QR al mesero asignado (REQ-WOK-042) | Javier | Yuly | Hecho |
| [x] T009 [P] [HU-044] Pruebas de HU-044 antes del código (AC-044.1, AC-044.2) | Javier | Yuly | Hecho |
| [x] T010 [HU-044] pedidos-service · POR_LIMPIAR al cobrar o liberar y «marcar limpia» (REQ-WOK-044) | Javier | Yuly | Hecho |
| [x] T011 [P] [HU-045] Pruebas de HU-045 antes del código (AC-045.1, AC-045.2) | Yuly | Javier | Hecho |
| [x] T012 [HU-045] pedidos-service · unión de mesas y separación al liberarse (REQ-WOK-045) | Yuly | Javier | Hecho |
| [x] T013 [P] [HU-046] Pruebas de HU-046 antes del código (AC-046.1, AC-046.2) | Javier | Yuly | Hecho |
| [x] T014 [HU-046] Capa de vista · mapa en vivo cada 3 s y rejilla para tablet (NFR-WOK-004, NFR-WOK-005) | Javier | Yuly | Hecho |
| [x] T015 Matriz de trazabilidad de la épica, docs/ de endpoints y seguridad, y demo | Yuly | Javier | Hecho |

[SUPUESTO] El responsable de cada T es el de su historia en la Pila de Producto (PBI-12 a PBI-18). T015 cierra el PBI-11, que está a cargo de Yuly.

## Evidencia de T001 a T014

| Tarea | Archivo de prueba | Qué comprueba |
|---|---|---|
| T001 | `pedidos-service/.../SalonWebTest` | AC-043.1: el anfitrión recibe 403 en `POST /api/mesas`, `PUT /api/mesas/m3` y `PATCH .../servicio`; en la web, `/mesas/nueva` y `/mesas/guardar` lo llevan a `/acceso-denegado` y no se crea ni se edita nada. AC-043.2: `/pedidos` y `/pedidos/nuevo` lo llevan a `/acceso-denegado`; la API de pedidos le responde 403. El mesero y el cajero no entran al salón. |
| T001 | `facturacion-service/.../CajaAnfitrionTest` | AC-043.2: `/caja/cuentas`, `/caja/comprobantes`, `/caja/cierres` y el cobro lo llevan a `/acceso-denegado`; `GET /api/cuentas/m3` y `POST /api/facturas` responden 403. |
| T001 | `usuarios-service/.../RolAnfitrionTest` | El rol ANFITRION solo tiene DASHBOARD_VER y SALON_OPERAR, se sincroniza al arrancar, se puede asignar a usuarios y su menú muestra «Salón y espera» y no Pedidos, Mesas, Caja ni Cocina. |
| T003 | `SalonServiceTest`, `SalonWebTest` | AC-040.1: «Familia Rojas, 4 personas» queda al final de la lista, con la hora de llegada y 0 min de espera; el grupo que llegó 20 min antes va primero con 20 min. AC-040.2: sin nombre o con 0 personas se rechaza indicando `nombre` o `personas`, en el servicio, en la API (`errores.nombre`, `errores.personas`) y en el formulario. También: editar, retirar (no se borra) y hora de llegada futura rechazada. |
| T005 | `SalonServiceTest`, `SalonWebTest` | AC-041.1: con 12 mesas (5 libres, 6 ocupadas, 1 por limpiar) el mapa trae las 12 con su estado y capacidad y los totales 5 / 6 / 1. AC-041.2: la mesa inactiva no aparece. |
| T007 | `SalonServiceTest`, `SalonWebTest`, `UsuariosApiSecurityTest` | AC-042.1: la mesa 3 pasa a OCUPADA con Carlos, el grupo sale de la lista y en `/mesas?mias=true` Carlos ve su mesa y no las de Ana. AC-042.2: grupo de 6 en mesa de 4 → `CAPACIDAD_INSUFICIENTE`, la mesa sigue libre y el grupo sigue en la lista; la API devuelve `codigo`. RN-07: el pedido QR de la mesa 3 queda con Carlos. El mesero debe estar activo; usuarios-service solo entrega usuario y nombre al token de servicio. |
| T009 | `SalonServiceTest`, `SalonWebTest` | AC-044.1: al cobrar la última cuenta la mesa pasa a POR_LIMPIAR, se borra la asignación y vence la sesión QR; si queda un pedido, sigue OCUPADA. AC-044.2: asignar una mesa por limpiar → `MESA_NO_DISPONIBLE` (también en la web); al marcarla limpia pasa a libre y se puede asignar. «El grupo se fue» deja la mesa por limpiar. |
| T011 | `SalonServiceTest` | AC-045.1: las mesas 4 y 5 de 4 puestos quedan unidas y OCUPADAS para el grupo de 8; al cobrar la última cuenta de la unión ambas pasan a por limpiar y se separan. AC-045.2: una libre y otra ocupada → `MESA_NO_DISPONIBLE` y la libre no se toca. ADR-003: si otra tablet ocupa una mesa de la unión a la vez, se deshace todo. |
| T013 | `SalonServiceTest`, `SalonWebTest` | AC-046.1: la huella cambia cuando la mesa 3 pasa a OCUPADA o llega un grupo, y no cambia sin cambios; la página consulta `/salon/estado` cada 3000 ms (menos de 5 s) y reemplaza el mapa con `/salon/vivo` sin recargar. AC-046.2: viewport de dispositivo, rejilla `auto-fill` de mínimo 9.5rem (4 columnas en 768 px), ningún ancho fijo mayor de 768 px y botones de 44 px. |

Resultado del 17/11/2026: `mvnw install` pasó con 213 pruebas (38 nuevas), 0 fallos y 4 omitidas (las mismas de Mongo, que requieren `WOK_TEST_MONGODB_URI`).

**T015:** la demo local con los 10 servicios y MongoDB Atlas se verificó el 17/11/2026 siguiendo el guion (dos ventanas para AC-046.1 y vista de 768 px para AC-046.2). Código en `main`, commit `38701d3`.

## Matriz de trazabilidad de la épica 8

| HU | Requisito | Criterios | Componentes | Pruebas |
|---|---|---|---|---|
| HU-040 | REQ-WOK-040 | AC-040.1, AC-040.2 | `GrupoEspera`, `SalonService.registrar/editar/retirar/espera`, `SalonApiRestController`, `SalonWebController`, `salon/mapa.html`, `salon/vivo.html`, `salon/espera-form.html` | `SalonServiceTest.ac040_*`, `hu040_*`; `SalonWebTest.ac040_2_*`, `hu040_*` |
| HU-041 | REQ-WOK-041 | AC-041.1, AC-041.2 | `SalonService.mapa`, `MapaSalon`, `MesaService.tablero`, `salon/vivo.html` | `SalonServiceTest.ac041_*`; `SalonWebTest.hu043_elAnfitrionVeElSalon…` |
| HU-042 | REQ-WOK-042 | AC-042.1, AC-042.2 | `SalonService.asignar`, `Mesa.Asignacion`, `UsuariosClient`, `GET /api/usuarios/internos/meseros`, `MesaWebController` (`mias`), `PedidoService.crearDesdeQr` y `porConfirmar(mesero)`, `salon/sentar.html` | `SalonServiceTest.ac042_*`, `hu042_*`, `rn07_*`; `SalonWebTest.ac042_*`; `UsuariosApiSecurityTest.elTokenDeServicioSoloVe…` |
| HU-043 | REQ-WOK-043, NFR-WOK-003 | AC-043.1, AC-043.2 | `Roles.ANFITRION`, `Permisos.SALON_OPERAR`, `Navegacion`, `SecurityConfig` (pedidos, usuarios), `GatewaySecurityConfig`, `api-gateway.yml` | `SalonWebTest.ac043_*`, `hu043_*`; `CajaAnfitrionTest`; `RolAnfitrionTest` |
| HU-044 | REQ-WOK-044 | AC-044.1, AC-044.2 | `Mesa.Estado.POR_LIMPIAR`, `MesaService.alCobrar/dejarPorLimpiar/liberar/marcarLimpia/actualizarOcupacion`, `PedidoService.marcarPagado`, `mesas/tablero.html` | `SalonServiceTest.ac044_*`, `hu044_*`; `SalonWebTest.ac044_2_*` |
| HU-045 | REQ-WOK-045 | AC-045.1, AC-045.2 | `SalonService.asignar` (varias mesas), `Mesa.Asignacion.unionId`, `MesaService.mesasDeLaUnion` | `SalonServiceTest.ac045_*`, `adr003_*` |
| HU-046 | NFR-WOK-004, NFR-WOK-005 | AC-046.1, AC-046.2 | `SalonService.huella`, `SalonWebController.estado/vivo`, `INTERVALO_MS`, `salon/mapa.html` | `SalonServiceTest.ac046_1_*`; `SalonWebTest.ac046_*` |

## Cierre de pendientes del Sprint 1

- **[SUPUESTO] de la HU-042:** cerrado. El pedido QR toma el mesero asignado a la mesa y la bandeja «Por confirmar» de cada mesero muestra los de sus mesas y los de mesas sin mesero.
- **Hallazgo CSRF:** sigue para el análisis estático de la PBI-29 (Sprint 4). Las rutas nuevas del salón usan los mismos formularios Thymeleaf con token CSRF.
