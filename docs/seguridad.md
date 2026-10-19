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

Además de los 7 roles del sistema, que son de solo lectura y se definen en `Roles.java`, se pueden
crear roles personalizados combinando permisos del catálogo.
