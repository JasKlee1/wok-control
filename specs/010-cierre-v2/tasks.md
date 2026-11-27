# tasks.md · Sprint 4 · Cierre y liberación de WokControl v2.0

| Campo | Valor |
|---|---|
| Versión | 1.0 (aprobada en la Puerta 3 del Sprint 4) |
| Spec · Plan | [spec.md](spec.md) · [plan.md](plan.md) |
| Redacta | Yuly Marcela Gómez Rico |
| Aprueba (Puerta 3) | Javier Steven Becerra Mantilla (PO) |

Marcas del taller: `[P]` se puede hacer en paralelo; `[PBI-##]` ítem al que pertenece; entre paréntesis, los criterios (AC) que cubre. Por la constitución 2, las pruebas van antes del código. Cada línea es una tarjeta T### del tablero del Sprint 4. Rama: `sprint-4/cierre-v2`.

## Tareas

| Tarea | Responsable | Revisa | Estado |
|---|---|---|---|
| [x] T001 [PBI-27] Regresión completa de la v1 y la v2 con `mvnw clean install` (AC-C01.1) | Yuly | Javier | Hecho |
| [x] T002 [PBI-27] Matriz de trazabilidad final HU-001 a HU-053 (AC-C01.2) | Yuly | Javier | Hecho |
| [x] T003 [P] [PBI-28] Pruebas de rendimiento antes de medir: p95 de la carta y peor caso del mapa de mesas (AC-C02.1, AC-C02.2) | Javier | Yuly | Hecho |
| [x] T004 [PBI-28] Informe de rendimiento con la evidencia y el procedimiento 4G sobre Render (AC-C02.3) | Javier | Yuly | Hecho |
| [x] T005 [P] [PBI-29] Pruebas de seguridad antes del código: CSRF con cookie, cabecera de correlación y registros sin datos personales (AC-C03.3, AC-C03.4) | Javier | Yuly | Hecho |
| [x] T006 [PBI-29] ADR-006: CSRF exigido con la cookie de sesión; validación de `X-Correlation-Id` (AC-C03.4) | Javier | Yuly | Hecho |
| [x] T007 [PBI-29] Análisis estático con SpotBugs, búsqueda de secretos y matriz de 403 de los 3 roles nuevos (AC-C03.1, AC-C03.2) | Javier | Yuly | Hecho |
| [x] T008 [PBI-30] Guion y acta de UAT con 15 escenarios de las épicas 7, 8 y 9 (AC-C04.1, AC-C04.2) | Javier | Yuly | Hecho |
| [x] T009 [P] [PBI-31] Notas de versión v2.0, README y `docs/` actualizados (AC-C05.1) | Yuly | Javier | Hecho |
| [x] T010 [PBI-31] Plan de retroceso y prueba del retroceso a la versión del Sprint 3 (AC-C05.2) | Yuly | Javier | Hecho |
| [x] T011 [PBI-32] Lista de la DoD, decisión de release y versión 2.0.0 (AC-C06.1, AC-C06.2) | Yuly | Javier | Hecho |
| [x] T012 [PBI-32] Guion de la demo final, Review y Retrospectiva del Sprint 4 | Yuly | Javier | Hecho |

## Evidencia

