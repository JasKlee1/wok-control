# plan.md · Épica 8 · Anfitrión y gestión del salón

| Campo | Valor |
|---|---|
| Versión | 1.0 (borrador para la Puerta 2) |
| Spec | [spec.md](spec.md) v1.0 |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta 2) | Javier Steven Becerra Mantilla (PO) |

## 1. Contexto técnico

Es el mismo del Sprint 1 ([plan de la épica 7](../007-cliente-salon/plan.md)): Java 21 y Spring Boot 4 en microservicios, MongoDB Atlas con una base por servicio, Thymeleaf servido por cada servicio detrás del gateway y JWT con los permisos firmados.

Esta épica no agrega microservicios, bases de datos ni dependencias de Maven (constitución 3). La mesa, sus pedidos y la sesión de mesa ya viven en pedidos-service; la lista de espera y la asignación son parte del mismo dominio, el salón.

## 2. Dónde vive cada parte

| Parte | Servicio | Por qué |
|---|---|---|
| Rol ANFITRION y permiso SALON_OPERAR | wok-common (`Roles`, `Permisos`, `Navegacion`); usuarios-service los sincroniza al arrancar | `Roles.SISTEMA` es la fuente única de verdad, igual que el CLIENTE del Sprint 1. |
| Lista de espera | pedidos-service, colección nueva `lista_espera` en `wok_pedidos` | Sentar a un grupo cambia la mesa en la misma operación; tenerlos en la misma base evita una transacción entre servicios. |
| Mapa, asignación, unión y «por limpiar» | pedidos-service (`SalonService`, `MesaService`) | La ocupación de la mesa ya se calcula ahí con los pedidos activos y el cobro llega ahí desde facturacion-service. |
| Lista de meseros | usuarios-service (`GET /api/usuarios?rol=MESERO&activo=true`) con token de servicio | Los usuarios viven en usuarios-service. Se abre esa consulta al permiso SISTEMA; desde fuera el gateway no emite ese permiso. |
| Mapa en vivo | Vista de pedidos-service con consulta periódica | Igual que «Mi pedido» del Sprint 1 (plan 7, decisión 3.1.3): sin WebSockets ni infraestructura nueva. |

## 3. ADR-003 · Asignación de mesas, unión y estado POR_LIMPIAR

**Estado:** propuesta para la Puerta 2 del Sprint 2 (17/11/2026). Implementada en la rama `sprint-2/epica-8-anfitrion` con sus pruebas.

**Contexto.** En la v1 la ocupación se deduce de los pedidos: sin pedidos activos la mesa vuelve a DISPONIBLE. Con el anfitrión, una mesa se ocupa cuando se sienta el grupo, antes del primer pedido, y no debe quedar libre al cobrar (RN-08).

**Decisión.**

1. `Mesa.Estado` gana `POR_LIMPIAR`. DISPONIBLE se muestra como «Libre» (spec, acta 1).
2. La mesa guarda una `asignacion` embebida: grupo, personas, mesero, nombre del mesero, hora en que se sentó y, si es una unión, `unionId` y los números de las mesas unidas. Solo existe mientras el grupo está sentado.
3. Reglas de la ocupación (`MesaService.actualizarOcupacion`):
   - Con pedidos activos, una mesa DISPONIBLE o POR_LIMPIAR pasa a OCUPADA.
   - Sin pedidos activos, una mesa OCUPADA vuelve a DISPONIBLE solo si no tiene grupo sentado (comportamiento de la v1 cuando se cancela el único pedido). Con grupo sentado sigue OCUPADA.
4. Al cobrar (`MesaService.alCobrar`, llamado por `PedidoService.marcarPagado`), si la mesa y las de su unión ya no tienen pedidos activos, todas pasan a POR_LIMPIAR, se borra la asignación (la unión se separa) y vencen las sesiones QR. «Liberar» hace lo mismo con una mesa ocupada sin pedidos.
5. Marcar limpia solo acepta una mesa POR_LIMPIAR y la deja DISPONIBLE.
6. Asignar (`SalonService.asignar`) valida en este orden: grupo en espera (o grupo nuevo que llega y se sienta directo), mesas activas y DISPONIBLE (si no, `MESA_NO_DISPONIBLE`), suma de puestos (si no alcanza, `CAPACIDAD_INSUFICIENTE`) y mesero activo con rol MESERO. El código viaja en el `ProblemDetail` como propiedad `codigo` y al inicio del mensaje en la web.
7. Concurrencia: cada mesa se guarda con su `@Version`. Si dos tablets asignan la misma mesa a la vez, la segunda recibe `MESA_NO_DISPONIBLE`. En una unión, si falla una mesa se deshacen las que ya se habían ocupado.
8. Los pedidos QR toman el mesero de la asignación; la bandeja «Por confirmar» del mesero muestra los de sus mesas y los que no tienen mesero. Esto cierra el [SUPUESTO] del Sprint 1.

**Alternativas descartadas.**
- Un microservicio nuevo de salón: la asignación cambia la mesa, que ya vive en pedidos-service; se necesitaría una transacción distribuida.
- WebSockets o Server-Sent Events para el mapa: agregan infraestructura y el gateway reactivo tendría que enrutarlos. Consultar cada 3 s una huella de 16 caracteres cumple los 5 s del NFR-WOK-004.
- Una colección aparte para las uniones: la unión solo vive mientras el grupo está sentado; con `unionId` en cada mesa basta.

