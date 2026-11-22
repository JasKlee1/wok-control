# spec.md · Épica 9 · Domicilios (CR-001)

| Campo | Valor |
|---|---|
| Versión | 1.0 (delta-spec para la Puerta del delta) |
| Sprint | 3 · 23/11/2026 a 27/11/2026 |
| PBI habilitador | PBI-19 · 4 SP (cambio CR-001) |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta del delta) | Javier Steven Becerra Mantilla (PO) |
| Fuente | WokControl_v2_Sprint0_SDD.md, épica 9 (HU-047 a HU-053) y registro del cambio CR-001 (D05) |

Esta spec no nombra tecnología. Las decisiones técnicas están en [plan.md](plan.md).

## 1. Contexto y objetivo

El restaurante recibe pedidos por teléfono y WhatsApp y los anota en papel: el cocinero no sabe cuáles son para llevar, el domiciliario sale sin saber cuánto cobrar y el dinero de los domicilios no aparece en el cierre de caja. El Acta v1 excluía los domicilios («NO INCLUYE»). El cambio CR-001, aprobado en el Sprint 0, los incorpora como la épica 9.

**Historia épica.** Como cajero y domiciliario, quiero registrar, despachar y cobrar los pedidos a domicilio en el sistema, para que vender fuera del salón no dependa de papeles ni de llamadas perdidas.

**Sprint Goal.** Al terminar el sprint, el restaurante puede tomar un pedido a domicilio por teléfono, asignarlo a un domiciliario, seguir la entrega y cobrarla contra entrega con su factura.

## 2. Delta respecto al Acta v1 y a las épicas 1 a 8

| Tipo | Elemento | Cambio |
|---|---|---|
| ADDED | Domicilio | Pedido para entregar en la dirección del cliente, con su propio ciclo de estados (sección 6). |
| ADDED | Rol DOMICILIARIO | Ve y gestiona solo los domicilios que tiene asignados. |
| ADDED | Permisos DOMICILIOS_GESTIONAR y DOMICILIOS_REPARTIR | El primero lo tienen el CAJERO y el ADMINISTRADOR; el segundo, el DOMICILIARIO. |
| ADDED | Ruta del domicilio en código QR | El sistema muestra un código QR que abre la ruta hasta la dirección del cliente en un mapa, para el domiciliario y el rótulo de la bolsa. |
| MODIFIED | Pedido | Puede no tener mesa: un pedido de domicilio va directo a cocina y muestra «Domicilio D-…» en lugar de la mesa. El mesero no lo entrega ni lo cancela. |
| MODIFIED | Comprobante y cierre de caja | El comprobante de un domicilio incluye el valor del domicilio y entra al cierre de caja del cajero que registró el pedido. |
| MODIFIED | Tablero de cocina | Marca los pedidos de domicilio para que cocina los empaque. |
| REMOVED | No-objetivo «domicilios» del Acta v1 | Se retira de la lista NO INCLUYE. Siguen fuera del alcance los pedidos en línea por el cliente y el rastreo GPS. |

## 3. Actores

| Actor | Necesidad en esta épica |
|---|---|
| Cajero | Registrar el pedido que llega por teléfono, corregirlo o cancelarlo antes del despacho, asignarlo a un domiciliario libre y ver su cobro en el cierre de caja. |
| Domiciliario | Ver solo sus entregas con dirección, teléfono y valor a cobrar, abrir la ruta, marcar «en camino», cobrar y entregar, o registrar que no se entregó. |
| Cocinero | Preparar el pedido de domicilio igual que uno de mesa y marcarlo listo. |
| Administrador | Lo mismo que el cajero, además de los reportes. |
| Administrador de plataforma | Crear usuarios con el rol DOMICILIARIO. |

## 4. Alcance y no-objetivos

| En el alcance | Fuera del alcance |
|---|---|
| Pedido por teléfono registrado por el cajero | Pedido en línea hecho por el propio cliente |
| Asignación manual a un domiciliario disponible | Reparto automático o por zonas |
| Ruta hasta la dirección en un mapa (QR o enlace) | Rastreo GPS en tiempo real del domiciliario |
| Cobro contra entrega en efectivo o con datáfono | Pago en línea anticipado y propinas |
| Valor fijo del domicilio por pedido | Tarifas por distancia o por zona |

## 5. Glosario

