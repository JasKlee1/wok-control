# Modelo de dominio (colecciones por base de datos)

| Base | Colección | Contenido principal |
|---|---|---|
| wok_usuarios | `usuarios` | username, nombre, email (únicos), hash BCrypt, roles, proveedorId, intentos y bloqueo |
| | `roles` | 7 roles del sistema y roles personalizados con sus permisos |
| | `auditoria` | Eventos de todos los servicios (solo inserción) |
| | `parametros` | Datos del restaurante e impuesto al consumo |
| wok_menu | `categorias`, `platos`, `combos` | Platos con precio, historial de precios, alérgenos, picante y receta (insumoId, cantidad) |
| wok_pedidos | `mesas`, `pedidos` | Pedido con ítems que guardan copia de precio y receta, estados por ítem, alergias, historial, cancelación |
| wok_inventario | `insumos` | Cantidad, mínimo, costo promedio, lotes con vencimiento |
| | `movimientos_inventario` | Tipo, cantidad, saldo anterior y nuevo, motivo, usuario, referencia (inmutable) |
| | `operaciones_inventario` | Control de idempotencia (`PEDIDO-…`, `OC-…`) |
| | `reportes_agotado` | Reportes enviados desde cocina |
| wok_proveedores | `proveedores` | NIT único, contacto, categorías, insumos que suministra, condiciones comerciales |
| | `ordenes_compra` | Ítems, entrega reportada, recepción, historial de estados |
| wok_facturacion | `facturas` | Comprobante interno: líneas, descuento, impuesto, pagos, cambio, anulación, sincronización |
| | `pedidos_facturados` | Bloqueo único por pedido que impide el doble cobro |
| | `autorizaciones_descuento` | Códigos de un solo uso, con vencimiento |
| | `cierres_caja` | Totales de sistema y contados por método, diferencias |

Todas las bases tienen además la colección `secuencias`, con los consecutivos `P-`, `OC-`, `TIQ-` y `CC-`.

Los importes se guardan como `Decimal128`. Las entidades editables usan `@Version` para el control de
concurrencia optimista.
