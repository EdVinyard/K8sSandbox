FROM adoptopenjdk/openjdk11:alpine-jre
COPY target/service-that-logs-0.1.0.jar .
EXPOSE 8080/tcp
ENTRYPOINT ["java","-jar","service-that-logs-0.1.0.jar"]
