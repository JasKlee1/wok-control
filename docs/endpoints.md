# Endpoints REST (todos a través de http://localhost:8080)

La documentación interactiva completa, con esquemas, validaciones y ejemplos, está en
`/swagger-ui.html`; ahí se elige el servicio en el selector. Cada servicio expone su documento en
`/docs/<servicio>/v3/api-docs`. Las rutas marcadas como **interno** solo aceptan tokens de servicio
y el gateway las bloquea desde el exterior.

| Servicio | Método y ruta | Descripción |
|---|---|---|
| usuarios | `POST /api/auth/login` · `GET /api/auth/yo` | Login JWT y perfil del usuario |
| | `GET/POST /api/usuarios` · `GET/PUT /api/usuarios/{id}` · `PATCH /api/usuarios/{id}/estado` · `POST /api/usuarios/{id}/restablecer-password` · `PUT /api/usuarios/yo/password` | Gestión de usuarios |
| | `GET/POST /api/roles` · `GET/PUT /api/roles/{codigo}` · `GET /api/permisos` | Roles y permisos |
| | `GET/PUT /api/parametros` · `GET /api/auditoria` · `POST /api/auditoria/eventos` (interno) | Parámetros y auditoría |
| menu | `GET/POST /api/menu/categorias` · `GET/PUT /api/menu/categorias/{id}` | Categorías |
| | `GET/POST /api/menu/platos` · `GET/PUT/DELETE /api/menu/platos/{id}` · `PATCH .../precio` · `PATCH .../disponibilidad` | Platos (DELETE = retiro lógico) |
| | `GET/POST /api/menu/combos` · `GET/PUT/DELETE /api/menu/combos/{id}` · `PATCH .../disponibilidad` | Combos |
| | `GET /api/menu/carta` · `POST /api/menu/cotizacion` | Carta disponible y cotización |
| | `PUT/DELETE /api/menu/{platos\|combos\|categorias}/{id}/imagen` (multipart) · `GET /api/menu/imagenes/{id}` · `GET /api/menu/imagenes/creditos` | Fotos en GridFS con su crédito |
| pedidos | `GET/POST /api/mesas` · `GET/PUT /api/mesas/{id}` · `GET .../pedidos` · `PATCH .../liberar` · `PATCH .../servicio` | Mesas |
| | `GET/POST /api/pedidos` · `GET/PUT /api/pedidos/{id}` · `PATCH /api/pedidos/{id}/estado` (ENVIADO, CANCELADO) | Pedidos |
| | `PATCH /api/pedidos/{id}/items/{itemId}/estado` · `GET /api/cocina/comandas` | Cocina |
| | `GET /api/pedidos/facturables?mesaId=` · `GET /api/pedidos/resumen` · `POST /api/pedidos/{id}/pago` (interno) · `POST .../reversion-pago` (interno) | Facturación y panel |
| inventario | `GET/POST /api/insumos` · `GET/PUT /api/insumos/{id}` · `PATCH .../estado` · `GET /api/insumos/resumen` | Insumos |
| | `POST /api/insumos/{id}/movimientos` · `POST /api/insumos/{id}/ajustes` · `GET /api/insumos/{id}/movimientos` · `POST /api/insumos/{id}/reportes-agotado` | Movimientos |
| | `GET /api/inventario/movimientos` · `GET /api/inventario/alertas` · `GET /api/inventario/valorizacion` · `POST /api/inventario/reportes-agotado/{id}/atender` | Historial y alertas |
| | `POST /api/inventario/consumos` · `POST /api/inventario/consumos/{ref}/reversion` · `POST /api/inventario/recepciones` (internos, idempotentes) | Integración |
| proveedores | `GET/POST /api/proveedores` · `GET/PUT /api/proveedores/{id}` · `PATCH .../estado` · `GET .../historial` · `GET /api/proveedores/resumen` | Proveedores |
| | `GET/PUT /api/proveedores/mi-perfil` · `GET /api/ordenes-compra/mias[/{id}]` | Portal del proveedor |
| | `GET/POST /api/ordenes-compra` · `GET/PUT /api/ordenes-compra/{id}` · `POST .../envio` · `.../confirmacion` · `.../entrega` · `.../recepcion` · `.../recepcion/reintento` · `.../cancelacion` · `GET /api/ordenes-compra/resumen` | Órdenes de compra |
| facturacion | `GET /api/cuentas/mesa/{mesaId}` · `POST /api/facturas` · `GET /api/facturas` · `GET /api/facturas/{id}` · `GET /api/facturas/resumen` | Cuenta, cobro e historial |
| | `POST /api/facturas/{id}/anulacion` · `POST /api/facturas/{id}/sincronizacion` | Anulación y reintento |
| | `GET /api/caja/cierres/preliminar` · `GET/POST /api/caja/cierres` · `GET /api/caja/cierres/{id}` · `GET/POST /api/descuentos/autorizaciones` | Caja y descuentos |
| reportes | `GET /api/reportes/dashboard` · `GET /api/reportes/ventas` · `GET /api/reportes/costos-platos` | Panel e indicadores |

