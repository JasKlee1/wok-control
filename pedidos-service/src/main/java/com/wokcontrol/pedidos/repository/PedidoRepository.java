package com.wokcontrol.pedidos.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.wokcontrol.pedidos.entity.EstadoPedido;
import com.wokcontrol.pedidos.entity.Pedido;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByMesaIdAndEstadoInOrderByCreadoEnAsc(String mesaId, Collection<EstadoPedido> estados);

    /** v2 · HU-034: pedidos que envió una sesión de mesa (lo que el cliente ve en su celular). */
    List<Pedido> findBySesionMesaIdOrderByCreadoEnAsc(String sesionMesaId);

    List<Pedido> findBySesionMesaIdInOrderByCreadoEnAsc(Collection<String> sesionesMesa);

    long countByMesaIdAndEstadoIn(String mesaId, Collection<EstadoPedido> estados);

    List<Pedido> findByEstadoInOrderByEnviadoEnAsc(Collection<EstadoPedido> estados);

    List<Pedido> findByEstadoInOrderByCreadoEnAsc(Collection<EstadoPedido> estados);
}
