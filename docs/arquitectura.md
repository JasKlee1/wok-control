# Arquitectura

```
Navegador / Swagger / Postman
            │  :8080
       api-gateway ── valida JWT en /api/**, bloquea endpoints internos, CORS, X-Correlation-Id
            │  lb://<servicio>  (Eureka :8761)       config-server :8888 → configuración de todos
 ┌──────────┼──────────┬───────────┬─────────────┬──────────────┬──────────────┐
usuarios   menu     pedidos    inventario   proveedores   facturacion   reportes
wok_usuarios wok_menu wok_pedidos wok_inventario wok_proveedores wok_facturacion (sin BD)
```

- **Una base lógica por servicio** en el mismo clúster de Atlas. Ningún servicio lee colecciones de
  otro; las relaciones son por identificador y se resuelven por REST (`client/` en cada servicio).
- **UI distribuida por dominio.** Cada servicio sirve sus pantallas (`controller/web`) con el layout
  común de `wok-common`. Como todo entra por el gateway (mismo origen), la cookie de sesión y el CSRF
  funcionan entre servicios.
- **Llamadas internas.** `ClienteInterno` usa un `RestClient` con balanceo por Eureka, timeouts
  (conexión 2 s, lectura 5 s), traducción de errores remotos y un reintento solo en llamadas
  idempotentes. Usa dos tipos de credencial:
  - el JWT del usuario, cuando el destino debe aplicar los permisos de ese usuario;
  - un token de servicio de 5 minutos (permiso `SISTEMA`, claim `actor`), para operaciones internas.

## Flujo del pedido y consistencia (sin transacciones distribuidas)

1. El mesero registra el pedido. pedidos-service cotiza en menu-service y copia precio, alérgenos y receta (`ABIERTO`; la mesa queda `OCUPADA`).
2. Al **enviar a cocina**, el pedido llama a `POST /api/inventario/consumos` con la referencia `PEDIDO-<id>`:
   - si falta stock o inventario no responde, el pedido sigue `ABIERTO`;
   - el reintento es idempotente gracias a la clave única en `operaciones_inventario`;
   - inventario descuenta con `findAndModify` condicionado a `cantidad >= requerido`, sin dejar saldos negativos; si una línea falla, compensa las ya descontadas.
3. Cocina avanza los ítems (`PENDIENTE → EN_PREPARACION → LISTO`) y el mesero los marca `ENTREGADO`. El estado del pedido se deriva de sus ítems.
4. Si se **cancela** antes de que cocina empiece, se revierte el consumo, también de forma idempotente. Queda registrado quién canceló y el motivo.
5. Caja **cobra**. El total se recalcula en el servidor (subtotal − descuento + 8 % de impuesto al consumo, en pesos). Dos controles impiden el doble cobro:
   - la clave de idempotencia hace que una solicitud repetida devuelva el mismo comprobante;
   - un bloqueo único por pedido impide cobrarlo en dos comprobantes.
6. facturación notifica a pedidos que el pedido está `PAGADO` (idempotente) y la mesa se libera. Si pedidos no responde, el comprobante queda `PENDIENTE` y una tarea reintenta cada minuto.
7. Las compras aumentan el inventario solo al **confirmar la recepción** (referencia `OC-<id>`, idempotente). Si inventario falla, la recepción queda guardada y se reintenta con los mismos datos.

No se usa cola de mensajes: la integración es REST con operaciones idempotentes y estados de
sincronización visibles.

## Tecnologías y versiones

Java 21 · Spring Boot 4.0.8 · Spring Cloud 2025.1.3 · springdoc-openapi 3.0.3 · Spring Security 7
(resource server JWT HS256 con Nimbus) · Spring Data MongoDB (BigDecimal como Decimal128) ·
Thymeleaf 3.1 · Bootstrap 5.3.8 y Bootstrap Icons servidos como webjars locales.

Se eligió Boot 4.0.x y no 4.1.x porque, a la fecha, Spring Cloud no tiene una versión GA compatible
con Boot 4.1 (la línea 2026.0 está en hitos).
