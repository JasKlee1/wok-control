# Lista de la DoD y decisión de release · WokControl 2.0.0 (PBI-32)

Revisa: **Yuly Marcela Gómez Rico** (revisora, SM + Developer). Decide: **Javier Steven Becerra Mantilla** (Product Owner). Fecha: 04/12/2026.

## Definition of Done v2, ítem por ítem

| # | Criterio de la DoD | Evidencia | Cumple |
|---|---|---|---|
| 1 | La spec pasó su puerta sin marcadores [NECESITA ACLARACIÓN] | `specs/007`, `008`, `009` y `010` con Puertas 1 a 3 (y la Puerta del delta de CR-001) | Sí |
| 2 | Cada criterio tiene al menos una prueba automatizada, incluidos los bordes | `docs/matriz-trazabilidad-v2.md`: las 22 HU de la v2 y los 6 ítems del Sprint 4 con prueba | Sí |
| 3 | Pruebas escritas antes del código y en verde | Tareas de pruebas antes de cada implementación en los `tasks.md`; `mvnw clean install`: 264 pruebas, 0 fallos | Sí |
| 4 | Llegó a `main` por pull request con T### y revisión de la otra persona | Ramas `sprint-1/…`, `sprint-2/…`, `sprint-3/…` unidas; `sprint-4/cierre-v2` se une en la Review | Sí, al unir la rama del Sprint 4 |
| 5 | Sin funcionalidad ni dependencias que nadie pidió (o con ADR) | Sin dependencias nuevas en el Sprint 4; SpotBugs se ejecuta como plugin externo; ADR-002 a ADR-006 | Sí |
| 6 | Todo cambio de estado de pedido, mesa o domicilio queda auditado | `DomicilioServiceTest.ac053_2_*`, auditoría de mesas (épica 8) y de sesiones QR (épica 7) | Sí |
| 7 | Sin hallazgos críticos o altos en el análisis estático y sin secretos | SpotBugs: 0 de prioridad alta; búsqueda de secretos sin hallazgos (`docs/seguridad.md`) | Sí |
| 8 | Datos personales según la Ley 1581 | `DomicilioServiceTest.pbi29_*`, `QrRutaClientTest.pbi29_*` | Sí |
| 9 | Documentación de endpoints actualizada | `docs/endpoints.md` (sin endpoints nuevos en el Sprint 4) | Sí |
| 10 | La matriz de trazabilidad incluye el ítem | `docs/matriz-trazabilidad-v2.md` y `specs/010-cierre-v2/tasks.md` | Sí |
| 11 | El uso de IA quedó en la bitácora | `docs/bitacora-ia.md`, entrada del 27/11/2026 | Sí |
| 12 | Desplegado en Render y demostrable en la Review | Épicas 7 a 9 desplegadas el 23/11/2026; la 2.0.0 se despliega al unir la rama | Sí, al unir la rama del Sprint 4 |

Condiciones para liberar:

| Condición | Estado |
|---|---|
| Regresión en verde (PBI-27) | Cumplida |
| NFR de rendimiento dentro del límite, sin IMP (PBI-28) | Cumplida |
| 0 hallazgos críticos o altos y 403 de los roles nuevos (PBI-29) | Cumplida |
| UAT aceptada por el administrador del restaurante (PBI-30) | Se firma en la Review (`docs/uat-v2.md`) |
| Plan de retroceso probado (PBI-31) | Cumplida |

## Lo que vuelve al Product Backlog

Nada del Product Goal v2 quedó sin terminar. Siguen en el backlog futuro, con nota, los ítems que el Sprint 0 dejó fuera del alcance:

| Ítem | Nota |
|---|---|
| Comandas por estación de bebidas | Lo rescatable del rol Barista, descartado en el Sprint 0 |
| Reservas en línea | El anfitrión trabaja sin reservas en la v2 |
| Domicilios pedidos en línea por el cliente | La v2 cubre el pedido telefónico registrado por el cajero |
| Rastreo GPS del domiciliario | El QR de la ruta cubre la navegación |
| App móvil nativa | La web adaptable cubre celular y tablet |
| Medición continua de rendimiento en Render | Hoy se mide con `scripts/medir-carta.ps1` en cada Review |

## Decisión de release (Product Owner)

> Con la Definition of Done cumplida, la regresión en verde, los NFR dentro del límite, la seguridad verificada y el plan de retroceso probado, **se aprueba liberar WokControl 2.0.0** en Render al unir `sprint-4/cierre-v2` a `main` y crear la etiqueta `v2.0.0`, **con la condición** de que el administrador del restaurante firme el acta de UAT en la Review del 04/12/2026. Si una observación de la UAT no se corrige ese día, se activa el plan de retroceso y el ítem vuelve al Product Backlog.
>
> Javier Steven Becerra Mantilla · Product Owner

| Rol | Nombre | Firma | Fecha |
|---|---|---|---|
| Revisora de la DoD | Yuly Marcela Gómez Rico | | |
| Product Owner | Javier Steven Becerra Mantilla | | |
