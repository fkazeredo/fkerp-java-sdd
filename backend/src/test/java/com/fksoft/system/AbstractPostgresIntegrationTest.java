package com.fksoft.system;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests that need a real Postgres (Testcontainers). Uses the <strong>singleton
 * container</strong> pattern: the container is started once in a static initializer and kept up for
 * the whole JVM (reaped by Testcontainers' Ryuk at exit), so it is safely shared across every
 * integration-test class even when Spring reuses a cached application context. It is wired into
 * Spring Boot via {@code @ServiceConnection}, so Flyway runs the real migrations against a real
 * database — proving the persistence seam end to end.
 *
 * <p>Deliberately <em>not</em> using {@code @Testcontainers}/{@code @Container}: that stops the
 * container after the first test class, which then breaks a second class reusing the cached context
 * ("connection has been closed").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractPostgresIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }
}
