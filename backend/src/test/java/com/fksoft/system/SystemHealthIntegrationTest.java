package com.fksoft.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.SystemHealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end proof of the stack (SPEC-0001 acceptance): HTTP -> controller -> infra probe -> real
 * Postgres -> response. With the database reachable, {@code GET /api/system/health} returns 200 and
 * {@code db: UP}.
 */
class SystemHealthIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void healthReturnsUpWhenDatabaseIsReachable() {
    ResponseEntity<SystemHealthResponse> response =
        restTemplate.getForEntity("/api/system/health", SystemHealthResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("UP");
    assertThat(response.getBody().db()).isEqualTo("UP");
  }
}