## Interfaz web del cliente en el salón (v2 · épica 7)

Rutas Thymeleaf servidas por pedidos-service a través del gateway (`pedidos-web`, `/cliente/**`).
El detalle de la sesión está en [seguridad.md](seguridad.md#sesión-de-mesa-del-cliente-v2--épica-7--adr-002).

| Método y ruta | Acceso | Respuesta |
|---|---|---|
| `GET /cliente/mesa/{codigo}` | Pública (lo abre el QR) | 302 a `/cliente/carta` con la cookie de la sesión de mesa · 404 «Mesa no disponible, pide ayuda al personal» |
| `GET /cliente/carta` | `CARTA_CLIENTE` con sesión ACTIVA | Carta con solo los platos disponibles y el botón «Agregar» · 401 si la sesión terminó |
| `POST /cliente/carrito` | `CARTA_CLIENTE` con sesión ACTIVA | HU-032: agrega un plato al pedido sin enviar (rechaza platos agotados) |
| `POST /cliente/carrito/{lineaId}` | `CARTA_CLIENTE` con sesión ACTIVA | HU-032: cambia cantidad y observación; con cantidad 0 lo quita |
| `POST /cliente/carrito/{lineaId}/quitar` | `CARTA_CLIENTE` con sesión ACTIVA | HU-032: quita un plato del pedido sin enviar |
| `GET /cliente/pedido` | `CARTA_CLIENTE` con sesión ACTIVA | HU-034: pedido sin enviar con aviso de agotados (HU-038), pedidos enviados con el estado de cada plato, motivo del rechazo (HU-037) y la cuenta (HU-035) |
| `POST /cliente/pedido/enviar` | `CARTA_CLIENTE` con sesión ACTIVA | HU-034: crea el pedido POR_CONFIRMAR · «Agrega al menos un plato» · «Quita los platos agotados antes de enviar» |
| `POST /cliente/pedidos/{id}/items/{itemId}/quitar` | `CARTA_CLIENTE` con sesión ACTIVA | AC-032.2: «El pedido ya fue enviado; pide el cambio al mesero» · 403 si el pedido es de otra mesa |
| `GET /cliente/estado` | `CARTA_CLIENTE` con sesión ACTIVA | JSON `{huella}`: el celular lo consulta cada 8 s y recarga si cambió |
| `POST /cliente/cuenta` | `CARTA_CLIENTE` con sesión ACTIVA | HU-035: la mesa queda como «Cuenta solicitada» para el cajero |
| `POST /pedidos/{id}/confirmar` | `PEDIDOS_GESTIONAR` | HU-037: el pedido QR pasa a cocina como comanda |
| `POST /pedidos/{id}/rechazar` | `PEDIDOS_GESTIONAR` | HU-037: exige motivo; el cliente lo ve |
| `PATCH /api/pedidos/{id}/estado` | `PEDIDOS_GESTIONAR` | Con `ENVIADO` confirma un pedido QR; con `RECHAZADO` y `motivo` lo rechaza |
| `POST /menu/platos/{id}/disponibilidad` · `PATCH /api/menu/platos/{id}/disponibilidad` | `MENU_GESTIONAR` o `COCINA_OPERAR` | HU-038: el cocinero marca un plato agotado |
| `GET /mesas/{id}/qr` | `MESAS_GESTIONAR` | QR imprimible de la mesa (genera el código la primera vez) |
| `GET /menu/imagenes/{id}` | `MENU_VER`, `MENU_GESTIONAR` o `CARTA_CLIENTE` | Foto del plato |

Códigos de respuesta usados en todas las API:

| Código | Significado |
|---|---|
| 200 / 201 / 204 | Operación correcta |
| 400 | Validación (campo `errores`) |
| 401 | Sin token o token inválido |
| 403 | Sin permiso |
| 404 | Recurso no encontrado |
| 409 | Duplicado o modificación concurrente |
| 422 | Regla de negocio incumplida |
| 503 | Otro servicio no disponible |
