# Wok Control

Plataforma de microservicios para gestionar un restaurante de comida china: carta, mesas, pedidos,
cocina, inventario, proveedores, caja y reportes. Java 21, Spring Boot 4.0.8, Spring Cloud 2025.1.3,
MongoDB Atlas, Thymeleaf y API REST documentadas con OpenAPI.

> Los comprobantes que emite el sistema son **comprobantes internos de venta**. **No** son facturas
> electrónicas DIAN: no hay integración con un proveedor tecnológico autorizado.

## Componentes

| Módulo | Puerto | Base de datos | Responsabilidad |
|---|---|---|---|
| config-server | 8888 | — | Configuración centralizada (Spring Cloud Config, `config-server/src/main/resources/config`) |
| discovery-service | 8761 | — | Registro de servicios (Eureka) |
| api-gateway | 8080 | — | Punto de entrada único: web, API y Swagger; valida JWT y bloquea endpoints internos |
| usuarios-service | 8081 | `wok_usuarios` | Usuarios, roles, permisos, login JWT, parámetros, auditoría central |
| menu-service | 8082 | `wok_menu` | Categorías, platos, recetas, combos, precios, carta y cotización |
| pedidos-service | 8083 | `wok_pedidos` | Mesas, pedidos, comandas y cocina |
| inventario-service | 8084 | `wok_inventario` | Insumos, lotes, movimientos, consumos idempotentes, alertas |
| proveedores-service | 8085 | `wok_proveedores` | Proveedores, órdenes de compra, recepción, portal del proveedor |
| facturacion-service | 8086 | `wok_facturacion` | Cuentas, cobros, comprobantes, descuentos autorizados, cierres de caja |
| reportes-service | 8087 | — | Panel por rol y reportes (agrega datos de los demás vía REST) |
| wok-common | — | — | Librería compartida: seguridad, errores, cliente interno, layout, CSS/JS |

Cada servicio sigue la estructura `controller/api` (`@RestController`, `/api/**`, JSON) y
`controller/web` (`@Controller`, vistas Thymeleaf), con `entity`, `repository`, `service`, `dto`,
`client` y `config`. Más detalle en [docs/arquitectura.md](docs/arquitectura.md).

## Requisitos

- **JDK 21** (si tiene varios JDK, defina `JAVA_HOME` apuntando al 21). No requiere Maven instalado: se usa `mvnw`.
- Acceso a **MongoDB Atlas**, con su IP permitida en *Network Access*.
- Windows PowerShell para los scripts; Python 3 solo para las pruebas de extremo a extremo.

## Configuración

1. Copie `.env.example` como `.env` en la raíz. Ese archivo está en `.gitignore` y nunca se sube al repositorio.
2. Complete `MONGODB_URI` con la contraseña real. Si contiene caracteres especiales (`@ : / ? #`), codifíquelos en URL.
3. Genere `JWT_SECRET` con `python -c "import secrets;print(secrets.token_urlsafe(48))"`.
4. Primer administrador: defina `WOK_BOOTSTRAP_ADMIN_USERNAME` y `WOK_BOOTSTRAP_ADMIN_PASSWORD`.
   Solo se crea si no existe ningún usuario, y el sistema obliga a cambiar la contraseña al ingresar.
   Después de crearlo, borre la contraseña del `.env`. No hay usuarios ni contraseñas predeterminados en el código.

Cada servicio lee `.env` al arrancar (`spring.config.import`) y el resto de su configuración desde config-server.

## Compilar y probar

```powershell
.\mvnw.cmd clean install                 # compila los 12 módulos y ejecuta las pruebas
```

Las pruebas de integración con MongoDB usan una base **exclusiva** (`wok_test_inventario`) y solo se
ejecutan si existe la variable `WOK_TEST_MONGODB_URI`:

```bash
export WOK_TEST_MONGODB_URI="$(grep '^MONGODB_URI=' .env | cut -d= -f2-)"; ./mvnw test -pl inventario-service
```

## Ejecutar

```powershell
powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1 -Compilar   # arranca todo en orden
powershell -ExecutionPolicy Bypass -File scripts\estado.ps1              # salud y registro en Eureka
powershell -ExecutionPolicy Bypass -File scripts\reiniciar.ps1 -Servicio menu-service
powershell -ExecutionPolicy Bypass -File scripts\detener.ps1
```

El orden de arranque es: config-server → discovery-service → servicios de negocio → api-gateway.
Los registros quedan en `logs\`. Desde VS Code: tareas *Wok: …* (`Terminal → Run Task`) o
configuraciones de `.vscode/launch.json`.

- Aplicación web: <http://localhost:8080> (inicio de sesión en `/login`)
- Swagger central: <http://localhost:8080/swagger-ui.html>. Ejecute `POST /api/auth/login`, pulse **Authorize** y pegue el token.
- Swagger de cada servicio: `http://localhost:808X/swagger-ui.html`
- Eureka: <http://localhost:8761>

Con Docker (preparado; **no verificado** porque Docker no estaba instalado en el equipo de desarrollo):
`docker compose up --build`. El `Dockerfile` compila dentro del contenedor el módulo indicado en el
argumento `MODULO`, así que no hace falta compilar antes.

## Datos de demostración y fotos

Con el sistema en marcha y `WOK_DEMO_PASSWORD` definido en `.env`:

```powershell
python scripts\cargar-datos-demo.py      # carta de "Éxito Chino", inventario, proveedores, mesas y pedidos del día
python scripts\cargar-imagenes-demo.py   # fotos de referencia de Wikimedia Commons (licencias libres)
```

Ambos scripts trabajan a través de la API, no borran ni modifican información existente y pueden
ejecutarse de nuevo sin duplicar datos. Las fotos se guardan en MongoDB (GridFS de `wok_menu`) junto con
su autor y licencia, que se muestran bajo cada foto y en *Menú → Platos → Créditos de las fotos*.
Cualquier foto puede reemplazarse por una propia desde el detalle del plato, combo o categoría.

## Pruebas de extremo a extremo

Con el sistema en marcha:

```powershell
python scripts\prueba-flujo.py   # 70 verificaciones: pedido → cocina → cobro → inventario → compras → cierre
python scripts\prueba-web.py     # 87 verificaciones: pantallas por rol, login con CSRF, accesos denegados
```

`prueba-flujo.py` crea datos reales con un sufijo aleatorio: usuarios de cada rol, un plato, una mesa,
un comprobante y un cierre. Las credenciales de esos usuarios quedan en `.run/credenciales-prueba.txt`.

## Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| `Could not resolve placeholder 'MONGODB_URI'` | Falta `.env` en la raíz, o se ejecutó desde otra carpeta |
| Timeout al conectar a Atlas | La IP no está en *Network Access* de Atlas |
| Gateway responde 503 | El servicio aún no se registró en Eureka; espere unos 30 s |
| `Unable to rename ... .jar` al compilar | Hay un servicio ejecutándose desde `target/`; use los scripts, que copian a `.run/` |
| Error de versión de Java | `JAVA_HOME` apunta a otro JDK; use el 21 |

Documentación: [arquitectura](docs/arquitectura.md) · [endpoints](docs/endpoints.md) ·
[seguridad](docs/seguridad.md) · [modelo de dominio](docs/modelo-dominio.md) ·
[informe de auditoría](docs/informe-auditoria.md)
