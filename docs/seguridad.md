# Seguridad

- **Contraseñas:** BCrypt (`DelegatingPasswordEncoder`, prefijo `{bcrypt}`). Política mínima: 8 caracteres, con letras y números.
- **Bloqueo de cuenta:** 5 intentos fallidos bloquean la cuenta 15 minutos. El mensaje de error es genérico y se iguala el tiempo de respuesta cuando el usuario no existe.
- **JWT HS256:** el secreto viene de `JWT_SECRET` (.env), nunca del repositorio. Se valida firma, emisor (`wok-control`) y expiración (480 min). Los permisos se calculan en el servidor al iniciar sesión y viajan firmados, así que el rol enviado por el navegador no se tiene en cuenta.
- **API (`/api/**`):** sin estado, con `Authorization: Bearer`. No usa cookies, por eso no necesita CSRF. Los errores se devuelven en JSON (ProblemDetail).
- **Web:**
  - el JWT viaja en la cookie `WOK_TOKEN` (HttpOnly, SameSite=Lax, `Secure` configurable);
  - CSRF con cookie de doble envío en todos los formularios;
  - no se usan sesiones HTTP: los mensajes flash viajan en una cookie de vida corta;
  - se impiden las redirecciones abiertas tras el login.
- **Gateway:**
  - rechaza `/api/**` sin un JWT válido;
  - bloquea desde el exterior `/api/inventario/consumos/**`, `/api/inventario/recepciones/**`, `/api/auditoria/eventos` y `/api/pedidos/*/pago`;
  - CORS restringido a `WOK_CORS_ORIGINS`.
- **Defensa en profundidad:** cada servicio vuelve a autorizar por permiso (`SecurityConfig` de cada módulo). Ocultar un botón nunca es el control.
- **Auditoría:** las operaciones importantes se envían a la auditoría central de usuarios-service (quién, qué, cuándo y en qué servicio). Para cuentas bloqueadas y las acciones de plataforma también.
- **Primer administrador:** se crea solo si no existen usuarios y si las variables de bootstrap están definidas, y debe cambiar la contraseña al ingresar.

## Matriz de roles

| Permiso | Admin | Mesero | Cocinero | Cajero | Proveedor | Inventario | Admin plataforma |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| Panel | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Reportes, costos | ✓ | | | | | | |
| Gestionar carta y precios | ✓ | | | | | | |
| Ver carta | ✓ | ✓ | ✓ | ✓ | | ✓ | |
| Mesas: ver / gestionar | ✓/✓ | ✓/ | | ✓/ | | | |
| Pedidos: ver / registrar | ✓/ | ✓/✓ | ✓/ | ✓/ | | | |
| Operar cocina | | | ✓ | | | | |
| Inventario: ver / insumos / movimientos / ajustes | ✓/✓// | | ✓/// | | | ✓/✓/✓/✓ | |
| Reportar agotado | | | ✓ | | | | |
| Proveedores: ver / gestionar | ✓/✓ | | | | | ✓/ | |
| Órdenes de compra | ✓ | | | | | ✓ | |
| Portal del proveedor (solo lo propio) | | | | | ✓ | | |
| Facturación: ver / cobrar / cerrar caja | ✓// | | | ✓/✓/✓ | | | |
| Autorizar descuentos, anular comprobantes | ✓ | | | | | | |
| Usuarios, roles, parámetros, auditoría | | | | | | | ✓ |

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
