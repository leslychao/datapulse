package io.datapulse.test;

import org.springframework.boot.test.context.SpringBootTest;
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
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "datapulse");
    registry.add("spring.liquibase.default-schema", () -> "datapulse");
  }
}