| Tarea | Archivo | Qué comprueba |
|---|---|---|
| T001 | Salida de `mvnw clean install` (27/11/2026) | 12 módulos con BUILD SUCCESS. **264 pruebas, 0 fallos, 0 errores, 4 omitidas** (las de integración con Mongo, que requieren `WOK_TEST_MONGODB_URI`). wok-common 4 · usuarios 39 · menu 23 · inventario 17 · pedidos 105 · proveedores 12 · facturación 28 · reportes 4 · domicilios 32. |
| T002 | `docs/matriz-trazabilidad-v2.md` | 53 HU (31 de la v1 y 22 de la v2), cada una con criterio y prueba o verificación. |
| T003 | `CartaLivianaTest.ac039_1_*`, `SalonWebTest.pbi28_*` | 50 repeticiones cada una; el p95 se escribe en `pedidos-service/target/evidencias-rendimiento/`. |
| T004 | `docs/rendimiento.md` | Carta: p95 = 57 ms (límite 3000 ms). Mesas: peor caso = 3000 ms + 13 ms = 3013 ms (límite 5000 ms). Sin IMP. |
| T005 | `DomiciliosSecurityTest.pbi29_*`, `DomicilioServiceTest.pbi29_*`, `QrRutaClientTest.pbi29_*`, `CorrelationIdFilterTest` | Un POST con cookie y sin token CSRF (o con token falso) va a `/acceso-denegado` y no llega al servicio; con token, sí. Los registros del ciclo completo del domicilio, de la caída de pedidos-service y de la falla de goQR.me no contienen nombre, teléfono ni dirección. Una cabecera `X-Correlation-Id` con saltos de línea se reemplaza por un UUID. |
| T006 | `WokSecurity.exigirCsrfConCookie`, `CorrelationIdFilter.normalizar`, `JwtProperties` | Corrección del hallazgo CSRF de la v1 (ADR-006) y de los dos hallazgos del análisis estático. |
| T007 | `docs/seguridad.md` (sección «Verificación de la v2.0») | SpotBugs 4.9.8, esfuerzo máximo: **0 hallazgos de prioridad alta**; de prioridad media quedan 312 `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` (rango 18, el más bajo) aceptados con justificación. Ningún secreto en los archivos versionados. 403 de los 3 roles nuevos cubiertos por 9 clases de prueba. |
| T008 | `docs/uat-v2.md` | Guion de 15 escenarios con resultado de la ejecución previa y espacio de firma del administrador del restaurante para la Review. |
| T009 | `docs/notas-version-v2.0.md`, `README.md`, `docs/seguridad.md`, `docs/bitacora-ia.md` | La documentación describe la v2.0. |
| T010 | `docs/plan-retroceso.md` | Retroceso probado el 27/11/2026: el commit `d17ddfc` (Sprint 3) compila en un *worktree* aparte en 28 s y produce los 12 jar. |
| T011 | `docs/dod-release-v2.0.md` | 12 ítems de la DoD revisados y decisión de release del PO. |
| T012 | `E:\wok\Guion_Demo_Sprint4_04-12.md` | Demo de cierre y guion de la Review y la Retrospectiva. |

## Matriz de trazabilidad del Sprint 4

| PBI | Requisito | Criterios | Componentes | Pruebas o evidencia |
|---|---|---|---|---|
| PBI-27 | REQ-WOK-C01 | AC-C01.1, AC-C01.2 | Los 12 módulos | `mvnw clean install`; `docs/matriz-trazabilidad-v2.md` |
| PBI-28 | REQ-WOK-C02, NFR-WOK-001, NFR-WOK-004 | AC-C02.1, AC-C02.2, AC-C02.3 | `cliente/carta.html`, `SalonWebController.INTERVALO_MS` | `CartaLivianaTest.ac039_1_*`; `SalonWebTest.pbi28_*`; `docs/rendimiento.md` |
| PBI-29 | REQ-WOK-C03, NFR-WOK-003 | AC-C03.1 a AC-C03.4 | `WokSecurity`, `CorrelationIdFilter`, `JwtProperties`, `DomicilioService`, `QrRutaClient` | `DomiciliosSecurityTest.pbi29_*`; `DomicilioServiceTest.pbi29_*`; `QrRutaClientTest.pbi29_*`; `CorrelationIdFilterTest`; SpotBugs |
| PBI-30 | REQ-WOK-C04 | AC-C04.1, AC-C04.2 | Las 3 épicas | `docs/uat-v2.md` |
| PBI-31 | REQ-WOK-C05 | AC-C05.1, AC-C05.2 | `docs/`, README | `docs/notas-version-v2.0.md`; `docs/plan-retroceso.md` |
| PBI-32 | REQ-WOK-C06 | AC-C06.1, AC-C06.2 | 13 `pom.xml`, `Dockerfile`, scripts | `docs/dod-release-v2.0.md` |

## Lo que queda para la Review (04/12/2026)

- Firma del acta de UAT por el administrador del restaurante (`docs/uat-v2.md`).
- Medición 4G sobre Render con `scripts/medir-carta.ps1` después de desplegar la 2.0.0.
- Unir `sprint-4/cierre-v2` a `main` (despliegue automático en Render) y crear la etiqueta `v2.0.0`.
