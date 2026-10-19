package com.wokcontrol.reportes.service;

import static com.wokcontrol.common.security.Permisos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import com.wokcontrol.common.exception.WokException;
import com.wokcontrol.common.security.UsuarioActual;
import com.wokcontrol.reportes.client.ServiciosClient;
import com.wokcontrol.reportes.dto.ReportesDtos.Alertas;
import com.wokcontrol.reportes.dto.ReportesDtos.Dashboard;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenCompras;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenPedidos;
import com.wokcontrol.reportes.dto.ReportesDtos.ResumenVentas;
import com.wokcontrol.reportes.dto.ReportesDtos.Seccion;
import com.wokcontrol.reportes.dto.ReportesDtos.Valorizacion;

/**
 * Arma el panel según los permisos del usuario. Cada sección se consulta en paralelo y de forma
 * independiente: si un servicio no responde, esa tarjeta lo indica y el resto del panel funciona.
 * Las secciones que el rol no puede ver no se consultan.
 */
@Service
public class DashboardService {

    private final ServiciosClient servicios;
    private final ZoneId zona;
    private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

    public DashboardService(ServiciosClient servicios, @Value("${wok.zona-horaria:America/Bogota}") String zona) {
        this.servicios = servicios;
        this.zona = ZoneId.of(zona);
    }

    public Dashboard construir() {
        UsuarioActual u = UsuarioActual.requerido();
        Consumer<HttpHeaders> auth = servicios.credenciales();
        LocalDate hoy = LocalDate.now(zona);

        CompletableFuture<Seccion<ResumenVentas>> ventas = u.tieneAlguno(FACTURACION_VER, REPORTES_VER)
                ? async(() -> servicios.ventas(auth, hoy, hoy)) : nada();
        CompletableFuture<Seccion<ResumenPedidos>> pedidos = u.tieneAlguno(PEDIDOS_VER, REPORTES_VER, FACTURACION_VER)
                ? async(() -> servicios.pedidos(auth)) : nada();
        CompletableFuture<Seccion<Alertas>> alertas = u.tieneAlguno(INVENTARIO_VER, REPORTES_VER)
                ? async(() -> servicios.alertas(auth)) : nada();
        CompletableFuture<Seccion<Valorizacion>> inventario = u.tieneAlguno(INVENTARIO_VER, REPORTES_VER)
                ? async(() -> servicios.valorizacion(auth)) : nada();
        CompletableFuture<Seccion<ResumenCompras>> compras = u.tieneAlguno(COMPRAS_GESTIONAR, PROVEEDORES_VER, REPORTES_VER)
                ? async(() -> servicios.compras(auth)) : nada();

        return new Dashboard(ventas.join(), pedidos.join(), alertas.join(), inventario.join(), compras.join());
    }

    private <T> CompletableFuture<Seccion<T>> async(Supplier<T> llamada) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                T datos = llamada.get();
                return datos == null ? Seccion.<T>fallo("Respuesta vacía") : Seccion.ok(datos);
            } catch (WokException ex) {
                return Seccion.<T>fallo(ex.getMessage());
            } catch (RuntimeException ex) {
                return Seccion.<T>fallo("No disponible");
            }
        }, hilos);
    }

    private static <T> CompletableFuture<Seccion<T>> nada() {
        return CompletableFuture.completedFuture(null);
    }
}
