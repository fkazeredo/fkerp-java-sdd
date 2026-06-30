package com.fksoft.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fksoft.security.TestJwtTokens;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hygiene regression for the structured (JSON) logs (SPEC-0027 BR5, DL-0096): the authenticated
 * identity path must never log the bearer token. Since Phase 13 the login itself happens at the
 * external IdP (the ERP never sees a password), so the secret to protect here is the bearer token
 * carried on the first authenticated touch ({@code GET /me}). The JSON serialization (logstash/ECS)
 * writes whatever the application logs, so the guarantee is that the application logs no secret in
 * the first place; this test captures the log events around a real authenticated call and asserts
 * the token appears in none of them.
 *
 * <p>Captures via a Logback {@link ListAppender} attached to the root logger, so it sees every log
 * event the call produces (identity service, security, audit) regardless of which logger emits it.
 */
@DisabledInNativeImage
class SensitiveDataNotLoggedIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ListAppender<ILoggingEvent> appender;
  private Logger rootLogger;

  @BeforeEach
  void attachAppender() {
    rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    appender = new ListAppender<>();
    appender.start();
    rootLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    if (rootLogger != null && appender != null) {
      rootLogger.detachAppender(appender);
    }
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  @Test
  void theAuthenticatedPathNeverLogsTheToken() {
    String token = TestJwtTokens.mint("it", "ROLE_IT");
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    restTemplate.exchange(
        "/api/identity/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

    // Every captured log line (formatted message + MDC) must not contain the bearer token — BR5: no
    // secret in the logs, JSON or not.
    for (ILoggingEvent event : appender.list) {
      String rendered = event.getFormattedMessage() + " " + event.getMDCPropertyMap();
      assertThat(rendered).doesNotContain(token);
    }
    // Sanity: the call did produce the identity SUCCESS log line, so the assertion above actually
    // inspected real output rather than an empty list.
    assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().contains("result=SUCCESS"));
  }
}
