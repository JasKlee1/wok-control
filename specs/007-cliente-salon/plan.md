# plan.md · Épica 7 · Cliente en el salón

| Campo | Valor |
|---|---|
| Versión | 1.0 (borrador para la Puerta 2) |
| Spec | [spec.md](spec.md) v1.0 |
| Redacta | Yuly Marcela Gómez Rico (según el documento) |
| Aprueba (Puerta 2) | Javier Steven Becerra Mantilla (PO) |

## 1. Contexto técnico

La v2 se construye sobre la v1, que ya está en producción:

- Java 21 y Spring Boot 4 en microservicios: config-server, Eureka, api-gateway, usuarios, menu, pedidos, inventario, proveedores, facturacion y reportes.
- MongoDB Atlas con una base por servicio.
- Interfaz Thymeleaf que sirve cada servicio a través del gateway.
- JWT HS256. En la web el token va en la cookie HttpOnly `WOK_TOKEN`; en la API va como Bearer.
- Autorización por permiso. Los permisos viajan en el token.

Esta épica no agrega microservicios, bases de datos ni dependencias de Maven. Se cumple la constitución 3: nada nuevo sin justificar.

## 2. Dónde vive cada parte

| Parte | Servicio | Por qué |
|---|---|---|
| Rol CLIENTE y permiso CARTA_CLIENTE | wok-common (`Roles`, `Permisos`); usuarios-service los sincroniza | `Roles.SISTEMA` es la fuente de verdad que usuarios-service ya sincroniza al arrancar. |
| Código QR de la mesa y sesión de mesa | pedidos-service | La mesa vive en pedidos-service. Abrir la sesión requiere validar la mesa, y el cierre ocurre al cobrar o liberar la mesa, que también pasa en pedidos-service. |
| Token de la sesión | wok-common (`TokenService.emitirTokenMesa`) | Se usa la misma llave y el mismo emisor que los demás tokens, así que todos los servicios lo validan sin cambios. |
| Carta del cliente | Datos en menu-service (`/api/menu/carta`, solo platos disponibles); vista en pedidos-service | pedidos-service ya consume la carta con su `MenuClient` para la toma de pedidos. Solo las fotos se piden a menu-service desde el navegador. |

[SUPUESTO] El borrador de tasks.md del documento ubicaba T002 en usuarios-service y T004 en menu-service. Al leer el código de la v1 se ubicaron como dice la tabla, porque la mesa y el cobro viven en pedidos-service. El documento permite este ajuste: "la numeración T### y el reparto final pueden cambiar" en la Puerta 3. Se somete a la Puerta 2.

## 3. ADR-002 · Sesión de mesa del CLIENTE por QR

**Estado:** propuesta para la Puerta 2 del Sprint 1 (09–10/11/2026). Ya está implementada en la rama `sprint-1/epica-7-cliente-qr`, con sus pruebas.

**Contexto.** RN-03 exige que el cliente entre sin usuario ni contraseña, que solo vea su mesa y que su acceso venza al cerrar la cuenta. NFR-WOK-003 exige 403 en todo recurso ajeno.

**Decisión.**

1. Cada mesa tiene un `codigoQr` aleatorio de 32 caracteres hexadecimales. Es un UUID sin guiones, único e indexado. Se genera la primera vez que el administrador abre el QR. El QR no lleva el número ni el id de la mesa, para que no se puedan adivinar las de otras mesas.
2. El QR apunta a `GET /cliente/mesa/{codigo}`, una ruta pública. Si la mesa existe, está activa y no está FUERA_DE_SERVICIO:
   - Se crea un documento `SesionMesa` en estado ACTIVA, en la colección `sesiones_mesa`.
   - Se emite un JWT con:
     - `tipo=mesa`
     - `roles=[CLIENTE]`
     - `permisos=[CARTA_CLIENTE]`
     - `mesaId`
     - `sesionMesa`
   - El JWT se guarda en la cookie HttpOnly `WOK_TOKEN` y se redirige a `/cliente/carta`.
   - Si la mesa no cumple las condiciones, se responde 404 con «Mesa no disponible, pide ayuda al personal» y no se crea sesión ni cookie (AC-033.2).
