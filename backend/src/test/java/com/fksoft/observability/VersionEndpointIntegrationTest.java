package com.fksoft.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.VersionResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for {@code GET /api/version} (SPEC-0027 AC1, BR2/BR4): the endpoint is public
 * (no token), always returns 200 with a fully-populated payload, and the reported version matches
 * the SemVer the build filtered from the pom into {@code app.version} (ADR 0015). Whether or not the
 * packaged build-info / git.properties is present, every field is populated — never blank, never a
 * failure — which is exactly BR4's graceful degradation (absent fields fall back to {@code
 * "unknown"}).
 */
class VersionEndpointIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Value("${app.version}")
  private String configuredVersion;

  @Test
  void versionIsPublicAndReportsTheConfiguredSemVer() {
    ResponseEntity<VersionResponse> response =
        restTemplate.getForEntity("/api/version", VersionResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    VersionResponse body = response.getBody();
    assertThat(body).isNotNull();
    // version comes from the pom-filtered app.version (BuildProperties absent in the test runtime).
    assertThat(body.version()).isEqualTo(configuredVersion);
    // BR4: every field is populated (never null/blank) — absent build-info/git degrades to a stable
    // marker, never a failure.
    assertThat(body.gitCommit()).isNotBlank();
    assertThat(body.buildTime()).isNotBlank();
  }
}
