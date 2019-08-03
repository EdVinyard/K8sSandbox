FROM openjdk:11-jre-slim
COPY target/service-that-logs-0.1.0.jar .
EXPOSE 8080/tcp
ENTRYPOINT ["java","-jar","service-that-logs-0.1.0.jar"]