3. Cada petición del cliente valida contra la base que la sesión del token siga ACTIVA y sea de la misma mesa. Si no, se responde 401, se borra la cookie y se pide escanear de nuevo (AC-036.2).
4. La sesión se cierra (CERRADA, con su motivo) en dos casos:
   - Cuando se paga el último pedido activo de la mesa: «Cuenta cobrada».
   - Cuando se libera la mesa: «Mesa liberada».
5. El permiso CARTA_CLIENTE solo abre:
   - `/cliente/**` en pedidos-service.
   - `GET /menu/imagenes/*` en menu-service.

   Todo lo demás responde 403 en la API y redirige a `/acceso-denegado` en la web (AC-036.1, NFR-WOK-003).
6. El rol CLIENTE pertenece a `Roles.SOLO_SESION`: no se puede asignar a usuarios y no aparece en el formulario de usuarios.
7. La imagen del QR se dibuja en el navegador del administrador con qrcodejs 1.0.0, desde cdnjs. No se agrega ninguna dependencia al backend.

**Alternativas descartadas.**
- Registrar clientes con usuario y contraseña: añade fricción para pedir y queda fuera del alcance.
- Poner el número de mesa en el QR: cualquiera podría entrar a otra mesa cambiando el número.
- Generar el QR en el servidor con una librería Java: sería una dependencia nueva sin necesidad.

**Consecuencias.**
- El token de mesa dura lo mismo que los demás (`JWT_EXPIRATION_MINUTES`, 480 min por defecto). Su vencimiento real lo controla el estado de la sesión en la base.
- Cada petición del cliente hace una consulta a la base para validar la sesión. Es aceptable para el volumen de un restaurante.
- Para invalidar todos los QR impresos de una mesa basta con borrar su `codigoQr`: se genera uno nuevo.

## 4. Datos

| Colección (wok_pedidos) | Campos nuevos |
|---|---|
| `mesas` | `codigoQr` (único, disperso) |
| `sesiones_mesa` (nueva) | `mesaId`, `mesaNumero`, `estado` (ACTIVA o CERRADA), `abiertaEn`, `cerradaEn`, `motivoCierre`; índice `mesa_estado` |

## 5. Contrato (rutas nuevas del Sprint 1)

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `GET /cliente/mesa/{codigo}` | Pública | 302 a `/cliente/carta` con cookie, o 404 «Mesa no disponible» |
| `GET /cliente/carta` | CARTA_CLIENTE y sesión ACTIVA | 200 con la carta, o 401 «Tu sesión terminó» |
| `GET /mesas/{id}/qr` | MESAS_GESTIONAR | 200, QR imprimible de la mesa |
| `GET /menu/imagenes/{id}` | MENU_VER, MENU_GESTIONAR o CARTA_CLIENTE | Foto del plato |

El gateway enruta `/cliente/**` a pedidos-service (`pedidos-web`).

## 6. Auditoría

Se registran tres eventos nuevos:
- `GENERAR_QR`: el administrador genera el QR de una mesa.
- `ABRIR_SESION_QR`: se abre una sesión de mesa.
- `CERRAR_SESION_QR`: se cierra una sesión, con su motivo.

## 7. Matriz requisito → componente

| Requisito | Componentes |
|---|---|
| REQ-WOK-033 | `ClienteMesaWebController`, `SesionMesaService.abrir`, `MenuClient.carta`, `cliente/carta.html` |
| REQ-WOK-036 · NFR-WOK-003 | `Roles.CLIENTE`, `Permisos.CARTA_CLIENTE`, `SecurityConfig` de pedidos y menu, `SesionMesaService.validar` y `cerrarDeMesa`, `RolService.validarAsignables` |
| REQ-WOK-032, 034, 035, 037, 038 · NFR-WOK-001 | Pendientes (T005 a T016) |
