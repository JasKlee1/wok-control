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

RUN addgroup -S wok && adduser -S wok -G wok

WORKDIR /app

COPY --from=build /workspace/${MODULO}/target/${MODULO}-1.0.0-SNAPSHOT.jar /app/app.jar

USER wok

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