| Término | Definición |
|---|---|
| Domicilio | Pedido para entregar en la dirección del cliente. Tiene número D-0001, cliente, teléfono, dirección, platos, valor del domicilio y estado. |
| Valor del domicilio | Lo que se cobra por llevar el pedido. Lo fija el cajero en cada pedido (por defecto $5.000). |
| Total a cobrar | Platos con su impuesto al consumo más el valor del domicilio. |
| Domiciliario disponible | Domiciliario activo que no está en camino con otra entrega. |
| Despacho | Momento en que el domiciliario sale del restaurante («en camino»). |
| Rótulo | Hoja para pegar en la bolsa con el número, el cliente, el valor a cobrar y el código QR de la ruta. |

## 6. Ciclo de estados

```
RECIBIDO → EN_PREPARACION → LISTO → ASIGNADO → EN_CAMINO → ENTREGADO
   │              │            │         │            └────→ NO_ENTREGADO
   └──────────────┴────────────┴─────────┴──→ CANCELADO (solo antes del despacho)
```

RECIBIDO, EN_PREPARACION y LISTO siguen el avance de cocina. ASIGNADO puede reasignarse a otro domiciliario mientras no salga.

## 7. Historias de usuario (Sprint 3 · 22 SP + PBI-19 · 4 SP = 26 SP)

| HU | Como | Quiero… para… | Técnica | MoSCoW | SP | Resp. | Revisa |
|---|---|---|---|---|---|---|---|
| HU-047 | Cajero | registrar, consultar, editar y cancelar pedidos a domicilio (cliente, teléfono, dirección, platos y valor del domicilio) antes del despacho, para vender fuera del salón sin papel | CRUD | Must | 5 | Javier | Yuly |
| HU-048 | Cajero | asignar un pedido listo a un domiciliario disponible, para que el pedido salga cuanto antes | Funcionales | Must | 2 | Yuly | Javier |
| HU-049 | Domiciliario | ver mis pedidos asignados con dirección y valor a cobrar, y marcarlos «en camino» y «entregado», para que el restaurante sepa dónde va cada pedido | Funcionales | Must | 3 | Javier | Yuly |
| HU-050 | Domiciliario | registrar el cobro contra entrega (efectivo o tarjeta) y que se genere la factura que el cajero ve en el cierre de caja, para que la caja cuadre con los domicilios | Funcionales | Must | 3 | Yuly | Javier |
| HU-051 | Administrador de plataforma | crear el rol DOMICILIARIO que solo vea los pedidos que tiene asignados, sin acceso a caja, inventario ni otros pedidos, para proteger los datos de los clientes | Roles | Must | 2 | Javier | Yuly |
| HU-052 | Domiciliario | marcar un pedido como «no entregado» con motivo cuando el cliente no está, para que el pedido vuelva, se anule sin factura y quede auditado | Escenarios | Should | 2 | Javier | Yuly |
| HU-053 | Responsable técnico | un servicio de domicilios con su propia base de datos, desplegado detrás de la puerta de entrada y con cada cambio de estado auditado, para mantener la regla de un servicio y una base por dominio | No funcionales | Must | 5 | Yuly | Javier |

## 8. Reglas de negocio

| ID | Regla | Origen |
|---|---|---|
| RN-11 | Todo domicilio tiene nombre del cliente, teléfono de 10 dígitos (se acepta el indicativo +57) y dirección. | HU-047 |
| RN-12 | Total a cobrar = platos + impuesto al consumo sobre los platos + valor del domicilio. El valor del domicilio no lleva impuesto. | HU-047, HU-050 |
| RN-13 | Solo se edita o cancela un domicilio antes del despacho (RECIBIDO, EN_PREPARACION, LISTO o ASIGNADO). Los platos no se editan porque ya están en cocina: se cancela y se registra de nuevo. | HU-047 |
| RN-14 | Solo se asigna un domicilio LISTO (o se reasigna uno ASIGNADO) a un domiciliario activo que no esté en camino. | HU-048 |
| RN-15 | El domiciliario solo ve y opera los domicilios asignados a él. No entra a caja, inventario, pedidos de mesa ni domicilios ajenos. | HU-049, HU-051 |
| RN-16 | El cobro debe cubrir el total. En efectivo se calcula el cambio; con tarjeta se cobra el valor exacto. El comprobante queda a nombre del cajero que registró el domicilio. | HU-050 |
| RN-17 | «No entregado» exige un motivo de al menos 10 caracteres; el pedido se anula sin factura y la comida no vuelve al inventario. | HU-052 |
| RN-18 | Cada cambio de estado queda en el historial del domicilio y en la auditoría central con usuario, fecha y hora UTC, estado anterior y nuevo, sin el teléfono ni la dirección del cliente (Ley 1581 de 2012). | HU-053 |

## 9. Requisitos funcionales (EARS)

