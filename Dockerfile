# Stage 1: Build file JAR bằng Gradle
FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
# Thực hiện build project, bỏ qua chạy test để tiết kiệm thời gian trên Render
RUN ./gradlew clean build -x test

# Stage 2: Chạy ứng dụng bằng JRE 17 gọn nhẹ
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy file .jar đã build từ Stage 1 sang Stage 2
COPY --from=build /app/build/libs/*.jar app.jar
# Mở port 8080 (port mặc định của Spring Boot)
EXPOSE 8080
# Lệnh chạy app
ENTRYPOINT ["java", "-jar", "app.jar"]