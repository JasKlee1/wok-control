# spec.md · Épica 8 · Anfitrión y gestión del salón

| Campo | Valor |
|---|---|
| Versión | 1.0 (borrador para la Puerta 1) |
| Sprint | 2 · 17/11/2026 a 20/11/2026 |
| PBI habilitador | PBI-11 · 3 SP |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta 1) | Javier Steven Becerra Mantilla (PO) |
| Fuente | WokControl_v2_Sprint0_SDD.md, épica 8 (HU-040 a HU-046) |

Esta spec no nombra tecnología. Las decisiones técnicas están en [plan.md](plan.md).

## 1. Contexto y objetivo

Hoy nadie recibe a los grupos en la puerta con información del salón: el mesero ocupa una mesa cuando registra el primer pedido, la mesa queda libre en cuanto se cobra aunque esté sucia, y cuando el salón está lleno los grupos se anotan en papel. La épica 8 da al restaurante un anfitrión que ve el estado real de cada mesa, maneja la lista de espera, sienta a cada grupo en una mesa libre y limpia con su mesero, y une mesas para los grupos grandes.

**Historia épica.** Como anfitrión, quiero ver el estado de todas las mesas y manejar la lista de espera, para que cada grupo que llega se siente rápido en una mesa adecuada y limpia.

**Sprint Goal.** Al terminar el sprint, el anfitrión puede recibir a cada grupo, anotarlo en la lista de espera si no hay mesa y sentarlo en una mesa libre y limpia con su mesero.

## 2. Actores

| Actor | Necesidad en esta épica |
|---|---|
| Anfitrión | Ver el mapa del salón, manejar la lista de espera, sentar grupos (uniendo mesas si hace falta) y marcar las mesas limpias. |
| Mesero | Ver las mesas que le asignaron y recibir los pedidos QR de esas mesas. |
| Cajero | Al cobrar la última cuenta de la mesa, la mesa pasa a «por limpiar». |
| Administrador | Sigue siendo el único que crea, edita, inactiva o pone fuera de servicio las mesas. |
| Administrador de plataforma | Crear el rol ANFITRION con su permiso mínimo y asignarlo a usuarios. |

## 3. Alcance y no-objetivos

| En el alcance | Fuera del alcance |
|---|---|
| Lista de espera de los grupos que llegan a la puerta | Reservas en línea o por teléfono con hora futura |
| Mapa del salón con libre, ocupada y por limpiar | Plano gráfico con la posición física de las mesas |
| Asignar mesa y mesero; unir mesas para grupos grandes | Reparto automático de mesas o de meseros |
| Mesa «por limpiar» después del cobro | Turnos o nómina de los meseros |

**Borde explícito (PBI-11):** crear, editar, inactivar y poner fuera de servicio una mesa sigue siendo del ADMINISTRADOR. El anfitrión solo cambia su ocupación.

## 4. Glosario

| Término | Definición |
|---|---|
| Grupo | Personas que llegan juntas. Tiene nombre, número de personas y hora de llegada. |
| Lista de espera | Grupos que esperan mesa, en orden de llegada. |
| Mesa libre | Activa, en servicio, sin grupo y limpia. Es la única que se puede asignar. |
| Mesa ocupada | Tiene un grupo sentado o pedidos activos. |
| Mesa por limpiar | Su grupo se fue (se cobró o se liberó) y todavía no está limpia. |
| Unión de mesas | Dos o más mesas libres que atienden a un mismo grupo. Se separan cuando se liberan. |

## 5. Historias de usuario (Sprint 2 · 18 SP + PBI-11 · 3 SP = 21 SP)

| HU | Como | Quiero… para… | Técnica | MoSCoW | SP | Resp. | Revisa |
|---|---|---|---|---|---|---|---|
| HU-040 | Anfitrión | registrar, consultar, editar y retirar grupos de la lista de espera (nombre, número de personas y hora de llegada), para atenderlos en orden de llegada | CRUD | Must | 3 | Javier | Yuly |
| HU-041 | Anfitrión | ver el mapa del salón con el estado de cada mesa (libre, ocupada o por limpiar) y su capacidad, para saber dónde sentar a cada grupo | Funcionales | Must | 3 | Yuly | Javier |
| HU-042 | Anfitrión | asignar a un grupo una mesa libre con capacidad suficiente y un mesero, para que el mesero sepa qué mesa debe atender | Funcionales | Must | 3 | Javier | Yuly |
| HU-043 | Administrador de plataforma | crear el rol ANFITRION que vea las mesas y la lista de espera y cambie su ocupación, sin crear ni editar mesas, tomar pedidos ni cobrar | Roles | Must | 2 | Yuly | Javier |
| HU-044 | Anfitrión | que la mesa pase a «por limpiar» cuando el cajero cobra la cuenta y marcarla libre cuando esté limpia | Escenarios | Must | 2 | Javier | Yuly |
| HU-045 | Anfitrión | unir mesas libres para un grupo grande y que se separen al liberarse | Escenarios | Should | 3 | Yuly | Javier |
| HU-046 | Anfitrión | que el estado de las mesas se actualice en 5 segundos o menos sin recargar y se pueda usar en una tablet | No funcionales | Should | 2 | Javier | Yuly |

