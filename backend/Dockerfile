# CaseSim Backend - Build multi-etapa
# ============================================
# Stage 1: compilación con Maven + JDK 21
# ============================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copiar Maven wrapper y configuración
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Hacer ejecutable el wrapper
RUN chmod +x mvnw

# Descargar dependencias (cache layer)
RUN ./mvnw dependency:go-offline -DskipTests -q || true

# Copiar código fuente
COPY src src

# Compilar y empaquetar (sin tests)
RUN ./mvnw clean package -DskipTests -q

# ============================================
# Stage 2: imagen final con JRE 21
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Crear usuario no root por seguridad
RUN addgroup -S casesim && adduser -S casesim -G casesim

# Copiar JAR desde stage de build
COPY --from=build /app/target/*.jar app.jar

# Permisos
RUN chown -R casesim:casesim /app

USER casesim

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
