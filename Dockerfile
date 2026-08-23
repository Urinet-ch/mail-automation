# Build erfolgt in CI (mvn package), hier wird nur die fast-jar verpackt.
FROM eclipse-temurin:21-jre-alpine

ENV LANG=C.UTF-8 TZ=Europe/Zurich

WORKDIR /app
COPY target/quarkus-app/lib/ /app/lib/
COPY target/quarkus-app/*.jar /app/
COPY target/quarkus-app/app/ /app/app/
COPY target/quarkus-app/quarkus/ /app/quarkus/

RUN addgroup -S app && adduser -S app -G app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