**Consecuencias.**
- Una mesa que tuvo un grupo necesita que alguien la marque limpia. Para los turnos sin anfitrión, el mesero también puede marcarla (spec, acta 4).
- Cada tablet hace una consulta liviana cada 3 s y solo pide el HTML del mapa cuando cambia la huella.

## 4. Datos

| Colección (wok_pedidos) | Campos nuevos |
|---|---|
| `mesas` | estado `POR_LIMPIAR`; `asignacion` {`grupoId`, `grupo`, `personas`, `mesero`, `meseroNombre`, `sentadaEn`, `unionId`, `unionNumeros`}; `porLimpiarDesde` |
| `lista_espera` (nueva) | `nombre`, `personas`, `llegadaEn`, `notas`, `estado` (EN_ESPERA, SENTADO, RETIRADO), `mesaIds`, `mesaNumeros`, `mesero`, `meseroNombre`, `sentadoEn`, `retiradoEn`, `motivoRetiro`, `registradoPor`; índice `estado_llegada` |

## 5. Contrato (rutas nuevas del Sprint 2)

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `GET /api/salon/mapa` | SALON_OPERAR | Mesas activas con estado, capacidad, grupo y mesero; totales y huella |
| `GET /api/salon/espera` | SALON_OPERAR | Grupos en espera por orden de llegada, con minutos de espera |
| `POST /api/salon/espera` | SALON_OPERAR | 201 con el grupo, o 400 con `errores.nombre` / `errores.personas` |
| `GET /api/salon/espera/{id}` · `PUT /api/salon/espera/{id}` | SALON_OPERAR | Consultar o editar un grupo en espera |
| `DELETE /api/salon/espera/{id}?motivo=` | SALON_OPERAR | 204; el grupo queda RETIRADO (no se borra, para la auditoría) |
| `POST /api/salon/asignaciones` | SALON_OPERAR | 201 con las mesas ocupadas, o 422 con `codigo` CAPACIDAD_INSUFICIENTE o MESA_NO_DISPONIBLE |
| `PATCH /api/salon/mesas/{id}/limpia` | SALON_OPERAR o PEDIDOS_GESTIONAR | La mesa pasa de POR_LIMPIAR a DISPONIBLE |
| `GET /api/salon/meseros` | SALON_OPERAR | Meseros activos para asignar |
| `GET /salon` | SALON_OPERAR | Mapa del salón y lista de espera |
| `GET /salon/estado` · `GET /salon/vivo` | SALON_OPERAR | Huella del salón y fragmento HTML del mapa y la lista (para la tablet) |
| `GET /salon/sentar` · `POST /salon/sentar` | SALON_OPERAR | Elegir mesas (una o varias) y mesero para un grupo |
| `POST /salon/espera`, `/salon/espera/{id}`, `/salon/espera/{id}/retirar` | SALON_OPERAR | Lista de espera desde la web |
| `POST /mesas/{id}/limpia` | SALON_OPERAR o PEDIDOS_GESTIONAR | Marcar limpia desde el mapa o desde Mesas |
| `GET /mesas?mias=true` | MESAS_VER | Mesas asignadas al mesero de la sesión |

El gateway enruta `/api/salon/**` y `/salon/**` a pedidos-service.

## 6. Auditoría

Eventos nuevos: `REGISTRAR_ESPERA`, `EDITAR_ESPERA`, `RETIRAR_ESPERA`, `SENTAR_GRUPO`, `UNIR_MESAS`, `MESA_POR_LIMPIAR` y `MARCAR_LIMPIA`.

## 7. Matriz requisito → componente

| Requisito | Componentes |
|---|---|
| REQ-WOK-040 | `GrupoEspera`, `GrupoEsperaRepository`, `SalonService.registrar`, `editar`, `retirar` y `espera`, `SalonApiRestController`, `SalonWebController`, `salon/mapa.html`, `salon/espera-form.html` |
| REQ-WOK-041 | `SalonService.mapa`, `MapaSalon`, `salon/vivo.html` |
| REQ-WOK-042 | `SalonService.asignar`, `Mesa.Asignacion`, `UsuariosClient.meseros`, `MesaWebController` (`?mias=true`), `PedidoService.crearDesdeQr` y `porConfirmar(mesero)` |
| REQ-WOK-043 · NFR-WOK-003 | `Roles.ANFITRION`, `Permisos.SALON_OPERAR`, `Navegacion`, `SecurityConfig` de pedidos-service y usuarios-service, gateway |
| REQ-WOK-044 | `Mesa.Estado.POR_LIMPIAR`, `MesaService.alCobrar`, `liberar`, `marcarLimpia` y `actualizarOcupacion` |
| REQ-WOK-045 | `SalonService.asignar` con varias mesas, `Mesa.Asignacion.unionId`, `MesaService.alCobrar` (separa la unión) |
| NFR-WOK-004 · NFR-WOK-005 | `SalonService.huella`, `GET /salon/estado`, `GET /salon/vivo`, `salon/mapa.html` (consulta cada 3 s y rejilla `auto-fill`) |
