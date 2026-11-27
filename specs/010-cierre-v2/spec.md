# spec.md · Sprint 4 · Cierre y liberación de WokControl v2.0

| Campo | Valor |
|---|---|
| Versión | 1.0 (aprobada en la Puerta 1 del Sprint 4) |
| PBI | PBI-27 a PBI-32 (Calidad · 21 SP) |
| Redacta | Yuly Marcela Gómez Rico (SM + Developer) |
| Aprueba | Javier Steven Becerra Mantilla (PO + Developer) |
| Sprint | 4 · 30/11/2026 – 04/12/2026 |

## 1. Intención

**Sprint Goal:** al terminar el sprint, WokControl v2.0 está verificado (regresión, rendimiento y seguridad), aceptado por el administrador del restaurante y listo para liberarse con la decisión del Product Owner.

El Sprint 4 no agrega historias de usuario. Cierra la v2 con los seis ítems de calidad del Product Backlog y deja una sola versión, la **2.0.0**, que reúne las épicas 7 (cliente en el salón), 8 (anfitrión) y 9 (domicilios, CR-001) sobre la base de la v1.

## 2. Requisitos

| ID | PBI | Requisito | Criterios de aceptación |
|---|---|---|---|
| REQ-WOK-C01 | PBI-27 | Regresión de la v1 y la v2 en un solo comando y matriz de trazabilidad final | AC-C01.1: `mvnw clean install` corre todas las pruebas de los 12 módulos con 0 fallos. AC-C01.2 (borde): cada HU-001 a HU-053 aparece en la matriz con su criterio y su prueba o su forma de verificación. |
| REQ-WOK-C02 | PBI-28 | Rendimiento de los NFR del Product Goal | AC-C02.1: el p95 de la carta del cliente (40 platos, 50 cargas) es menor de 3 s. AC-C02.2: el peor caso de actualización del mapa de mesas (intervalo de sondeo + p95 de la consulta) es de 5 s o menos. AC-C02.3 (error): si un p95 supera el límite, se registra un IMP y la versión no se libera. |
| REQ-WOK-C03 | PBI-29 | Seguridad por rol, análisis estático y Ley 1581 | AC-C03.1: 0 hallazgos críticos o altos en el análisis estático y ningún secreto en el repositorio. AC-C03.2: los roles CLIENTE, ANFITRION y DOMICILIARIO reciben 403 (API) o acceso no permitido (web) en todo recurso ajeno. AC-C03.3 (borde): el nombre, el teléfono y la dirección del cliente del domicilio no aparecen en los registros, ni siquiera cuando falla una dependencia. AC-C03.4 (error, hallazgo heredado de la v1): un POST web con la cookie de sesión y sin token CSRF se rechaza. |
| REQ-WOK-C04 | PBI-30 | Pruebas de aceptación con el administrador del restaurante | AC-C04.1: los escenarios de las tres épicas se ejecutan con el administrador y quedan en el acta con su resultado. AC-C04.2 (borde): cada observación se corrige y el escenario se repite antes del cierre. |
| REQ-WOK-C05 | PBI-31 | Documentación v2, notas de versión y plan de retroceso | AC-C05.1: `docs/` y el README describen la v2.0. AC-C05.2 (error): sin un plan de retroceso probado no hay decisión de release. |
| REQ-WOK-C06 | PBI-32 | Lista de DoD, decisión de release y paquete final | AC-C06.1: la lista de DoD está revisada por la revisora y la decisión de release la escribe el PO. AC-C06.2 (borde): lo que no se termine vuelve al Product Backlog con una nota. |

## 3. Fuera de alcance

Funciones nuevas. Lo que quedó fuera del Product Goal v2 sigue en el backlog futuro: comandas por estación de bebidas, reservas en línea, domicilios pedidos en línea por el cliente, rastreo GPS y app móvil nativa.

## 4. Supuestos

- [SUPUESTO] El análisis estático se hace con SpotBugs 4.9.8 (umbral *High* para el criterio y *Medium* como informe), porque el proyecto no tiene SonarQube. Sigue la constitución 3: no se agrega como dependencia del proyecto, se ejecuta como plugin desde la línea de comandos.
- [SUPUESTO] Las mediciones automáticas de rendimiento se hacen en el servidor (MockMvc). La medición con red 4G simulada sobre Render se hace en la Review con `scripts/medir-carta.ps1`, que ya existía desde el Sprint 1.
