package com.fksoft.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.security.TestJwtTokens;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end proof that business metrics are derived from already-published domain events
 * (SPEC-0027 AC7/AC8, BR6/BR7, DL-0098). A user's first authenticated touch ({@code GET /me})
 * publishes {@code UserAuthenticated}, which the infra {@link
 * com.fksoft.infra.observability.BusinessMetrics} consumer turns into the Micrometer counter {@code
 * acme.identity.logins}; the test then scrapes {@code /actuator/prometheus} (as ROLE_IT) and
 * asserts the exported series {@code acme_identity_logins_total} is present, carries the common
 * {@code application} tag (AC8) and reflects the login. {@code @AutoConfigureObservability}
 * re-enables metrics export under {@code @SpringBootTest}.
 */
@AutoConfigureObservability
class BusinessMetricsIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  @Test
  void aLoginIncrementsTheBusinessCounterExposedInPrometheus() {
    // Trigger the business fact: the first authenticated touch (/me) publishes UserAuthenticated.
    String itToken = TestJwtTokens.mint("it", "ROLE_IT");
    restTemplate.exchange(
        "/api/identity/me", HttpMethod.GET, new HttpEntity<>(bearer(itToken)), String.class);

    // Scrape the Prometheus exposition as ROLE_IT and read the business counter.
    String scrape =
        restTemplate
            .exchange(
                "/actuator/prometheus",
                HttpMethod.GET,
                new HttpEntity<>(bearer(itToken)),
                String.class)
            .getBody();

    assertThat(scrape).isNotNull();
    // The business series exists, is tagged with the common application tag (AC8) and is >= 1
    // (AC7).
    double logins = sumSeries(scrape, "acme_identity_logins_total");
    assertThat(logins).isGreaterThanOrEqualTo(1.0);
    assertThat(scrape).contains("acme_identity_logins_total");
    assertThat(scrape).contains("application=\"acme-travel-erp\"");
  }

  /** Sums every {@code <name>{...} <value>} sample line of the given metric in the scrape body. */
  private static double sumSeries(String scrape, String metric) {
    double total = 0;
    for (String line : scrape.split("\\R")) {
      if (line.startsWith(metric) && !line.startsWith("# ")) {
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace > 0) {
          total += Double.parseDouble(line.substring(lastSpace + 1).trim());
        }
      }
    }
    return total;
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
