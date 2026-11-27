# Matriz de trazabilidad final · WokControl v2.0 (PBI-27)

Regresión del 27/11/2026 con `mvnw clean install` (Java 21): **12 módulos, 264 pruebas, 0 fallos, 0 errores, 4 omitidas** (las 4 de integración con MongoDB, que corren solo con `WOK_TEST_MONGODB_URI`).

Columna «Verificación»: **A** = prueba automatizada en CI; **E** = script de extremo a extremo contra el sistema en marcha (`scripts/prueba-flujo.py`, `scripts/prueba-web.py`); **I** = inspección del despliegue o de la documentación. Toda HU tiene al menos una verificación A, salvo las de infraestructura (HU-003 a HU-005), que por su naturaleza se verifican con E o I.

## v1 · 31 historias (épicas 1 a 6)

| HU | Historia (resumen) | Verificación | Pruebas o evidencia |
|---|---|---|---|
| HU-001 | 10 microservicios con Spring Boot 4 y Java 21, una base por dominio | A, I | Compilación de los 12 módulos; `ExistenciasMongoIntegrationTest` (base aislada); `render.yaml` |
| HU-002 | Comunicación entre servicios por REST | A, E | `OperacionInternaServiceTest`, `PedidoServiceTest.enviarACocinaDescuentaInventario…`, `FacturacionServiceTest.cobra…NotificaElPago`; `prueba-flujo.py` (70 verificaciones) |
| HU-003 | Secretos solo en wok-secretos y `.env` | A, I | `ValidacionArranqueTest`; `JwtProperties` exige `JWT_SECRET` ≥ 32 bytes; búsqueda de secretos del Sprint 4 sin hallazgos |
| HU-004 | `git push` a main y Render recompila solo lo modificado | I | `render.yaml` y `render-demo.yaml` con `buildFilter` por módulo; despliegues del 23/11/2026 |
| HU-005 | Arquitectura, endpoints, seguridad y despliegue documentados | I | `docs/` (arquitectura, endpoints, seguridad, despliegue, modelo de dominio) |
| HU-006 | CRUD de usuarios del personal | A | `UsuarioServiceTest`; `UsuariosApiSecurityTest.adminPlataformaListaUsuarios`, `crearUsuarioValidaElCuerpo` |
| HU-007 | Iniciar sesión con usuario y contraseña | A, E | `AuthServiceTest`; `LoginWebControllerTest`; `UsuariosApiSecurityTest.loginEsPublicoYDevuelveToken` |
| HU-008 | Asignar un rol a cada usuario | A | `UsuarioServiceTest.rolProveedorRequiereVinculo…`; `RolClienteTest`, `RolAnfitrionTest`, `RolDomiciliarioTest` |
| HU-009 | Mensaje claro al abrir una pantalla ajena | A, E | `CajaAnfitrionTest`, `CajaDomiciliarioTest`, `InventarioDomiciliarioTest`, `DomiciliosSecurityTest.pantallasWebSegunElRol`; `prueba-web.py` |
| HU-010 | Contraseñas cifradas | A | `UsuarioServiceTest.creaUsuarioConHash…`, `exigeContrasenaConLetrasYNumeros`; `AuthServiceTest` |
| HU-011 | CRUD de platos | A | `MenuApiSecurityTest.administradorCreaPlato`, `precioNegativoYCamposVaciosSonRechazados`; `MenuServiciosTest.cambioDePrecioQuedaEnHistorial` |
| HU-012 | Categorías y combos | A | `MenuServiciosTest.cotizacionDeComboSuma…`, `comboNoDisponibleSiUnPlatoEstaAgotado` |
| HU-013 | El mesero consulta la carta sin cambiar precios | A | `MenuApiSecurityTest.meseroConsultaPlatos`, `meseroNoPuedeCrearNiRetirarPlatos`, `cajeroNoPuedeCambiarPrecios` |
| HU-014 | El cocinero marca un plato agotado | A | `CocineroAgotaPlatoTest`; `PedidoServiceTest.rechazaProductosNoDisponibles` |
| HU-015 | Fotos de la carta en GridFS | A | `ImagenServiceTest`; `MenuApiSecurityTest.soloQuienGestionaLaCartaPuedeSubirFotos` |
| HU-016 | Crear y consultar mesas | A | `PedidosApiSecurityTest.soloAdministracionCreaMesas`; `SalonServiceTest` |
| HU-017 | Abrir pedido por mesa con observaciones | A | `PedidoServiceTest.creaPedidoConPrecioCopiadoDeLaCartaYMesaOcupada`; `PedidosApiSecurityTest.cantidadesYPicanteSeValidan` |
| HU-018 | Enviar el pedido a cocina | A | `PedidoServiceTest.enviarACocinaDescuentaInventarioConReferenciaIdempotente`, `siInventarioFallaElPedidoSigueAbierto` |
| HU-019 | El cocinero ve solo comandas y cambia estados | A | `PedidosApiSecurityTest.cocineroConsultaComandas`, `cocineroNoRegistraPedidos…`; `PedidoServiceTest.meseroNoPuedeCambiarEstadosDePreparacion…` |
| HU-020 | Modificar o eliminar ítems antes de enviar | A | `PedidoServiceTest.cancelarExigeMotivo…`, `cancelarEnCocinaSinPreparar…`; `PedidosApiSecurityTest.pedidoSinProductosEsRechazado` |
| HU-021 | Tomar pedidos desde tablet o celular | A, E | `CartaLivianaTest.ac039_2_*`, `SalonWebTest.ac046_2_*` (viewport y anchos); `prueba-web.py` |
| HU-022 | Insumos con unidad y stock mínimo | A | `InventarioApiSecurityTest.cantidadNegativaEsInvalida`; `MenuServiciosTest.recetaValidaCopiaNombreYUnidadDelInsumo` |
| HU-023 | Entradas de mercancía | A | `ExistenciasMongoIntegrationTest.costoPromedioPonderado…`; `OrdenCompraServiceTest.recepcionIngresaAInventario…` |
| HU-024 | Descuento automático de insumos | A | `OperacionInternaServiceTest` (5 pruebas de idempotencia y compensación); `ExistenciasMongoIntegrationTest.consumoReintentado…` |
| HU-025 | Alerta de stock mínimo | A, E | `InventarioApiSecurityTest.stockInsuficienteResponde422ConMensajeClaro`; `ReportesTest.elPanelSoloConsulta…`; `prueba-flujo.py` |
| HU-026 | Solo administradores ajustan inventario | A | `InventarioApiSecurityTest.ajustesSoloConPermisoDeAjuste`, `proveedorNoPuedeModificarInventario`; `ExistenciasMongoIntegrationTest.ajusteRegistraLaDiferencia…` |
| HU-027 | Consultar y anular facturas con autorización | A | `FacturacionServiceTest.anularExigeMotivoYNoSePermiteSiYaEstaEnUnCierre`; `FacturacionApiSecurityTest.cajeroNoAutorizaDescuentosNiAnula` |
| HU-028 | Cobrar una cuenta con impuestos | A | `FacturacionServiceTest.cobraConTotalesCalculadosEnServidor…`; `CalculadoraCuentaTest.calculaDescuentoEImpuestoAlConsumoEnPesos` |
| HU-029 | Pagos mixtos | A | `CalculadoraCuentaTest.elEfectivoAdmiteCambio…`, `pagosInsuficientesSonRechazados`, `netoEnEfectivoDescuentaElCambio` |
| HU-030 | Descuento autorizado | A | `FacturacionServiceTest.cajeroNoPuedeDescontarSinCodigo…`, `descuentoConCodigoValidoSeAplica…` |
| HU-031 | Cierre de caja y tablero de ventas | A | `FacturacionApiSecurityTest.administradorConsultaVentasYCierres…`; `ReportesTest` |

