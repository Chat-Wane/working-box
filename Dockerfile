FROM maven AS builder
ARG https_host
ARG https_port

WORKDIR /tmp

COPY pom.xml .
RUN mvn -Dhttps.proxyHost=${https_host} -Dhttps.proxyPort=${https_port} dependency:go-offline
COPY src ./src
RUN mvn  -Dhttps.proxyHost=${https_host} -Dhttps.proxyPort=${https_port} clean package


FROM adoptopenjdk/openjdk12:alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /tmp/target/working-box-1.0-SNAPSHOT.jar /app/working-box.jar
CMD ["java", "-jar", "/app/working-box.jar"]
