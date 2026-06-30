package com.fksoft.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fksoft.application.api.dto.LoginRequest;
import com.fksoft.application.api.dto.LoginResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hygiene regression for the structured (JSON) logs (SPEC-0027 BR5, DL-0096): the most sensitive
 * path — login — must never log the raw password (or the issued token). The JSON serialization
 * (logstash/ECS) writes whatever the application logs, so the guarantee is that the application
 * logs no secret in the first place; this test captures the log events around a real login and
 * asserts the submitted password and the issued token appear in none of them.
 *
 * <p>Captures via a Logback {@link ListAppender} attached to the root logger, so it sees every log
 * event the login produces (identity service, security, audit) regardless of which logger emits it.
 */
@DisabledInNativeImage
class SensitiveDataNotLoggedIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String DEV_PASSWORD = "dev12345";

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
  void loginNeverLogsThePasswordOrTheToken() {
    LoginResponse body =
        restTemplate
            .postForEntity(
                "/api/identity/login", new LoginRequest("it", DEV_PASSWORD), LoginResponse.class)
            .getBody();
    assertThat(body).isNotNull();
    String token = body.token();
    assertThat(token).isNotBlank();

    // Every captured log line (formatted message + MDC) must contain neither the raw password nor
    // the issued JWT — BR5: no secret in the logs, JSON or not.
    for (ILoggingEvent event : appender.list) {
      String rendered = event.getFormattedMessage() + " " + event.getMDCPropertyMap();
      assertThat(rendered).doesNotContain(DEV_PASSWORD);
      assertThat(rendered).doesNotContain(token);
    }
    // Sanity: the login did produce a log line (the audit/identity SUCCESS line), so the assertion
    // above actually inspected real output rather than an empty list.
    assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().contains("result=SUCCESS"));
  }
}
