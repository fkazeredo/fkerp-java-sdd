package com.fksoft.commercialpolicy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.IssueDirectiveRequest;
import com.fksoft.application.api.dto.ParameterRuleResponse;
import com.fksoft.application.api.dto.ResolvedParameterResponse;
import com.fksoft.domain.commercialpolicy.CommercialPolicyService;
import com.fksoft.domain.commercialpolicy.DefineRuleCommand;
import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ParameterValueTypeCodes;
import com.fksoft.domain.commercialpolicy.PolicyDirectiveForbiddenException;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for CommercialPolicy (SPEC-0014) against real Postgres: the seeded
 * SYSTEM_DEFAULT resolves with provenance, a directive immediately wins for its scope and is
 * audited, a key without a SYSTEM_DEFAULT yields 404, and the directive authorization (BR5/BR7,
 * DL-0038) rejects a roleless actor at the service boundary.
 */
class CommercialPolicyIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private CommercialPolicyService policyService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    // Keep only the seeded SYSTEM_DEFAULT rows; drop anything a test created.
    jdbcTemplate.execute("DELETE FROM parameter_rules WHERE defined_by <> 'system-seed'");
  }

  @Test
  void resolvesTheSeededSystemDefaultWithProvenance() {
    ResponseEntity<ResolvedParameterResponse> response =
        restTemplate.getForEntity(
            "/api/commercial-policy/resolve?key=MARKUP_PCT", ResolvedParameterResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ResolvedParameterResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.key()).isEqualTo("MARKUP_PCT");
    assertThat(body.value()).isEqualTo("0");
    assertThat(body.type()).isEqualTo("PERCENT");
    assertThat(body.provenance().layer()).isEqualTo("SYSTEM_DEFAULT");
    assertThat(body.provenance().definedBy()).isEqualTo("system-seed");
    assertThat(body.provenance().ruleId()).isNotNull();
  }

  @Test
  void aDirectiveImmediatelyWinsForItsScopeAndIsAudited() {
    UUID account = UUID.randomUUID();

    // Before the directive: the account resolves to the global SYSTEM_DEFAULT.
    ResolvedParameterResponse before =
        restTemplate
            .getForEntity(
                "/api/commercial-policy/resolve?key=MARKUP_PCT&accountId=" + account,
                ResolvedParameterResponse.class)
            .getBody();
    assertThat(before).isNotNull();
    assertThat(before.provenance().layer()).isEqualTo("SYSTEM_DEFAULT");

    // Issue a directive for that account.
    ResponseEntity<ParameterRuleResponse> created =
        restTemplate.postForEntity(
            "/api/commercial-policy/directives",
            new IssueDirectiveRequest(
                "MARKUP_PCT",
                "0.08",
                ParameterValueTypeCodes.PERCENT,
                account,
                null,
                null,
                LocalDate.of(2026, 6, 26),
                null,
                "fechar cliente estratégico"),
            ParameterRuleResponse.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().layer()).isEqualTo("DIRECTIVE");
    assertThat(created.getBody().justification()).isEqualTo("fechar cliente estratégico");
    assertThat(created.getBody().definedBy()).isNotBlank();

    // After: the same account now resolves to the directive — immediately (no cache).
    ResolvedParameterResponse after =
        restTemplate
            .getForEntity(
                "/api/commercial-policy/resolve?key=MARKUP_PCT&accountId=" + account,
                ResolvedParameterResponse.class)
            .getBody();
    assertThat(after).isNotNull();
    assertThat(after.value()).isEqualTo("0.08");
    assertThat(after.provenance().layer()).isEqualTo("DIRECTIVE");

    // A different account is unaffected (scope matcher).
    ResolvedParameterResponse otherAccount =
        restTemplate
            .getForEntity(
                "/api/commercial-policy/resolve?key=MARKUP_PCT&accountId=" + UUID.randomUUID(),
                ResolvedParameterResponse.class)
            .getBody();
    assertThat(otherAccount).isNotNull();
    assertThat(otherAccount.provenance().layer()).isEqualTo("SYSTEM_DEFAULT");
  }

  @Test
  void returns404ForAKeyWithoutASystemDefault() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/commercial-policy/resolve?key=UNGOVERNED_KEY", ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("policy.parameter.unknown");
  }

  @Test
  void issuingADirectiveWithoutTheDirectorRoleIsForbidden() {
    // The directive authorization is enforced in the domain service (the backend is the authority).
    DefineRuleCommand directive =
        new DefineRuleCommand(
            ParameterKey.MARKUP_PCT,
            ParameterLayer.DIRECTIVE,
            ParameterScope.global(),
            "0.05",
            ParameterValueTypeCodes.PERCENT,
            LocalDate.of(2026, 6, 1),
            null,
            "sem papel");

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> policyService.defineRule(directive, Set.of("ROLE_DEV"), "intruder"))
        .isInstanceOf(PolicyDirectiveForbiddenException.class);
  }
}
