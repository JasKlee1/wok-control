# Informe de verificación y auditoría — 19/10/2026

## Qué se verificó realmente

| Verificación | Resultado |
|---|---|
| `mvnw clean install`: 12 módulos con Java 21 | BUILD SUCCESS |
| Pruebas automatizadas: unitarias, de seguridad por rol en los controladores REST y de validación | 110 ejecutadas, 0 fallos, 0 omitidas |
| Pruebas de integración contra MongoDB Atlas (base aislada `wok_test_inventario`) | 4/4. 20 hilos compiten por 10 unidades: exactamente 10 éxitos y saldo final 0; consumo y reversión idempotentes; costo promedio; ajuste |
| Arranque real de los 10 componentes con `scripts/iniciar.ps1` y registro de los 8 clientes en Eureka | Correcto |
| `scripts/prueba-flujo.py` por el gateway contra Atlas | 70/70 verificaciones |
| `scripts/prueba-web.py`: pantallas de los 6 roles, login con CSRF, accesos denegados, detalle de registros | 87/87 verificaciones |

Errores encontrados al ejecutar el sistema real y corregidos:
- Ambigüedad de `TaskExecutor`, que impedía arrancar 6 servicios.
- Dependencia circular entre el cliente de Eureka y el `RestClient` con balanceo.
- Jackson 3 rechazaba campos primitivos ausentes en el JSON.
- Restricciones de Thymeleaf 3.1 con `T(...)` y con beans dentro de los parámetros de fragmentos.
- Opciones de un `select` sin atributo `value`.

## Revisión de los requisitos de la auditoría

- **Controladores API y web separados** en todos los servicios (`controller/api` y `controller/web`). Las rutas JSON (`/api/**`) y las HTML no se mezclan, y cada una tiene su propio manejador de errores.
- **Repositorios:** solo existen para entidades persistentes. reportes-service no tiene base de datos y excluye la autoconfiguración de MongoDB.
- **Bases de datos:** ningún servicio accede a la base de otro; todas las integraciones son REST.
- **Inventario:** la cantidad nunca se edita directamente; todo cambio genera un movimiento con saldo anterior y nuevo.
- **Cálculos financieros:** se hacen en el backend; el `total` que envíe el cliente se ignora (verificado).
- **Credenciales:** no hay credenciales en archivos versionados. `.env` está en `.gitignore` y `.env.example` solo contiene marcadores.
- **Swagger:** disponible de forma central en el gateway y en cada servicio.

## Limitaciones y pendientes conocidos (declarados con honestidad)

- **Docker:** `compose.yaml` y `Dockerfile` están preparados, pero **no se probaron** porque Docker no está instalado en el equipo. Tampoco hay despliegue en la nube verificado.
- **Firma del JWT:** es HS256 con un secreto compartido, así que cualquier servicio podría emitir tokens. En producción convendría usar RS256: solo usuarios-service tendría la clave privada y el resto validaría con la pública.
- **Auditoría:** el envío a la auditoría central es de mejor esfuerzo; si usuarios-service está caído, el evento queda solo en el log del servicio que lo generó.
- **Lotes:** se registran con su vencimiento y generan alertas, pero las salidas no se descuentan por lote (no hay FIFO por lote).
- **Precios e impuesto:** los precios de la carta se consideran antes del impuesto al consumo (8 % por defecto, configurable en Parámetros).
- **Cambio de contraseña obligatorio:** se exige en el login web. La API sigue funcionando aunque la contraseña no se haya cambiado.
- **Datos de prueba en las bases reales:** `prueba-flujo.py` crea datos con sufijo (usuarios, plato, mesa, comprobante, cierre). Los comprobantes no se pueden borrar, por diseño de trazabilidad; los usuarios se pueden desactivar.
- **Cobertura de pruebas:** no hay prueba de carga del contexto completo (`@SpringBootTest`) por servicio. El arranque real lo cubren los scripts de extremo a extremo.
