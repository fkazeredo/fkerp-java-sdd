package com.fksoft.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.platform.JobRunView;
import com.fksoft.domain.platform.ScheduledJobView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * REST API journey for the Platform job governance (SPEC-0023) slice 8j-2: the seeded catalog is
 * listed, a manual trigger runs the job under governance (202 with the recorded run), an unknown
 * job is 404, and the run history is readable/filterable. Runs against a real Postgres
 * (Testcontainers).
 */
class PlatformJobApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM job_runs");
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  @Test
  void theSeededCatalogIsListed() {
    ResponseEntity<List<ScheduledJobView>> jobs =
        restTemplate.exchange(
            "/api/platform/jobs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<ScheduledJobView>>() {});

    assertThat(jobs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(jobs.getBody())
        .extracting(ScheduledJobView::name)
        .contains(
            "point-clock-crawl",
            "aftersales-sla-sweep",
            "asset-license-expiry",
            "representation-expiry",
            "retention-expiry",
            "certificate-expiry");
  }

  @Test
  void triggeringAKnownJobRunsItUnderGovernanceAndReturns202() {
    ResponseEntity<JobRunView> response =
        restTemplate.postForEntity(
            "/api/platform/jobs/certificate-expiry/trigger", null, JobRunView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().job()).isEqualTo("certificate-expiry");

    // The run is now in the history.
    ResponseEntity<String> runs =
        restTemplate.getForEntity("/api/platform/jobs/runs?job=certificate-expiry", String.class);
    assertThat(runs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(runs.getBody()).contains("certificate-expiry");
  }

  @Test
  void triggeringAnUnknownJobIs404() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/platform/jobs/no-such-job/trigger", null, ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("platform.job.not-found");
  }
}
