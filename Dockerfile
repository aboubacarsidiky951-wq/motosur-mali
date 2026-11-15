# Étape 1 : compilation (Maven télécharge les dépendances sur Render, qui a un accès Internet normal)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Étape 2 : image d'exécution légère, sans droits root
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 motosur
WORKDIR /app
COPY --from=build /app/target/motosur-*.jar app.jar
USER motosur
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70" SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
