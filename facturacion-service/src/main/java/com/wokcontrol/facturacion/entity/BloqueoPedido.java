package com.wokcontrol.facturacion.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Garantiza que un pedido se cobre una sola vez: el _id es el id del pedido, así dos cobros
 * simultáneos del mismo pedido chocan por clave duplicada. Se libera al anular el comprobante.
 */
@Document("pedidos_facturados")
public record BloqueoPedido(@Id String pedidoId, String facturaId, Instant fecha) {
}
