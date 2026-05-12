# Stage 1: Build
FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew clean build -x test

# Stage 2: Run
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# TẠO THƯ MỤC UPLOAD TRONG LINUX
RUN mkdir -p /app/upload

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]