FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
