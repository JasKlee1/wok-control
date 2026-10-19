# Imagen genérica para cualquier módulo de Wok Control.
# Requiere compilar antes:  ./mvnw package -DskipTests
# Uso: docker build --build-arg MODULO=menu-service -t wok/menu-service .
FROM eclipse-temurin:21-jre-alpine

ARG MODULO
RUN addgroup -S wok && adduser -S wok -G wok
WORKDIR /app
COPY ${MODULO}/target/${MODULO}-1.0.0-SNAPSHOT.jar /app/app.jar
USER wok
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
