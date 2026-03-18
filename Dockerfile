FROM eclipse-temurin:17-jdk-alpine
# Install mysql-client so mysqldump is available for database backups
RUN apk add --no-cache mysql-client
ARG JAR_FILE=target/elgransazon-0.0.1.jar
COPY ${JAR_FILE} elgransazon.jar
# Force container OS and JVM to UTC so all LocalDateTime.now() calls produce UTC timestamps
ENV TZ=UTC
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "elgransazon.jar"]