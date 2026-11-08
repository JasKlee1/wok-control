# spec.md · Épica 7 · Cliente en el salón

| Campo | Valor |
|---|---|
| Versión | 1.0 (borrador para la Puerta 1) |
| Sprint | 1 · 09/11/2026 a 17/11/2026 |
| PBI habilitador | PBI-02 · 4 SP |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta 1) | Javier Steven Becerra Mantilla (PO) |
| Fuente | WokControl_v2_Parcial_Segundo_Corte_SDD.pdf, secciones 7.1 a 7.11 |

Esta spec no nombra tecnología. Las decisiones técnicas están en [plan.md](plan.md).

## 1. Contexto y objetivo

Hoy el cliente sentado en una mesa depende del mesero para ver la carta, pedir y pedir la cuenta. La épica 7 cumple el objetivo específico 7 del proyecto: habilitar el autoservicio del cliente en el salón con una sesión de mesa por QR, y mantener la confirmación del mesero antes de que el pedido llegue a cocina.

**Historia épica.** Como cliente sentado en una mesa del restaurante, quiero ver la carta con fotos, hacer mi pedido y pedir la cuenta desde mi celular, para no tener que esperar al mesero en cada paso.

## 2. Actores

| Actor | Necesidad en esta épica |
|---|---|
| Cliente en el salón | Escanear el QR de su mesa, ver la carta con fotos y solo los platos disponibles, armar y enviar su pedido, seguir su estado y pedir la cuenta, sin usuario ni contraseña. |
| Mesero | Confirmar o rechazar los pedidos hechos por QR antes de que pasen a cocina. |
| Cocinero | Marcar platos agotados para que se avise al cliente. |
| Cajero | Ver la cuenta solicitada. Al cobrar, se cierra la sesión de mesa. |
| Administrador de plataforma | Crear el rol CLIENTE con su permiso mínimo. |

## 3. Alcance y no-objetivos

| En el alcance | Fuera del alcance |
|---|---|
| Carta y pedido por QR dentro del salón, con confirmación del mesero | Reservas en línea |
| Sesión de mesa sin usuario ni contraseña, limitada a su mesa | Registro de clientes con usuario y contraseña |
| Aviso de plato agotado y solicitud de la cuenta | Pagos en línea y aplicación móvil nativa |

## 4. Glosario

| Término | Definición |
|---|---|
| Sesión de mesa | Acceso temporal del cliente. Se abre al escanear el QR de una mesa, queda limitado a esa mesa y vence al cerrar la cuenta. |
| POR_CONFIRMAR | Estado del pedido hecho por QR mientras el mesero no lo confirma. En ese estado no llega a cocina. |
| Comanda | Pedido confirmado que cocina ve en su lista, ordenado por llegada. |

## 5. Historias de usuario (Sprint 1 · 23 SP)

| HU | Como | Quiero… para… | Técnica | MoSCoW | SP | Resp. | Revisa |
|---|---|---|---|---|---|---|---|
| HU-032 | Cliente | agregar, cambiar la cantidad o la observación y quitar platos de mi pedido antes de enviarlo, para que el pedido quede exactamente como lo quiero | CRUD | Must | 3 | Javier | Yuly |
| HU-033 | Cliente | escanear el QR de mi mesa y ver la carta con fotos, precios y solo los platos disponibles, para elegir sin esperar la carta impresa ni al mesero | Funcionales | Must | 3 | Yuly | Javier |
| HU-034 | Cliente | enviar mi pedido desde el celular y seguir su estado (por confirmar, en preparación, listo, entregado), para saber cuándo llega mi comida | Funcionales | Must | 5 | Javier | Yuly |
| HU-035 | Cliente | pedir la cuenta desde el celular, para que el cajero la prepare sin que tenga que llamar a nadie | Funcionales | Should | 2 | Yuly | Javier |
| HU-036 | Administrador de plataforma | crear el rol CLIENTE como una sesión de mesa sin usuario ni contraseña, que solo vea la carta y el pedido de su mesa y venza al cerrar la cuenta, para que nadie pueda ver ni cambiar otra información del sistema | Roles | Must | 3 | Yuly | Javier |
| HU-037 | Mesero | confirmar o rechazar los pedidos hechos por QR antes de que pasen a cocina, para evitar pedidos falsos o de broma | Escenarios | Must | 3 | Javier | Yuly |
| HU-038 | Cliente | recibir un aviso cuando un plato se agota mientras armo mi pedido, para cambiarlo antes de enviarlo | Escenarios | Should | 2 | Yuly | Javier |
| HU-039 | Cliente | que la carta y mi pedido carguen en menos de 3 segundos en un celular con datos móviles y se vean bien en pantallas pequeñas, para no desistir de pedir | No funcionales | Should | 2 | Javier | Yuly |

