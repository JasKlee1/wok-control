# Seguridad

- **Contraseñas:** BCrypt (`DelegatingPasswordEncoder`, prefijo `{bcrypt}`). Política mínima: 8 caracteres, con letras y números.
- **Bloqueo de cuenta:** 5 intentos fallidos bloquean la cuenta 15 minutos. El mensaje de error es genérico y se iguala el tiempo de respuesta cuando el usuario no existe.
- **JWT HS256:** el secreto viene de `JWT_SECRET` (.env), nunca del repositorio. Se valida firma, emisor (`wok-control`) y expiración (480 min). Los permisos se calculan en el servidor al iniciar sesión y viajan firmados, así que el rol enviado por el navegador no se tiene en cuenta.
- **API (`/api/**`):** sin estado, con `Authorization: Bearer`. No usa cookies, por eso no necesita CSRF. Los errores se devuelven en JSON (ProblemDetail).
- **Web:**
  - el JWT viaja en la cookie `WOK_TOKEN` (HttpOnly, SameSite=Lax, `Secure` configurable);
  - CSRF con cookie de doble envío en todos los formularios, **también cuando la petición trae la cookie de sesión** (v2.0 · ADR-006);
  - no se usan sesiones HTTP: los mensajes flash viajan en una cookie de vida corta;
  - se impiden las redirecciones abiertas tras el login.
- **Gateway:**
  - rechaza `/api/**` sin un JWT válido;
  - bloquea desde el exterior `/api/inventario/consumos/**`, `/api/inventario/recepciones/**`, `/api/auditoria/eventos`, `/api/pedidos/*/pago`, `/api/usuarios/internos/**` y, desde la v2 · épica 9, `/api/pedidos/internos/**` y `/api/facturas/domicilios/**`;
  - CORS restringido a `WOK_CORS_ORIGINS`.
- **Defensa en profundidad:** cada servicio vuelve a autorizar por permiso (`SecurityConfig` de cada módulo). Ocultar un botón nunca es el control.
- **Auditoría:** las operaciones importantes se envían a la auditoría central de usuarios-service (quién, qué, cuándo y en qué servicio). Para cuentas bloqueadas y las acciones de plataforma también.
- **Primer administrador:** se crea solo si no existen usuarios y si las variables de bootstrap están definidas, y debe cambiar la contraseña al ingresar.

## Matriz de roles

| Permiso | Admin | Mesero | Cocinero | Cajero | Proveedor | Inventario | Admin plataforma | Anfitrión (v2) | Domiciliario (v2) |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Panel | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Reportes, costos | ✓ | | | | | | | | |
| Gestionar carta y precios | ✓ | | | | | | | | |
| Ver carta | ✓ | ✓ | ✓ | ✓ | | ✓ | | | |
| Mesas: ver / gestionar | ✓/✓ | ✓/ | | ✓/ | | | | | |
| Salón: mapa, lista de espera, sentar, marcar limpia (`SALON_OPERAR`) | ✓ | (solo marcar limpia) | | | | | | ✓ | |
| Domicilios: registrar, editar, cancelar, asignar (`DOMICILIOS_GESTIONAR`) | ✓ | | | ✓ | | | | | |
| Domicilios: mis entregas, en camino, cobrar, no entregado (`DOMICILIOS_REPARTIR`, solo los suyos) | | | | | | | | | ✓ |
| Pedidos: ver / registrar | ✓/ | ✓/✓ | ✓/ | ✓/ | | | | | |
| Operar cocina | | | ✓ | | | | | | |
| Inventario: ver / insumos / movimientos / ajustes | ✓/✓// | | ✓/// | | | ✓/✓/✓/✓ | | | |
| Reportar agotado | | | ✓ | | | | | | |
| Proveedores: ver / gestionar | ✓/✓ | | | | | ✓/ | | | |
| Órdenes de compra | ✓ | | | | | ✓ | | | |
| Portal del proveedor (solo lo propio) | | | | | ✓ | | | | |
| Facturación: ver / cobrar / cerrar caja | ✓// | | | ✓/✓/✓ | | | | | |
| Autorizar descuentos, anular comprobantes | ✓ | | | | | | | | |
| Usuarios, roles, parámetros, auditoría | | | | | | | ✓ | | |

