# Plan de retroceso · WokControl 2.0.0 (PBI-31)

## Cuándo se activa

Lo decide el Product Owner, con la Scrum Master, si después de desplegar la 2.0.0 en Render ocurre cualquiera de estos casos y no se corrige en 2 horas:

1. Un flujo de la v1 deja de funcionar (login, pedido de mesa, cocina, cobro o cierre de caja).
2. El p95 de la carta pasa de 3 s o el mapa de mesas tarda más de 5 s (`scripts/medir-carta.ps1`).
3. Aparece en los registros un teléfono o una dirección de un cliente (Ley 1581).
4. Un rol ve o cambia datos que no le corresponden.

## Versión de retorno

Commit `d17ddfc` de `main` (cierre del Sprint 3, desplegado el 23/11/2026). Tiene las épicas 7, 8 y 9; solo le faltan las correcciones del Sprint 4.

## Pasos

| # | Paso | Quién | Tiempo |
|---|---|---|---|
| 1 | Anunciar el retroceso al equipo y al administrador del restaurante | SM | 5 min |
| 2 | **Render:** en cada servicio afectado, *Events* → el despliegue del 23/11/2026 → *Rollback*. Es lo más rápido porque no recompila. | PO | 10 min |
| 3 | **Si hay que rehacer `main`:** `git revert -m 1 <commit de la unión del Sprint 4>` y `git push origin main`. Render recompila y despliega desde `main` (auto-sync del Blueprint). No se usa `git push --force`. | PO | 15 min + compilación |
| 4 | Despertar los servicios y verificar `https://wokcontrol-s1-gateway-lu10.onrender.com/actuator/health` | SM | 5 min |
| 5 | Repetir los escenarios U01, U06 y U11 del acta de UAT | PO y SM | 15 min |
| 6 | Registrar el IMP, volver los PBI al Product Backlog con una nota y planear la corrección | SM | 10 min |

**Datos:** la 2.0.0 no cambia el esquema de las bases de la versión de retorno ni borra colecciones, así que el retroceso no necesita migrar ni restaurar datos. Las bases de MongoDB Atlas se mantienen.

## Prueba del retroceso (27/11/2026)

| Verificación | Resultado |
|---|---|
| `git worktree add` del commit `d17ddfc` en una carpeta aparte | Correcto |
| `mvnw -o package -DskipTests` con Java 21 | BUILD SUCCESS en 28 s, 12 jar `-1.0.0-SNAPSHOT` |
| Diferencias de esquema entre `d17ddfc` y la 2.0.0 | Ninguna: el Sprint 4 solo cambia seguridad web, la cabecera de correlación, pruebas, documentación y la versión |

Con esto se cumple AC-C05.2: hay un plan de retroceso probado antes de la decisión de release.