## 6. Reglas de negocio

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Un pedido hecho por QR no pasa a cocina sin la confirmación del mesero. El rechazo exige motivo. | Decisión del PO · HU-037 |
| RN-02 | Un pedido ya enviado a cocina solo lo cambia el mesero. | HU-032 |
| RN-03 | La sesión de mesa no usa usuario ni contraseña, solo ve su mesa y vence al cerrar la cuenta. | Decisión del PO · HU-036 |
| RN-04 | No se envían pedidos vacíos ni con platos agotados. | HU-034, HU-038 |

## 7. Requisitos funcionales (EARS)

| ID | Patrón | Requisito | HU |
|---|---|---|---|
| REQ-WOK-032 | Estado | Mientras un pedido de mesa no se haya enviado, el sistema deberá permitir al cliente agregar platos, cambiar su cantidad u observación y quitarlos, y recalcular el total. Si el pedido ya fue enviado a cocina, deberá impedir el cambio y remitir al mesero. | HU-032 |
| REQ-WOK-033 | Evento | Cuando un cliente escanea el QR de una mesa activa, el sistema deberá abrir una sesión de mesa y mostrar la carta con foto, precio y categoría de los platos disponibles. Si la mesa está inactiva o no existe, no deberá abrir sesión. | HU-033 |
| REQ-WOK-034 | Evento | Cuando el cliente envía un pedido con al menos un plato, el sistema deberá crearlo en estado POR_CONFIRMAR, mostrarlo al mesero de la mesa y mostrar al cliente el estado de cada plato (por confirmar, en preparación, listo, entregado). | HU-034 |
| REQ-WOK-035 | Evento | Cuando el cliente pide la cuenta, el sistema deberá mostrar la mesa como «Cuenta solicitada» al cajero con el total y avisar si hay platos sin entregar. | HU-035 |
| REQ-WOK-036 | Ubicuo | El sistema deberá limitar la sesión de mesa (rol CLIENTE, sin usuario ni contraseña) a la carta y al pedido de su propia mesa, y deberá vencerla cuando el cajero cierre la cuenta. | HU-036 |
| REQ-WOK-037 | Evento | Cuando el mesero confirma un pedido POR_CONFIRMAR, el sistema deberá enviarlo a cocina como comanda. Cuando lo rechaza, deberá exigir un motivo y mostrarlo al cliente. | HU-037 |
| REQ-WOK-038 | No deseado | Si un plato se agota mientras está en un pedido sin enviar, entonces el sistema deberá avisar al cliente, marcar el plato como no disponible e impedir el envío hasta que se quite. | HU-038 |

## 8. Requisitos no funcionales (medibles)

| ID | Requisito | Cómo se mide | HU |
|---|---|---|---|
| NFR-WOK-001 | La carta y el pedido del cliente cargarán en menos de 3 s (p95) en un celular con red 4G simulada y se verán sin desplazamiento horizontal en 360 px de ancho. | 50 cargas de la carta de 40 platos y revisión en 360 px (PBI-28) | HU-039 |
| NFR-WOK-003 | Cada rol accederá solo a lo que le corresponde: el rol CLIENTE recibirá 403 en todo recurso ajeno, con 0 hallazgos críticos o altos en el análisis estático. | Pruebas de acceso por rol y análisis estático (PBI-29) | HU-036 |