**Anfitrión (v2 · HU-043).** Recibe a los grupos: ve el mapa del salón y la lista de espera, sienta grupos con su mesero (uniendo mesas si hace falta) y marca mesas limpias. No crea ni edita mesas (403, es del Administrador), no toma pedidos ni cobra (acceso no permitido). La lista de meseros la consulta pedidos-service en `GET /api/usuarios/internos/meseros`, que solo acepta tokens de servicio, devuelve solo usuario y nombre y está bloqueada en el gateway.

**Domiciliario (v2 · HU-051, RN-15).** Ve en «Mis entregas» solo los domicilios ASIGNADOS o EN_CAMINO a su nombre, con dirección, teléfono, valor a cobrar y el QR de la ruta; los marca en camino, cobra y entrega, o registra que no se entregó. Si consulta un domicilio de otro domiciliario recibe 403 (domicilios-service compara el `sub` del JWT con el domiciliario asignado). No entra a caja, inventario, pedidos de mesa ni a la gestión de domicilios. El cobro lo registra domicilios-service en facturación con token de servicio (`POST /api/facturas/domicilios`, bloqueado en el gateway) y el comprobante queda a nombre del cajero que registró el domicilio.

**Datos personales del cliente del domicilio (Ley 1581 de 2012).** Nombre, teléfono y dirección viven solo en `wok_domicilios`. No se escriben en los registros ni en la auditoría central (los eventos `ESTADO_DOMICILIO` llevan número, estados y motivo). A la API externa del QR (goQR.me) solo viaja la URL de Google Maps con la dirección, sin nombre ni teléfono, y se puede apagar con `WOK_QR_HABILITADO=false` (ADR-005).

El **Administrador** opera el restaurante y el **Administrador de plataforma** administra accesos.
Se mantuvieron separados para no mezclar la operación con la gestión de usuarios.

Además de los roles del sistema, que son de solo lectura y se definen en `Roles.java`, se pueden
crear roles personalizados combinando permisos del catálogo.

## Sesión de mesa del cliente (v2 · épica 7 · ADR-002)

El rol **CLIENTE** no es un usuario: es una sesión de mesa que se abre al escanear el QR. Su único
permiso es `CARTA_CLIENTE`, pertenece a `Roles.SOLO_SESION` y por eso no se puede asignar a usuarios
(`RolService` lo rechaza y el formulario de usuarios no lo muestra).

- **QR:** cada mesa tiene un `codigoQr` aleatorio (UUID sin guiones), que se genera la primera vez que
  alguien con `MESAS_GESTIONAR` abre `/mesas/{id}/qr`. El QR no contiene el número ni el id de la mesa.
- **Entrada:** `GET /cliente/mesa/{codigo}` es pública. Si la mesa existe, está activa y no está fuera de
  servicio, crea una `SesionMesa` ACTIVA y emite un JWT con `tipo=mesa`, `roles=[CLIENTE]`,
  `permisos=[CARTA_CLIENTE]`, `mesaId` y `sesionMesa`, que va en la cookie `WOK_TOKEN`. Si no, responde
  404 «Mesa no disponible, pide ayuda al personal» sin sesión ni cookie.
- **Cada petición** del cliente comprueba en la base que la sesión siga ACTIVA y sea de la misma mesa.
  Si no, responde 401, borra la cookie y pide escanear el QR de nuevo.
- **Vencimiento:** la sesión se cierra al pagar el último pedido activo de la mesa («Cuenta cobrada»)
  o al liberar la mesa («Mesa liberada»).
- **Alcance:** `CARTA_CLIENTE` solo abre `/cliente/**` (pedidos-service) y `GET /menu/imagenes/*`
  (menu-service). Todo lo demás responde 403 en la API y redirige a `/acceso-denegado` en la web.
- **Auditoría:** `GENERAR_QR`, `ABRIR_SESION_QR` y `CERRAR_SESION_QR`.

## Verificación de la v2.0 (Sprint 4 · PBI-29)

### Hallazgos corregidos