## 6. Reglas de negocio

| ID | Regla | Origen |
|---|---|---|
| RN-05 | Solo se asigna una mesa libre: activa, en servicio, sin grupo y limpia. | HU-042, HU-044 |
| RN-06 | Las mesas asignadas deben sumar al menos tantos puestos como personas tiene el grupo. | HU-042, HU-045 |
| RN-07 | Toda asignación lleva un mesero activo. Los pedidos QR de esa mesa le llegan a ese mesero. | HU-042 · cierra el [SUPUESTO] del Sprint 1 |
| RN-08 | Una mesa que tuvo un grupo no vuelve a libre sola: pasa a por limpiar y alguien la marca limpia. | HU-044 |
| RN-09 | Crear, editar, inactivar y poner fuera de servicio las mesas es solo del ADMINISTRADOR. | HU-043 |
| RN-10 | La lista de espera se atiende en orden de llegada; un grupo retirado o sentado sale de la lista. | HU-040 |

## 7. Requisitos funcionales (EARS)

| ID | Patrón | Requisito | HU |
|---|---|---|---|
| REQ-WOK-040 | Evento | Cuando el anfitrión registra un grupo con nombre y al menos una persona, el sistema deberá agregarlo al final de la lista de espera con su hora de llegada y mostrar cuánto lleva esperando. Si falta el nombre o el número de personas es menor que 1, deberá rechazarlo indicando el campo. El anfitrión deberá poder consultar, editar y retirar los grupos que esperan. | HU-040 |
| REQ-WOK-041 | Ubicuo | El sistema deberá mostrar al anfitrión cada mesa activa con su estado (libre, ocupada o por limpiar) y su capacidad, y los totales por estado. Las mesas inactivas no deberán aparecer. | HU-041 |
| REQ-WOK-042 | Evento | Cuando el anfitrión asigna a un grupo una mesa libre con capacidad suficiente y un mesero, el sistema deberá marcar la mesa como ocupada, sacar al grupo de la lista y mostrar la mesa entre las del mesero. Si la capacidad no alcanza, deberá rechazarlo con CAPACIDAD_INSUFICIENTE. | HU-042 |
| REQ-WOK-043 | Ubicuo | El sistema deberá permitir al rol ANFITRION ver el salón y la lista de espera y cambiar la ocupación de las mesas, y deberá negarle crear o editar mesas (403), tomar pedidos y cobrar (acceso no permitido). | HU-043 |
| REQ-WOK-044 | Evento | Cuando el cajero cobra la última cuenta de una mesa, el sistema deberá pasarla a por limpiar. Mientras esté por limpiar, deberá rechazar su asignación con MESA_NO_DISPONIBLE; cuando se marque limpia, deberá pasarla a libre. | HU-044 |
| REQ-WOK-045 | Evento | Cuando el anfitrión asigna varias mesas libres a un grupo, el sistema deberá ocuparlas como una unión con la suma de sus puestos y separarlas cuando la unión se libere. Si alguna no está libre, deberá rechazar la unión con MESA_NO_DISPONIBLE. | HU-045 |

## 8. Requisitos no funcionales (medibles)

| ID | Requisito | Cómo se mide | HU |
|---|---|---|---|
| NFR-WOK-004 | Un cambio de estado de una mesa se verá en los demás mapas abiertos en 5 s o menos, sin recargar la página. | Dos navegadores con el mapa abierto: se cambia una mesa en uno y se cronometra en el otro (demo). Prueba automática: el mapa consulta cada 3 s y la huella cambia con el estado. | HU-046 |
| NFR-WOK-005 | El mapa de 12 mesas se verá completo en una tablet de 768 px sin desplazamiento horizontal, con botones de al menos 44 px. | Revisión a 768 px y prueba automática de la hoja de estilos del mapa. | HU-046 |
| NFR-WOK-003 | (Ampliado) El rol ANFITRION recibirá 403 en todo recurso ajeno a su permiso. | Pruebas de acceso por rol. | HU-043 |

## 9. Criterios de aceptación (Given/When/Then)