| ID | Patrón | Requisito | HU |
|---|---|---|---|
| REQ-WOK-047 | Evento | Cuando el cajero registra un domicilio con cliente, teléfono válido, dirección, al menos un plato y valor del domicilio, el sistema deberá crearlo en RECIBIDO con el total de la RN-12 y enviar sus platos a cocina. Si falta la dirección o el teléfono no tiene 10 dígitos, deberá rechazarlo indicando el campo. El cajero deberá poder consultarlo, editar los datos del cliente y el valor del domicilio, y cancelarlo con motivo; si ya fue despachado, deberá rechazar la edición con YA_DESPACHADO. | HU-047 |
| REQ-WOK-048 | Evento | Cuando el cajero asigna un domicilio LISTO a un domiciliario disponible, el sistema deberá dejarlo ASIGNADO a ese domiciliario. Si el pedido sigue en cocina, deberá rechazarlo con PEDIDO_NO_LISTO; si el domiciliario está en camino, con DOMICILIARIO_NO_DISPONIBLE. | HU-048 |
| REQ-WOK-049 | Evento | Cuando el domiciliario abre sus entregas, el sistema deberá mostrarle solo los domicilios ASIGNADOS o EN_CAMINO a su nombre, con dirección, teléfono, valor a cobrar y la ruta. Deberá permitir ASIGNADO → EN_CAMINO → ENTREGADO y rechazar cualquier otro paso con TRANSICION_INVALIDA. | HU-049 |
| REQ-WOK-050 | Evento | Cuando el domiciliario registra el cobro de un domicilio EN_CAMINO, el sistema deberá emitir el comprobante con el valor del domicilio, calcular el cambio y dejar el domicilio ENTREGADO; el comprobante deberá aparecer en el cierre de caja del cajero que lo registró. Si el pago no cubre el total, deberá rechazarlo con PAGO_INSUFICIENTE. | HU-050 |
| REQ-WOK-051 | Ubicuo | El sistema deberá ofrecer el rol DOMICILIARIO con los permisos mínimos de la RN-15 y responder 403 (o la página de acceso no permitido) a todo lo demás. | HU-051 |
| REQ-WOK-052 | Evento | Cuando el domiciliario marca un domicilio EN_CAMINO como no entregado con un motivo válido, el sistema deberá dejarlo NO_ENTREGADO, anular su pedido sin factura y auditarlo. Si el motivo tiene menos de 10 caracteres, deberá rechazarlo con MOTIVO_OBLIGATORIO. | HU-052 |
| REQ-WOK-053 | Ubicuo | El sistema deberá guardar los domicilios en una base propia del servicio de domicilios, atender sus consultas a través de la puerta de entrada con token válido y registrar cada cambio de estado según la RN-18. | HU-053 |

## 10. Requisitos no funcionales (medibles)

| ID | Requisito | Cómo se mide | HU |
|---|---|---|---|
| NFR-WOK-006 | Sin la cadena de conexión a la base de datos, el servicio de domicilios no arranca y el registro lo explica sin mostrar el valor de ningún secreto. | Prueba automática de arranque y revisión del registro. | HU-053 |
| NFR-WOK-007 | Si el servicio externo que genera el código QR no responde en 5 s, la pantalla muestra el enlace de la ruta y el resto del flujo sigue funcionando. | Prueba automática con el servicio externo simulado (error y respuesta inválida). | HU-049 |
| NFR-WOK-003 | (Ampliado) El rol DOMICILIARIO recibirá 403 en todo recurso ajeno a su permiso. | Pruebas de acceso por rol. | HU-051 |

