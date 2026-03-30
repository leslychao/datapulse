FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
COPY datapulse-common/pom.xml datapulse-common/
COPY datapulse-platform/pom.xml datapulse-platform/
COPY datapulse-tenancy-iam/pom.xml datapulse-tenancy-iam/
COPY datapulse-integration/pom.xml datapulse-integration/
COPY datapulse-etl/pom.xml datapulse-etl/
COPY datapulse-analytics-pnl/pom.xml datapulse-analytics-pnl/
COPY datapulse-pricing/pom.xml datapulse-pricing/
COPY datapulse-execution/pom.xml datapulse-execution/
COPY datapulse-promotions/pom.xml datapulse-promotions/
COPY datapulse-seller-operations/pom.xml datapulse-seller-operations/
COPY datapulse-audit-alerting/pom.xml datapulse-audit-alerting/
COPY datapulse-api/pom.xml datapulse-api/
RUN mvn dependency:go-offline -B
COPY . .
RUN mvn clean package -DskipTests -pl datapulse-api -am

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/datapulse-api/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
