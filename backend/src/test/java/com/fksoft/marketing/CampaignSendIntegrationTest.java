package com.fksoft.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.marketing.CampaignSendResult;
import com.fksoft.domain.marketing.CampaignView;
import com.fksoft.domain.marketing.CreateCampaignCommand;
import com.fksoft.domain.marketing.DefineSegmentCommand;
import com.fksoft.domain.marketing.GrantConsentCommand;
import com.fksoft.domain.marketing.LegalBasis;
import com.fksoft.domain.marketing.MarketingCodes;
import com.fksoft.domain.marketing.MarketingService;
import com.fksoft.domain.marketing.SegmentView;
import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the campaign dispatch (SPEC-0019 BR2/BR4; DL-0055/DL-0059): a campaign
 * sends <strong>only</strong> to subjects with a current GRANTED consent — the rest are suppressed
 * and counted (BR2); a re-issued send never double-mails the same recipient (BR4 idempotency); and
 * the LGPD regression: revoking consent excludes the subject from the next dispatch. Drives the
 * {@link MarketingService} facade against a real Postgres and the traceable newsletter mock.
 */
class CampaignSendIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MarketingService marketingService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM campaign_sends");
    jdbcTemplate.execute("DELETE FROM campaigns");
    jdbcTemplate.execute("DELETE FROM segments");
    jdbcTemplate.execute("DELETE FROM consents");
  }

  private CampaignView newCampaign(String code) {
    SegmentView segment =
        marketingService.defineSegment(
            new DefineSegmentCommand("Agencies SE", Map.of("accountType", "AGENCY")), "mkt");
    return marketingService.createCampaign(
        new CreateCampaignCommand(segment.id(), code, "content-1", null, null), "mkt");
  }

  private void grant(String id) {
    marketingService.grantConsent(
        new GrantConsentCommand(
            new SubjectRef(id, MarketingCodes.ACCOUNT),
            MarketingCodes.NEWSLETTER,
            LegalBasis.CONSENT,
            "form"),
        "mkt");
  }

  @Test
  void sendsOnlyToConsentedSubjectsAndCountsSuppressed() {
    grant("acc-1");
    grant("acc-2");
    // acc-3 has a consent row but it is revoked → must be suppressed.
    grant("acc-3");
    var granted3 =
        marketingService
            .history(new SubjectRef("acc-3", MarketingCodes.ACCOUNT), MarketingCodes.NEWSLETTER)
            .get(0);
    marketingService.revokeConsent(granted3.id(), "mkt");

    CampaignView campaign = newCampaign("CAMP-SE-1");
    CampaignSendResult result = marketingService.sendCampaign(campaign.id(), "mkt");

    assertThat(result.targeted()).isEqualTo(3); // 3 subjects with a consent row for NEWSLETTER
    assertThat(result.suppressedNoConsent()).isEqualTo(1); // acc-3 (revoked)
    assertThat(result.queued()).isEqualTo(2); // acc-1, acc-2

    Integer sends =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM campaign_sends WHERE campaign_id = ?",
            Integer.class,
            campaign.id());
    assertThat(sends).isEqualTo(2);
  }

  @Test
  void reSendingDoesNotDoubleMailTheSameRecipient() {
    grant("acc-1");
    grant("acc-2");
    CampaignView campaign = newCampaign("CAMP-SE-2");

    CampaignSendResult first = marketingService.sendCampaign(campaign.id(), "mkt");
    assertThat(first.queued()).isEqualTo(2);

    // Re-issue: already-sent recipients are skipped (BR4 idempotency).
    CampaignSendResult second = marketingService.sendCampaign(campaign.id(), "mkt");
    assertThat(second.targeted()).isEqualTo(2);
    assertThat(second.queued()).isZero();

    Integer sends =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM campaign_sends WHERE campaign_id = ?",
            Integer.class,
            campaign.id());
    assertThat(sends).isEqualTo(2); // still 2, not 4
  }

  @Test
  void revokingConsentExcludesTheSubjectFromTheNextDispatch() {
    // LGPD regression (Tests Required): revoke → suppressed on the next send.
    grant("acc-1");
    grant("acc-2");
    CampaignView campaign = newCampaign("CAMP-SE-3");

    // Before revoke both are consented.
    var beforeState =
        marketingService
            .history(new SubjectRef("acc-2", MarketingCodes.ACCOUNT), MarketingCodes.NEWSLETTER)
            .get(0);
    marketingService.revokeConsent(beforeState.id(), "mkt");

    CampaignSendResult result = marketingService.sendCampaign(campaign.id(), "mkt");
    assertThat(result.targeted()).isEqualTo(2);
    assertThat(result.suppressedNoConsent()).isEqualTo(1); // acc-2 now revoked
    assertThat(result.queued()).isEqualTo(1); // only acc-1
  }

  @Test
  void previewCountsCurrentlyConsentedReachableSubjects() {
    grant("acc-1");
    grant("acc-2");
    SegmentView segment =
        marketingService.defineSegment(
            new DefineSegmentCommand("All", Map.of("purpose", "NEWSLETTER")), "mkt");

    long reach = marketingService.previewSegment(segment.id());
    assertThat(reach).isEqualTo(2);
  }

  @Test
  void sendingAMissingCampaignIsNotFound() {
    assertThatThrownBy(() -> marketingService.sendCampaign(java.util.UUID.randomUUID(), "mkt"))
        .isInstanceOf(com.fksoft.domain.marketing.CampaignNotFoundException.class);
  }
}
