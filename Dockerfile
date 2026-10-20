# Imagen de cualquier microservicio. MODULO = carpeta del módulo (config-server, menu-service, ...).
# En Render basta con definir la variable de entorno MODULO: Render la pasa como argumento del build.

# Etapa 1: compilar el microservicio y sus dependencias Maven
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY . .

ARG MODULO

RUN test -n "$MODULO" \
    && chmod +x mvnw \
    && ./mvnw -B -pl "$MODULO" -am -DskipTests package

# Etapa 2: ejecutar solamente el JAR generado
FROM eclipse-temurin:21-jre-alpine

ARG MODULO

RUN apk add --no-cache curl && addgroup -S wok && adduser -S wok -G wok

WORKDIR /app

COPY --from=build /workspace/${MODULO}/target/${MODULO}-1.0.0-SNAPSHOT.jar /app/app.jar

USER wok

# Ajustado para contenedores de 512 MB (plan gratuito de Render): montón al 55 %, recolector serial,
# pilas de hilo pequeñas y compilación C1, que arranca más rápido con poca CPU.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/urandom"

# Si el servicio depende del config-server (CONFIG_SERVER_URL), espera hasta 10 minutos a que
# responda antes de arrancar: en Render los 10 servicios se despliegan a la vez y el config-server
# puede estar dormido o arrancando todavía.
ENTRYPOINT ["sh", "-c", "if [ -n \"$CONFIG_SERVER_URL\" ]; then i=0; until curl -sf -m 60 \"$CONFIG_SERVER_URL/actuator/health\" | grep -q UP || [ $i -ge 60 ]; do i=$((i+1)); echo \"Esperando config-server ($i)...\"; sleep 10; done; fi; exec java -jar /app/app.jar"]
