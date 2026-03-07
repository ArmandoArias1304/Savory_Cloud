FROM eclipse-temurin:17-jdk-alpine
ARG JAR_FILE=target/elgransazon-0.0.1.jar
COPY ${JAR_FILE} elgransazon.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "elgransazon.jar"]