## 11. Criterios de aceptación (Given/When/Then)

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| AC-047.1 | Cliente «Ana Pérez», teléfono de 10 dígitos, dirección, platos y valor del domicilio | El cajero lo registra | Se crea el domicilio en RECIBIDO con total = platos (con su impuesto) + domicilio y el pedido va a cocina |
| AC-047.2 (error) | Un pedido sin dirección o con teléfono de menos de 10 dígitos | Se guarda | Se rechaza indicando el campo |
| AC-047.3 (borde) | Un pedido EN_CAMINO | El cajero intenta editarlo | Se rechaza con YA_DESPACHADO |
| AC-048.1 | Un pedido LISTO y el domiciliario Luis DISPONIBLE | El cajero lo asigna | Queda ASIGNADO a Luis |
| AC-048.2 (error) | Un pedido aún en preparación | Intenta asignarlo | Se rechaza con PEDIDO_NO_LISTO |
| AC-049.1 | Luis con 2 pedidos asignados | Abre su lista | Ve solo esos 2 con dirección, teléfono y valor a cobrar |
| AC-049.2 (error) | Un pedido ASIGNADO | Luis lo marca entregado sin pasar por en camino | Se rechaza con TRANSICION_INVALIDA |
| AC-050.1 | Un pedido EN_CAMINO por $45.000 | Luis registra un pago en efectivo de $50.000 | Se genera la factura, el cambio es $5.000 y el pedido queda ENTREGADO |
| AC-050.2 (error) | Un pedido por $45.000 | Se registra un pago de $40.000 | Se rechaza con PAGO_INSUFICIENTE |
| AC-051.1 (error) | Luis con rol DOMICILIARIO | Consulta un pedido asignado a Marta | Recibe 403 |
| AC-051.2 (error) | Un usuario DOMICILIARIO | Abre caja o inventario | Ve el mensaje de acceso no permitido |
| AC-052.1 | Un pedido EN_CAMINO | Luis lo marca NO_ENTREGADO con motivo «Cliente no contesta» | El pedido se anula sin factura y la anulación queda en la auditoría |
| AC-052.2 (error) | Un pedido EN_CAMINO | Lo marca NO_ENTREGADO con un motivo de 5 caracteres | Se rechaza con MOTIVO_OBLIGATORIO (mínimo 10) |
| AC-053.1 | El servicio desplegado | La puerta de entrada recibe la consulta de un domicilio con token válido | Responde con los datos de la base del servicio de domicilios |
| AC-053.2 | Un cambio de estado de un domicilio | Se consulta la auditoría | Hay usuario, fecha-hora UTC, estado anterior y nuevo |
| AC-053.3 (error) | Falta la cadena de conexión | El servicio arranca | Falla al iniciar y el registro lo indica sin mostrar secretos |

## 12. Decisiones de clarificación (acta)

Se resolvieron el 23/11/2026 leyendo el código del Sprint 2:

| # | Duda | Respuesta | Efecto en el Sprint 3 |
|---|---|---|---|
| 1 | En AC-047.1, ¿«platos» incluye el impuesto al consumo? | En el salón el cliente paga los platos con el impuesto al consumo del 8 %. | Total = platos + impuesto sobre los platos + domicilio (RN-12). El ejemplo de la demo: platos $37.500 + impuesto $3.000 + domicilio $4.500 = $45.000, que es el total de AC-050.1. |
| 2 | ¿Se pueden cambiar los platos de un domicilio antes del despacho? | Cuando se registra, el pedido ya está en cocina y descontó inventario. | Se editan cliente, teléfono, dirección, referencia, valor del domicilio y observaciones. Para cambiar platos se cancela y se registra de nuevo (RN-13). |
| 3 | ¿A nombre de quién sale el comprobante? | El domiciliario no tiene caja. | El comprobante queda a nombre del cajero que registró el domicilio y entra a su cierre; el comprobante guarda también quién cobró (RN-16). |
| 4 | ¿Qué es un domiciliario «disponible»? | No estaba definido. | Activo y sin entregas EN_CAMINO. Puede tener otros pedidos ASIGNADOS para salir con varios (RN-14). |
| 5 | ¿La comida de un «no entregado» vuelve al inventario? | Ya se preparó. | No. Si el domicilio se cancela antes de que cocina empiece, el inventario sí se restituye (RN-13, RN-17). |
| 6 | ¿Cómo encuentra el domiciliario la dirección? | Hoy pregunta por teléfono. | Se agrega un código QR con la ruta en un mapa (ADDED en la sección 2). Si el servicio de QR falla se muestra el enlace (NFR-WOK-007). |

No quedan marcadores [NECESITA ACLARACIÓN].

## 13. Lista de calidad (Puerta del delta) e historial

| Ítem de la Puerta del delta | Estado |
|---|---|
| El delta lista lo ADDED, MODIFIED y REMOVED, incluido el retiro del no-objetivo del Acta v1 | Cumple (sección 2) |
| Cada HU tiene requisito EARS y criterios con al menos un caso de error o de borde | Cumple |
| Los NFR son medibles | Cumple |
| La spec no nombra tecnología | Cumple |
| Ninguna tarjeta T del cambio se creó antes de aprobar el delta | Cumple (tasks.md nace el 23/11/2026) |

| Versión | Fecha | Cambio |
|---|---|---|
| 0.1 | 04/11/2026 | Intención, criterios y registro de CR-001 (Sprint 0) |
| 1.0 | 23/11/2026 | Delta-spec, RN-11 a RN-18, REQ y NFR de la épica 9 (PBI-19) |
