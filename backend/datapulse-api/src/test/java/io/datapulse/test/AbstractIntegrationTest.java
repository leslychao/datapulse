package io.datapulse.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import io.datapulse.test.config.IntegrationTestInfraConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.transaction.Transactional;

@SpringBootTest
@Testcontainers
@Transactional
@ActiveProfiles("integration-test")
@Import(IntegrationTestInfraConfig.class)
public abstract class AbstractIntegrationTest {

  @SuppressWarnings("resource")
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("datapulse_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(true);

  static {
    POSTGRES.start();
    createSchema();
  }

  private static void createSchema() {
    try (Connection conn = DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement stmt = conn.createStatement()) {
      stmt.execute("CREATE SCHEMA IF NOT EXISTS datapulse");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to create test schema", e);
    }
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url",
        () -> POSTGRES.getJdbcUrl() + "&currentSchema=datapulse");
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "datapulse");
    registry.add("spring.liquibase.default-schema", () -> "datapulse");
  }
}