| ID | Dado | Cuando | Entonces |
|---|---|---|---|
| AC-040.1 | El salón lleno | El anfitrión registra «Familia Rojas, 4 personas» a las 19:05 | Aparece al final de la lista con la hora y el tiempo de espera |
| AC-040.2 (error) | Un registro sin nombre o con 0 personas | Se guarda | Se rechaza indicando el campo que falta |
| AC-041.1 | Un salón con 12 mesas: 5 libres, 6 ocupadas y 1 por limpiar | El anfitrión abre el mapa | Ve cada mesa con su estado y capacidad y los totales 5 / 6 / 1 |
| AC-041.2 (borde) | Una mesa inactivada por el administrador | Se abre el mapa | Esa mesa no aparece |
| AC-042.1 | Un grupo de 4 y la mesa 3 libre para 4 | El anfitrión la asigna con el mesero Carlos | La mesa pasa a OCUPADA, el grupo sale de la lista y Carlos la ve en sus mesas |
| AC-042.2 (error) | Un grupo de 6 y la mesa 3 para 4 | Intenta asignarla | Se rechaza con CAPACIDAD_INSUFICIENTE |
| AC-043.1 (error) | Un usuario con rol ANFITRION | Intenta crear o editar una mesa | Recibe 403 (crear y editar mesas es del ADMINISTRADOR) |
| AC-043.2 (error) | Un usuario con rol ANFITRION | Abre pedidos o caja | Ve el mensaje de acceso no permitido (HU-009) |
| AC-044.1 | La mesa 3 OCUPADA | El cajero cobra su cuenta | La mesa pasa a POR_LIMPIAR |
| AC-044.2 (error) | La mesa 3 POR_LIMPIAR | El anfitrión intenta asignarla | Se rechaza con MESA_NO_DISPONIBLE; al marcarla limpia pasa a LIBRE |
| AC-045.1 | Un grupo de 8 y las mesas 4 y 5 libres de 4 puestos | El anfitrión las une y las asigna | Queda un grupo de mesas de 8 puestos OCUPADO |
| AC-045.2 (error) | Una mesa libre y otra ocupada | Intenta unirlas | Se rechaza con MESA_NO_DISPONIBLE |
| AC-046.1 | Dos tablets con el mapa abierto | Una cambia la mesa 3 a OCUPADA | La otra lo muestra en 5 s o menos sin recargar |
| AC-046.2 (borde) | Una tablet de 768 px de ancho | Se abre el mapa de 12 mesas | Se ve completo sin desplazamiento horizontal |

## 10. Decisiones de clarificación (acta)

Se resolvieron leyendo el código de la v1 y del Sprint 1 el 17/11/2026:

| # | Duda | Respuesta | Efecto en el Sprint 2 |
|---|---|---|---|
| 1 | ¿La mesa ya tiene los estados libre, ocupada y por limpiar? | Tiene DISPONIBLE, OCUPADA y FUERA_DE_SERVICIO. La ocupación se deduce de los pedidos activos. | Se agrega POR_LIMPIAR. DISPONIBLE se muestra como «Libre»: es el LIBRE de los criterios y no se renombra para no migrar datos. |
| 2 | ¿Dónde se guarda el mesero de la mesa? | En ningún lado: solo el pedido guarda quién lo registró (el [SUPUESTO] del Sprint 1). | La mesa guarda la asignación del grupo con su mesero. El pedido QR toma ese mesero y la bandeja «Por confirmar» del mesero muestra sus mesas y las que no tienen mesero. |
| 3 | ¿Qué pasa si el mesero libera una mesa a mano? | Hoy queda disponible. | Si estaba ocupada pasa a por limpiar, igual que al cobrar (RN-08). |
| 4 | ¿Quién marca limpia la mesa cuando no hay anfitrión en el turno? | No estaba definido. | El anfitrión, el administrador y el mesero pueden marcarla limpia. Solo el anfitrión y el administrador sientan grupos. Lo aprueba el PO en la Puerta 1. |
| 5 | ¿Un grupo que llega y encuentra mesa pasa por la lista? | No estaba definido. | Se puede sentar directamente: queda registrado como grupo sentado con su hora de llegada, para la auditoría. |

No quedan marcadores [NECESITA ACLARACIÓN].

## 11. Lista de calidad (Puerta 1) e historial

| Ítem de la Puerta 1 | Estado |
|---|---|
| Cada HU tiene requisito EARS y criterios con al menos un caso de error o de borde | Cumple |
| Los NFR son medibles | Cumple |
| La spec no nombra tecnología | Cumple |
| Sin marcadores [NECESITA ACLARACIÓN] | Cumple |
| La spec deja explícito que crear y editar mesas sigue siendo del ADMINISTRADOR (borde del PBI-11) | Cumple (sección 3 y RN-09) |

| Versión | Fecha | Cambio |
|---|---|---|
| 0.1 | 04/11/2026 | Intención y criterios (Sprint 0) |
| 1.0 | 17/11/2026 | REQ y NFR de la épica 8 para la Puerta 1 (PBI-11) |
