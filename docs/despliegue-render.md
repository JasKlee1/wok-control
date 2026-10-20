# Despliegue en Render

Wok Control se despliega como **10 servicios web independientes**, uno por microservicio. Todos se
construyen con el mismo `Dockerfile`, y la variable `MODULO` indica qué módulo compila cada uno.
El archivo [`render.yaml`](../render.yaml), que Render llama *Blueprint*, crea los 10 servicios de una
sola vez.

La base de datos sigue siendo **MongoDB Atlas**, la misma que se usa en local. Por eso la carta, las
fotos, los usuarios y los pedidos aparecen igual en Render.

## Cómo queda en Render

| Servicio en Render | Módulo | Función |
|---|---|---|
| `wokcontrol-exito-config` | config-server | Configuración centralizada (protegida con usuario y contraseña) |
| `wokcontrol-exito-eureka` | discovery-service | Registro de servicios (protegido con usuario y contraseña) |
| `wokcontrol-exito-usuarios` … `-reportes` | 7 servicios de negocio | Cada uno con su base de datos en Atlas |
| `wokcontrol-exito-gateway` | api-gateway | **Entrada de la aplicación**: `https://wokcontrol-exito-gateway.onrender.com` |

El plan gratuito de Render no tiene red privada entre servicios. Por eso cada microservicio se registra
en Eureka con su URL pública (`https://…onrender.com`), y el gateway lo llama por ahí. Eso lo activa el
perfil `render` ([`config/application-render.yml`](../config-server/src/main/resources/config/application-render.yml)).

## Paso a paso

### 1. Permitir que Render llegue a MongoDB Atlas

Render no tiene una IP fija en el plan gratuito. Haz esto en Atlas:

1. Entra a **Network Access** y pulsa **Add IP Address**.
2. Elige **Allow access from anywhere** (`0.0.0.0/0`) y confirma.

El acceso sigue protegido por el usuario y la contraseña de la base de datos.

### 2. Crear el grupo de secretos en Render

Los secretos no van en el repositorio. En el panel de Render:

1. Abre **Environment Groups** y pulsa **New Environment Group**.
2. Ponle el nombre exacto `wok-secretos` y agrega estas tres variables, copiando cada valor de tu archivo `.env` local:

| Clave | Valor |
|---|---|
| `MONGODB_URI` | La misma de tu `.env` |
| `JWT_SECRET` | La misma de tu `.env` |
| `WOK_INFRA_PASSWORD` | La misma de tu `.env` (solo letras y números) |

3. Pulsa **Create Environment Group**.

### 3. Crear los 10 servicios con el Blueprint

1. En Render pulsa **New** y luego **Blueprint**.
2. Conecta GitHub, si aún no lo has hecho, y elige el repositorio `wok-control`.
3. Render lee `render.yaml` y muestra los 10 servicios. Pulsa **Apply** (o **Deploy Blueprint**).
4. Espera a que los 10 queden en estado **Live**. En el plan gratuito cada uno tarda varios minutos
   en compilar, y el conjunto puede tardar entre 20 y 40 minutos. Los servicios de negocio esperan
   solos a que el config-server responda antes de arrancar.

### 4. Comprobar las URL

Render usa el nombre del servicio como URL: `https://<nombre>.onrender.com`. Si algún nombre ya estaba
ocupado en Render, la URL tendrá un sufijo extra, por ejemplo `wokcontrol-exito-config-a1b2.onrender.com`.
En ese caso:

- Si cambió la URL de **config**: corrige `CONFIG_SERVER_URL` en los servicios de negocio y en el gateway.
- Si cambió la URL de **eureka**: corrige `EUREKA_HOST` en esos mismos servicios (solo el dominio, sin `https://`).
- Si cambió la URL del **gateway**: corrige `WOK_CORS_ORIGINS` en el gateway.

Lo más cómodo es editar los valores en `render.yaml` y subir el cambio a GitHub. Render vuelve a sincronizar.

### 5. Usar la aplicación

- **Aplicación**: `https://wokcontrol-exito-gateway.onrender.com`. Los usuarios y contraseñas son los mismos que en local.
- **Swagger**: `https://wokcontrol-exito-gateway.onrender.com/swagger-ui.html`.
- **Panel de Eureka**: `https://wokcontrol-exito-eureka.onrender.com`. Pide usuario `wok` y la contraseña `WOK_INFRA_PASSWORD`.

## Plan gratuito: lo que hay que saber

- **Se duerme.** Cada servicio se duerme tras 15 minutos sin tráfico y tarda alrededor de un minuto en
  despertar. Antes de una presentación ejecuta:

  ```powershell
  python scripts\despertar-render.py
  ```

  El script despierta los 10 servicios a la vez y avisa cuando todos responden.
- **Horas limitadas.** Render da 750 horas gratuitas al mes para toda la cuenta, y solo se gastan
  mientras los servicios están despiertos. Con 10 servicios equivalen a unas 75 horas de uso del sistema completo al mes.
- **Poca CPU.** Cada servicio tiene 0,1 CPU y 512 MB. Medido con las mismas opciones de Java del
  `Dockerfile`, un servicio ocupa unos 230 MB al arrancar. La primera carga de cada pantalla puede ser lenta.
- **Compilaciones selectivas.** Cada `git push` a `main` solo recompila los servicios cuyos archivos
  cambiaron (`buildFilter` en `render.yaml`).

Para usarlo de forma continua sin que se duerma, cambia cada servicio a un plan pago desde el panel
de Render (**Settings → Instance Type**). No hace falta cambiar código.

## Seguridad en Render

- **Secretos**: los secretos solo están en el grupo `wok-secretos` de Render y en tu `.env` local, nunca en Git.
- **Infraestructura**: Eureka y config-server exigen usuario y contraseña con el perfil `render`. Solo
  su health check queda público, porque Render lo necesita. Sin esa protección, cualquiera podría
  registrar un servicio falso en Eureka y recibir tráfico de los usuarios.
- **Cookie de sesión**: con el perfil `render` la cookie se marca como `Secure`, porque Render siempre usa HTTPS.
- **Microservicios con URL pública**: cada microservicio queda accesible directamente, sin pasar por el
  gateway, pero todos validan el JWT por su cuenta. Los endpoints internos además exigen el permiso `SISTEMA`.