| Hallazgo | Origen | Corrección | Prueba |
|---|---|---|---|
| POST web con la cookie `WOK_TOKEN` y sin token CSRF aceptado | v1 (anotado en los Sprints 1 a 3) | El servidor de recursos OAuth2 de Spring Security hace que el filtro CSRF ignore las peticiones en las que encuentra un token, pensando en la cabecera `Authorization`. En la web el token viaja en una cookie, así que `WokSecurity.exigirCsrfConCookie()` vuelve a poner la regla por defecto: todo POST, PUT, PATCH y DELETE exige el token (ADR-006). | `DomiciliosSecurityTest.pbi29_*` (sin token y con token falso → `/acceso-denegado`, el servicio no se llama) |
| `HRS_REQUEST_PARAMETER_TO_HTTP_HEADER` (SpotBugs, rango 7) | v1 | `CorrelationIdFilter` solo copia a la respuesta y al MDC un `X-Correlation-Id` de 1 a 64 caracteres `[A-Za-z0-9._-]`; si no, genera un UUID. Evita la división de respuestas HTTP y la inyección de líneas en los registros. | `CorrelationIdFilterTest` |
| `BX_UNBOXING_IMMEDIATELY_REBOXED` (SpotBugs, rendimiento) | v1 | `JwtProperties` usa `Long.valueOf` y `Boolean.TRUE.equals`. | Compilación y pruebas de JWT |

### Análisis estático

SpotBugs 4.9.8 (plugin de Maven ejecutado desde la línea de comandos, sin agregarlo al proyecto), esfuerzo máximo, 12 módulos:

| Prioridad | Antes | Después | Decisión |
|---|---|---|---|
| Alta (crítica o alta) | 0 | **0** | Cumple AC-C03.1 |
| Media · seguridad | 1 | **0** | Corregido |
| Media · `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` (rango 18) | 312 | 312 | Aceptado: son documentos de Mongo, DTO y beans de Spring que exponen listas o fechas mutables. Ningún dato sale del servicio sin pasar por los DTO y la autorización por permiso. |

Búsqueda de secretos en los archivos versionados (`git ls-files`): sin cadenas de MongoDB con contraseña, `JWT_SECRET`, llaves privadas ni llaves de AWS. `.env`, `.run/` y `logs/` siguen en `.gitignore`.

### 403 de los roles nuevos (AC-C03.2)

| Rol | Recurso ajeno | Resultado esperado | Pruebas |
|---|---|---|---|
| CLIENTE (sesión de mesa) | Pedido o mesa de otra mesa; API de pedidos, mesas, carta interna | 403 | `ClienteMesaWebTest.ac036_1_*`, `ClientePedidoWebTest.ac036_1_*`, `PedidoClienteServiceTest.ac036_1_*`, `MenuClienteQrSecurityTest`, `RolClienteTest` |
| ANFITRION | Crear o editar mesas; pedidos; caja | 403 en la API, acceso no permitido en la web | `SalonWebTest.ac043_*`, `CajaAnfitrionTest`, `RolAnfitrionTest` |
| DOMICILIARIO | Domicilio de otro domiciliario; caja; inventario; gestión de domicilios | 403 en la API, acceso no permitido en la web | `DomiciliosSecurityTest`, `CajaDomiciliarioTest`, `InventarioDomiciliarioTest`, `RolDomiciliarioTest` |

### Ley 1581 de 2012 en los registros (AC-C03.3)

- `DomicilioServiceTest.pbi29_losRegistrosNoLlevanDatosPersonalesDelCliente`: captura la salida de todo el ciclo (registro, cocina, asignación, en camino, cobro con cambio) y de una caída de pedidos-service, y comprueba que no aparecen el nombre, el teléfono (con y sin espacios), la dirección ni el complemento.
- `QrRutaClientTest.pbi29_elRegistroDeUnFalloNoIncluyeLaDireccion`: si goQR.me falla, el registro nombra el domicilio (`D-0009`) y no la dirección, ni en claro ni codificada.
- `DomicilioServiceTest.ac053_2_*` (Sprint 3): los eventos de la auditoría central no llevan datos personales.
