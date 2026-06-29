package com.fksoft.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.booking.BookingConfirmed;
import com.fksoft.domain.marketing.AttributionView;
import com.fksoft.domain.marketing.CampaignConverted;
import com.fksoft.domain.marketing.CampaignView;
import com.fksoft.domain.marketing.ConsentPurpose;
import com.fksoft.domain.marketing.ConsentStatus;
import com.fksoft.domain.marketing.CreateCampaignCommand;
import com.fksoft.domain.marketing.DefineSegmentCommand;
import com.fksoft.domain.marketing.ErasureResult;
import com.fksoft.domain.marketing.GrantConsentCommand;
import com.fksoft.domain.marketing.LegalBasis;
import com.fksoft.domain.marketing.MarketingService;
import com.fksoft.domain.marketing.RegisterAttributionCommand;
import com.fksoft.domain.marketing.SegmentView;
import com.fksoft.domain.marketing.SubjectRef;
import com.fksoft.domain.marketing.SubjectType;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for attribution and LGPD erasure (SPEC-0019 BR5/BR6; DL-0057/DL-0058): a
 * pre-registered campaign code on a confirmed booking is attributed and emits {@code
 * CampaignConverted} (the signal the Intelligence consumes, captured here via Spring's recorded
 * application events) — idempotently; a booking with no code does nothing; and erasure removes the
 * subject's marketing PII while preserving a revocation tombstone so the subject is suppressed from
 * the next dispatch but the {@code attributions} (no PII) remain.
 */
@RecordApplicationEvents
class AttributionAndErasureIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MarketingService marketingService;
  @Autowired private ApplicationEventPublisher publisher;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents applicationEvents;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM attributions");
    jdbcTemplate.execute("DELETE FROM campaign_sends");
    jdbcTemplate.execute("DELETE FROM campaigns");
    jdbcTemplate.execute("DELETE FROM segments");
    jdbcTemplate.execute("DELETE FROM consents");
  }

  private CampaignView campaign(String code) {
    SegmentView segment =
        marketingService.defineSegment(
            new DefineSegmentCommand("seg", Map.of("accountType", "AGENCY")), "mkt");
    return marketingService.createCampaign(
        new CreateCampaignCommand(segment.id(), code, null, null, null), "mkt");
  }

  @Test
  void confirmedBookingWithCodeIsAttributedAndEmitsCampaignConverted() {
    CampaignView campaign = campaign("UTM-ABC");
    UUID bookingId = UUID.randomUUID();
    marketingService.registerAttribution(
        new RegisterAttributionCommand("UTM-ABC", bookingId), "mkt");

    // Booking is confirmed → the Marketing consumer confirms the conversion and publishes the
    // event.
    publisher.publishEvent(
        new BookingConfirmed(bookingId, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

    List<AttributionView> attributions = marketingService.attributionsForCode("UTM-ABC");
    assertThat(attributions).hasSize(1);
    assertThat(attributions.get(0).converted()).isTrue();

    assertThat(applicationEvents.stream(CampaignConverted.class))
        .anyMatch(e -> e.campaignId().equals(campaign.id()) && e.bookingId().equals(bookingId));
  }

  @Test
  void confirmingTheSameBookingTwiceEmitsCampaignConvertedOnlyOnce() {
    campaign("UTM-IDEMP");
    UUID bookingId = UUID.randomUUID();
    marketingService.registerAttribution(
        new RegisterAttributionCommand("UTM-IDEMP", bookingId), "mkt");

    BookingConfirmed event =
        new BookingConfirmed(bookingId, UUID.randomUUID(), UUID.randomUUID(), Instant.now());
    publisher.publishEvent(event);
    publisher.publishEvent(event); // re-delivery

    assertThat(applicationEvents.stream(CampaignConverted.class))
        .filteredOn(e -> e.bookingId().equals(bookingId))
        .hasSize(1);
  }

  @Test
  void confirmedBookingWithoutACodeIsNotAttributed() {
    UUID bookingId = UUID.randomUUID();
    publisher.publishEvent(
        new BookingConfirmed(bookingId, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

    assertThat(applicationEvents.stream(CampaignConverted.class))
        .noneMatch(e -> e.bookingId().equals(bookingId));
    assertThat(marketingService.attributionsForCode("none")).isEmpty();
  }

  @Test
  void registeringTheSameAttributionTwiceIsIdempotent() {
    campaign("UTM-DUP");
    UUID bookingId = UUID.randomUUID();
    AttributionView first =
        marketingService.registerAttribution(
            new RegisterAttributionCommand("UTM-DUP", bookingId), "mkt");
    AttributionView second =
        marketingService.registerAttribution(
            new RegisterAttributionCommand("UTM-DUP", bookingId), "mkt");

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(marketingService.attributionsForCode("UTM-DUP")).hasSize(1);
  }

  @Test
  void erasureSuppressesTheSubjectButPreservesTombstoneAndAttributions() {
    SubjectRef subject = new SubjectRef("acc-erase", SubjectType.ACCOUNT);
    marketingService.grantConsent(
        new GrantConsentCommand(subject, ConsentPurpose.NEWSLETTER, LegalBasis.CONSENT, "form"),
        "mkt");
    // An attribution that carries NO subject PII must survive the erasure (business metric).
    UUID bookingId = UUID.randomUUID();
    marketingService.registerAttribution(
        new RegisterAttributionCommand("UTM-KEEP", bookingId), "mkt");

    ErasureResult result = marketingService.erase(subject, "dpo");
    assertThat(result.suppressed()).isTrue();
    assertThat(result.anonymizedConsents()).isGreaterThanOrEqualTo(1);

    // The subject is now suppressed (latest consent is REVOKED) — not granted anymore.
    assertThat(marketingService.currentState(subject, ConsentPurpose.NEWSLETTER).isGranted())
        .isFalse();

    // The original subject id no longer appears in the consents table (PII anonymized).
    Integer remaining =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM consents WHERE subject_id = ?", Integer.class, "acc-erase");
    assertThat(remaining).isZero();

    // A revocation tombstone exists (anonymized) so the subject stays suppressed.
    Integer revoked =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM consents WHERE status = ? AND subject_id LIKE 'anon-%'",
            Integer.class, ConsentStatus.REVOKED.name());
    assertThat(revoked).isGreaterThanOrEqualTo(1);

    // Attributions (no PII) are preserved.
    assertThat(marketingService.attributionsForCode("UTM-KEEP")).hasSize(1);
  }
}