## v2 · 22 historias (épicas 7 a 9)

| HU | Requisito | Criterios | Pruebas |
|---|---|---|---|
| HU-032 | REQ-WOK-032 | AC-032.1, AC-032.2 | `PedidoClienteServiceTest.ac032_*`; `ClientePedidoWebTest.ac032_*` |
| HU-033 | REQ-WOK-033 | AC-033.1, AC-033.2 | `MenuServiciosTest.ac033_1_*`; `ClienteMesaWebTest.ac033_*` |
| HU-034 | REQ-WOK-034 | AC-034.1, AC-034.2, AC-034.3 | `PedidoClienteServiceTest.ac034_*`; `ClientePedidoWebTest.ac034_*` |
| HU-035 | REQ-WOK-035 | AC-035.1, AC-035.2 | `PedidoClienteServiceTest.ac035_*`, `hu035_*`; `ClientePedidoWebTest.ac035_*` |
| HU-036 | REQ-WOK-036 | AC-036.1, AC-036.2 | `PedidoClienteServiceTest.ac036_1_*`; `ClienteMesaWebTest.ac036_*`; `ClientePedidoWebTest.ac036_*`; `SesionMesaServiceTest`; `RolClienteTest`; `MenuClienteQrSecurityTest` |
| HU-037 | REQ-WOK-037 | AC-037.1, AC-037.2 | `PedidoClienteServiceTest.ac037_*`; `ClientePedidoWebTest.ac037_*` |
| HU-038 | REQ-WOK-038 | AC-038.1, AC-038.2 | `CocineroAgotaPlatoTest.ac038_1_*`; `ClientePedidoWebTest.ac038_*`; `PedidoClienteServiceTest.ac038_*` |
| HU-039 | NFR-WOK-001, NFR-WOK-002 | AC-039.1, AC-039.2 | `CartaLivianaTest.ac039_*` (p95 = 57 ms en el servidor, ver `docs/rendimiento.md`) |
| HU-040 | REQ-WOK-040 | AC-040.1, AC-040.2 | `SalonServiceTest.ac040_*`, `hu040_*`; `SalonWebTest.ac040_2_*`, `hu040_*` |
| HU-041 | REQ-WOK-041 | AC-041.1, AC-041.2 | `SalonServiceTest.ac041_*`; `SalonWebTest.hu043_elAnfitrionVeElSalon…` |
| HU-042 | REQ-WOK-042 | AC-042.1, AC-042.2 | `SalonServiceTest.ac042_*`, `hu042_*`, `rn07_*`; `SalonWebTest.ac042_*`; `UsuariosApiSecurityTest.elTokenDeServicioSoloVe…` |
| HU-043 | REQ-WOK-043, NFR-WOK-003 | AC-043.1, AC-043.2 | `SalonWebTest.ac043_*`, `hu043_*`; `CajaAnfitrionTest`; `RolAnfitrionTest` |
| HU-044 | REQ-WOK-044 | AC-044.1, AC-044.2 | `SalonServiceTest.ac044_*`, `hu044_*`; `SalonWebTest.ac044_2_*` |
| HU-045 | REQ-WOK-045 | AC-045.1, AC-045.2 | `SalonServiceTest.ac045_*`, `adr003_*` |
| HU-046 | NFR-WOK-004, NFR-WOK-005 | AC-046.1, AC-046.2 | `SalonServiceTest.ac046_1_*`; `SalonWebTest.ac046_*`, `pbi28_*` (peor caso 3013 ms) |
| HU-047 | REQ-WOK-047 | AC-047.1, AC-047.2, AC-047.3 | `DomicilioServiceTest.ac047_*`; `DomiciliosSecurityTest.ac047_2_*`; `PedidoDomicilioTest` |
| HU-048 | REQ-WOK-048 | AC-048.1, AC-048.2 | `DomicilioServiceTest.ac048_*`, `rn14_*` |
| HU-049 | REQ-WOK-049, NFR-WOK-007 | AC-049.1, AC-049.2 | `DomicilioServiceTest.ac049_*`; `DomiciliosSecurityTest.qrDeLaRuta…`; `QrRutaClientTest` |
| HU-050 | REQ-WOK-050 | AC-050.1, AC-050.2 | `DomicilioServiceTest.ac050_*`; `FacturaDomicilioTest` |
| HU-051 | REQ-WOK-051, NFR-WOK-003 | AC-051.1, AC-051.2 | `DomiciliosSecurityTest`; `CajaDomiciliarioTest`; `InventarioDomiciliarioTest`; `RolDomiciliarioTest` |
| HU-052 | REQ-WOK-052 | AC-052.1, AC-052.2 | `DomicilioServiceTest.ac052_*`; `PedidoDomicilioTest.ac052_1_*` |
| HU-053 | REQ-WOK-053, NFR-WOK-006 | AC-053.1, AC-053.2, AC-053.3 | `DomiciliosSecurityTest.ac053_1_*`; `DomicilioServiceTest.ac053_2_*`; `ValidacionArranqueTest` |

## Ítems de calidad del Sprint 4

Ver la matriz de [specs/010-cierre-v2/tasks.md](../specs/010-cierre-v2/tasks.md).

## Resumen

| Grupo | HU | Con prueba automatizada | Solo E o I |
|---|---|---|---|
| v1 | 31 | 29 | 2 (HU-004, HU-005) |
| v2 | 22 | 22 | 0 |
| **Total** | **53** | **51** | **2** |
