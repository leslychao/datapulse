FROM maven:3.9-eclipse-temurin-21 AS plugin-builder
WORKDIR /build
RUN git clone --depth 1 --branch v26.1.1 \
    https://github.com/mesutpiskin/keycloak-2fa-email-authenticator.git .
RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.0 AS builder
COPY --from=plugin-builder /build/target/*.jar /opt/keycloak/providers/
RUN /opt/keycloak/bin/kc.sh build --db=postgres --health-enabled=true --http-relative-path=/auth

FROM quay.io/keycloak/keycloak:26.0
COPY --from=builder /opt/keycloak/ /opt/keycloak/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
