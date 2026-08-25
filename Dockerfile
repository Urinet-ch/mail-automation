# Build erfolgt in CI (mvn package), hier wird nur die fast-jar verpackt.
FROM eclipse-temurin:21-jre-alpine

ENV LANG=C.UTF-8 TZ=Europe/Zurich

WORKDIR /app
COPY target/quarkus-app/lib/ /app/lib/
COPY target/quarkus-app/*.jar /app/
COPY target/quarkus-app/app/ /app/app/
COPY target/quarkus-app/quarkus/ /app/quarkus/

# Explizite numerische UID, nicht nur ein Name: Kubernetes kann mit
# runAsNonRoot nur pruefen, ob der User kein root ist, wenn USER eine Zahl ist -
# sonst verweigert das kubelet den Start ("image has non-numeric user").
# Ausserdem vergibt "adduser -S" ohne -u die naechste freie System-ID (hier 100);
# die kann sich beim Wechsel des Base-Image still verschieben.
RUN addgroup -g 1001 -S app && adduser -u 1001 -S app -G app
USER 1001

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
