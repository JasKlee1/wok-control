# plan.md · Sprint 4 · Cierre y liberación de WokControl v2.0

| Campo | Valor |
|---|---|
| Versión | 1.0 (aprobado en la Puerta 2 del Sprint 4) |
| Spec | [spec.md](spec.md) v1.0 |
| Redacta | Yuly Marcela Gómez Rico |
| Aprueba | Javier Steven Becerra Mantilla (PO) |

## 1. Contexto técnico

Sin cambios de arquitectura: Java 21, Spring Boot 4.0.8, Spring Cloud 2025.1.3 (Config Server, Eureka y Gateway), MongoDB Atlas con una base por servicio, Thymeleaf detrás del gateway y JWT firmado. Once microservicios más la librería `wok-common`, desplegados en Render.

## 2. Estrategia por PBI

| PBI | Cómo se cumple | Dónde queda la evidencia |
|---|---|---|
| PBI-27 | Un solo `mvnw clean install` ejecuta las pruebas de la v1 y la v2. La matriz junta las de las specs 007, 008 y 009 con las 31 HU de la v1. | `docs/matriz-trazabilidad-v2.md` |
| PBI-28 | `CartaLivianaTest` y `SalonWebTest.pbi28_*` miden 50 repeticiones, calculan el p95 y escriben el resultado en `target/evidencias-rendimiento/`. | `docs/rendimiento.md` |
| PBI-29 | Pruebas primero (CSRF, cabecera de correlación y registros sin datos personales); luego la corrección en `WokSecurity` y `CorrelationIdFilter`; SpotBugs y búsqueda de secretos en los archivos versionados. | `docs/seguridad.md` (sección v2.0) |
| PBI-30 | Guion de UAT con 15 escenarios (5 por épica), acta con resultado y firmas. | `docs/uat-v2.md` |
| PBI-31 | Notas de versión, README, plan de retroceso con la prueba de compilar la versión anterior. | `docs/notas-version-v2.0.md`, `docs/plan-retroceso.md` |
| PBI-32 | Lista de la DoD ítem por ítem, decisión del PO y versión 2.0.0 en los 13 `pom.xml`, el `Dockerfile` y los scripts. | `docs/dod-release-v2.0.md` |

## 3. ADR-006 · Exigir el token CSRF también con la cookie de sesión

**Estado:** aceptado el 30/11/2026 (Sprint 4, PBI-29).

**Contexto.** Desde la v1, el servidor de recursos OAuth2 de Spring Security le indica al filtro CSRF que ignore toda petición en la que el `BearerTokenResolver` encuentre un token. Ese comportamiento supone que el token viaja en la cabecera `Authorization`, que un sitio ajeno no puede poner. En WokControl, las pantallas web llevan el JWT en la cookie `WOK_TOKEN`, que el navegador envía solo, así que los POST con sesión no exigían el token CSRF. La cookie `SameSite=Lax` bajaba el riesgo, pero dependía de una sola defensa. El hallazgo se anotó en los Sprints 1, 2 y 3.

**Decisión.** En la cadena web de `WokSecurity` se aplica un `ObjectPostProcessor<CsrfFilter>` que vuelve a poner `CsrfFilter.DEFAULT_CSRF_MATCHER`: todo método distinto de GET, HEAD, TRACE y OPTIONS exige el token, con cookie de sesión o sin ella. La cadena `/api/**` no cambia: es sin estado, usa la cabecera `Authorization` y no lee cookies.

**Alternativas descartadas.**
- Pasar el JWT a la cabecera también en la web: obliga a reescribir los formularios Thymeleaf con JavaScript.
- Confiar solo en `SameSite=Lax`: no protege de un sitio en el mismo dominio registrable ni de navegadores antiguos.

**Consecuencias.** Todos los formularios ya usan `th:action`, que agrega el campo `_csrf`, y las llamadas `fetch` de las pantallas son GET. Las 257 pruebas anteriores pasaron sin cambios. Dos pruebas nuevas protegen la regla.

## 4. Riesgos

| Riesgo | Mitigación |
|---|---|
| La UAT encuentra observaciones al final | El plan de sprints dejó 9 SP libres para corregir lo que salga de la UAT. |
| Render gratuito duerme los servicios y la primera carga es lenta | La medición en Render se hace después de despertar los servicios con `scripts/despertar-render.py`. La primera carga no cuenta para el p95. |
| Un cambio de seguridad rompe un formulario | Regresión completa antes de unir a `main` y revisión de todas las plantillas (`th:action`). |