## 9. Criterios de aceptación (Given/When/Then)

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| AC-032.1 | Un pedido sin enviar de la mesa 5 con 2 «Arroz chino especial» | El cliente cambia la cantidad a 3 y agrega «sin cebolla» | El pedido muestra 3 unidades con la observación y el total se recalcula |
| AC-032.2 (error) | Un pedido ya enviado a cocina | El cliente intenta quitar un plato | Se impide con el mensaje «El pedido ya fue enviado; pide el cambio al mesero» |
| AC-033.1 | El QR de la mesa 5 y una carta de 40 platos con 2 agotados | El cliente escanea el QR | Ve 38 platos con foto, precio y categoría |
| AC-033.2 (error) | El QR de una mesa inactiva o inexistente | Se escanea | Se muestra «Mesa no disponible, pide ayuda al personal» y no se abre sesión |
| AC-034.1 | Un pedido de la mesa 5 con 3 platos | El cliente lo envía | Queda en estado POR_CONFIRMAR y el mesero de la mesa lo ve en su lista |
| AC-034.2 | Un pedido confirmado | Cocina cambia un plato a LISTO | El cliente ve el nuevo estado de ese plato en su celular |
| AC-034.3 (borde) | Un pedido sin platos | El cliente pulsa Enviar | Se rechaza con «Agrega al menos un plato» |
| AC-035.1 | Un pedido de la mesa 5 con todos sus platos entregados | El cliente pide la cuenta | La mesa aparece como «Cuenta solicitada» en la pantalla del cajero con el total |
| AC-035.2 (borde) | Un pedido con platos aún en preparación | El cliente pide la cuenta | Se registra la solicitud y el cajero ve el aviso «Hay platos sin entregar» |
| AC-036.1 (error) | Un cliente con sesión de la mesa 5 | Consulta el pedido de la mesa 7 cambiando el identificador | Recibe 403 y no ve datos de otra mesa |
| AC-036.2 (borde) | El cajero cerró la cuenta de la mesa 5 | El cliente intenta agregar un plato con la sesión anterior | La sesión está vencida y debe escanear el QR de nuevo |
| AC-037.1 | Un pedido POR_CONFIRMAR de la mesa 5 | El mesero lo confirma | Pasa a cocina como comanda |
| AC-037.2 (error) | Un pedido POR_CONFIRMAR | El mesero lo rechaza sin motivo | Se exige un motivo; con motivo, el cliente ve «Pedido rechazado: <motivo>» |
| AC-038.1 | Un pedido sin enviar con «Pato laqueado» | El cocinero lo marca agotado | El cliente ve el aviso y el plato queda marcado como no disponible |
| AC-038.2 (error) | Un pedido con un plato agotado | El cliente intenta enviarlo | Se rechaza con «Quita los platos agotados antes de enviar» |
| AC-039.1 | Un celular con perfil de red 4G simulado | Abre la carta de 40 platos 50 veces | El p95 de carga es menor de 3 s |
| AC-039.2 (borde) | Una pantalla de 360 px de ancho | Se muestra la carta | No hay desplazamiento horizontal y los botones se pueden tocar |

## 10. Decisiones de clarificación (acta)

En el Sprint 0 quedaron tres dudas sobre la v1. Se resolvieron leyendo el código de la v1 el 08/11/2026:

| # | Duda | Respuesta en la v1 | Efecto en el Sprint 1 |
|---|---|---|---|
| 1 | ¿Los platos tienen foto? | Sí. Cada plato guarda su imagen en el almacén de archivos del servicio de menú. | HU-033 no necesita cargar fotos nuevas. |
| 2 | ¿El pedido tiene estado por plato? | Sí. Cada ítem tiene PENDIENTE, EN_PREPARACION, LISTO y ENTREGADO. | HU-034 reutiliza esos estados. Solo falta POR_CONFIRMAR en el pedido. |
| 3 | ¿La mesa tiene un identificador estable para el QR? | Tiene identificador y número único. | Se agrega un código QR aleatorio por mesa para que el número no se pueda adivinar (ver plan.md, ADR-002). |

No quedan marcadores [NECESITA ACLARACIÓN].

## 11. Lista de calidad (Puerta 1) e historial

| Ítem de la Puerta 1 | Estado |
|---|---|
| Cada HU tiene requisito EARS y criterios con al menos un caso de error o de borde | Cumple |
| Los NFR son medibles | Cumple |
| La spec no nombra tecnología | Cumple |
| Sin marcadores [NECESITA ACLARACIÓN] | Cumple |

| Versión | Fecha | Cambio |
|---|---|---|
| 0.1 | 04/11/2026 | Intención y criterios (Sprint 0) |
| 1.0 | 09/11/2026 | REQ y NFR de la épica 7 para la Puerta 1 (PBI-02) |
