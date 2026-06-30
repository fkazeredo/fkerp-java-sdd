package com.fksoft.system;

import com.fksoft.security.TestSecurityConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
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
 *
 * <p>Runs under the {@code test} profile with {@link TestSecurityConfig} (SPEC-0024/DL-0081): the
 * real Spring Security chain is mounted, but unauthenticated requests are authenticated as a
 * full-access test actor, so the existing suite stays green without weakening the security layer.
 * Security tests that send a token exercise the genuine 401/403 paths.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public abstract class AbstractPostgresIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }
}
