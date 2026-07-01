package com.fksoft.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.marketing.ConsentNotFoundException;
import com.fksoft.domain.marketing.ConsentStatus;
import com.fksoft.domain.marketing.ConsentView;
import com.fksoft.domain.marketing.GrantConsentCommand;
import com.fksoft.domain.marketing.LegalBasis;
import com.fksoft.domain.marketing.MarketingCodes;
import com.fksoft.domain.marketing.MarketingService;
import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the Consent append-only log (SPEC-0019 BR1; V24; DL-0056): granting and
 * revoking append immutable rows, the current state is the latest row per subject+purpose, the
 * history is preserved, and re-consent after revoke resolves back to GRANTED. Drives the {@link
 * MarketingService} facade against a real Postgres (Testcontainers), proving the schema and the
 * latest-row projection.
 */
class ConsentApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MarketingService marketingService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final SubjectRef ACME = new SubjectRef("acc-1", MarketingCodes.ACCOUNT);

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM consents");
  }

  @Test
  void grantingAppendsAGrantedRowAndCurrentStateIsGranted() {
    ConsentView granted =
        marketingService.grantConsent(
            new GrantConsentCommand(
                ACME, MarketingCodes.NEWSLETTER, LegalBasis.CONSENT, "signup-form"),
            "agent");

    assertThat(granted.status()).isEqualTo(ConsentStatus.GRANTED);
    assertThat(marketingService.currentState(ACME, MarketingCodes.NEWSLETTER).isGranted()).isTrue();

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM consents WHERE subject_id = ? AND purpose = ?",
            Integer.class,
            "acc-1",
            "NEWSLETTER");
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void revokingAppendsANewRowAndPreservesHistory() {
    ConsentView granted =
        marketingService.grantConsent(
            new GrantConsentCommand(ACME, MarketingCodes.NEWSLETTER, LegalBasis.CONSENT, "form"),
            "agent");

    ConsentView revoked = marketingService.revokeConsent(granted.id(), "agent");
    assertThat(revoked.status()).isEqualTo(ConsentStatus.REVOKED);
    assertThat(revoked.id()).isNotEqualTo(granted.id()); // a NEW row, not an update

    // Current state is now REVOKED (latest row), but the GRANTED row is preserved (append).
    assertThat(marketingService.currentState(ACME, MarketingCodes.NEWSLETTER).isGranted())
        .isFalse();
    List<ConsentView> history = marketingService.history(ACME, MarketingCodes.NEWSLETTER);
    assertThat(history).hasSize(2);
    assertThat(history.get(0).status()).isEqualTo(ConsentStatus.REVOKED); // newest first
    assertThat(history.get(1).status()).isEqualTo(ConsentStatus.GRANTED);
  }

  @Test
  void reConsentAfterRevokeResolvesBackToGranted() {
    ConsentView granted =
        marketingService.grantConsent(
            new GrantConsentCommand(ACME, MarketingCodes.NEWSLETTER, LegalBasis.CONSENT, "form"),
            "agent");
    marketingService.revokeConsent(granted.id(), "agent");

    marketingService.grantConsent(
        new GrantConsentCommand(ACME, MarketingCodes.NEWSLETTER, LegalBasis.CONSENT, "form-2"),
        "agent");

    assertThat(marketingService.currentState(ACME, MarketingCodes.NEWSLETTER).isGranted()).isTrue();
    assertThat(marketingService.history(ACME, MarketingCodes.NEWSLETTER)).hasSize(3);
  }

  @Test
  void unknownSubjectIsNotConsented() {
    SubjectRef unknown = new SubjectRef("never-seen", MarketingCodes.AGENT);
    assertThat(marketingService.currentState(unknown, MarketingCodes.NEWSLETTER).isGranted())
        .isFalse();
  }

  @Test
  void revokingAMissingConsentIsNotFound() {
    assertThatThrownBy(() -> marketingService.revokeConsent(UUID.randomUUID(), "agent"))
        .isInstanceOf(ConsentNotFoundException.class);
  }
}
