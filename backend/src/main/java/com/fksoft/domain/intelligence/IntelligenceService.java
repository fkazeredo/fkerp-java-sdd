package com.fksoft.domain.intelligence;

import com.fksoft.domain.intelligence.internal.AgencyFxAccrual;
import com.fksoft.domain.intelligence.internal.AgencyFxAccrualRepository;
import com.fksoft.domain.intelligence.internal.BookingAttribution;
import com.fksoft.domain.intelligence.internal.BookingAttributionRepository;
import com.fksoft.domain.intelligence.internal.Insight;
import com.fksoft.domain.intelligence.internal.InsightRepository;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Intelligence (DSS) module (SPEC-0013). It consumes events from other
 * contexts (read-only), projects them into read-models, and generates prescriptive {@code
 * Insight}s. It ADVISES, it NEVER COMMANDS (BR2/BR3): it writes only its own tables and never calls
 * back into a producer (it is a consumer-leaf; the agency of a booking is learned from {@code
 * BookingConfirmed}, not fetched from Booking — DL-0034).
 *
 * <p>Every generated insight's output is validated before it is persisted (BR7): an invalid
 * assessment yields no insight (fallback), never an inconsistent one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligenceService {

  private static final String CURRENCY = "BRL";
  private static final List<String> PROMO_FX_SOURCES =
      List.of("RateSubsidyAccrued", "FxPositionClosed", "BookingConfirmed");

  private final InsightRepository insights;
  private final BookingAttributionRepository attributions;
  private final AgencyFxAccrualRepository accruals;
  private final InsightNarrator narrator;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * OverrideNudge feature flag (BR6 / Q4, DL-0036). Default {@code false}: while the
   * commission-tier model does not exist, the nudge stays OFF and produces NO insight — no fake
   * data. The seam (the type, the listener and this branch) is in place so it can be switched on
   * without refactoring once the tier table exists.
   */
  @org.springframework.beans.factory.annotation.Value(
      "${intelligence.override-nudge.enabled:false}")
  private final boolean overrideNudgeEnabled;

  /**
   * Learns the {@code booking → account} mapping from a confirmed booking (DL-0034) and rolls any
   * already-buffered FX facts into the agency accrual, then refreshes the agency's promo-FX
   * insight.
   */
  @Transactional
  public void onBookingConfirmed(UUID bookingId, UUID accountId) {
    BookingAttribution attribution = attribution(bookingId);
    attribution.linkAccount(accountId, clock.instant());
    attributions.save(attribution);
    applyIfReady(attribution);
  }

  /**
   * Records the accrued subsidy for a booking (read-only consumption of {@code
   * RateSubsidyAccrued}), buffering it until the agency is known, then attributing it.
   */
  @Transactional
  public void onRateSubsidyAccrued(UUID bookingId, Money subsidy) {
    BookingAttribution attribution = attribution(bookingId);
    attribution.addSubsidy(toBrl(subsidy), clock.instant());
    attributions.save(attribution);
    applyIfReady(attribution);
  }

  /**
   * Records a closed FX position's total gap for a booking (read-only consumption of {@code
   * FxPositionClosed}), buffering it until the agency is known, then attributing it.
   */
  @Transactional
  public void onFxPositionClosed(UUID bookingId, Money totalGap) {
    BookingAttribution attribution = attribution(bookingId);
    attribution.recordPositionClosed(toBrl(totalGap), clock.instant());
    attributions.save(attribution);
    applyIfReady(attribution);
  }

  /**
   * Consumes a {@code PriceOverridden} fact for the OverrideNudge seam (BR6, DL-0036). While the
   * commission-tier model does not exist (feature flag off, the default), this is a deliberate
   * no-op — the nudge produces NO insight rather than fake data. When the tier table exists, the
   * flag turns on and the distance-to-next-tier computation plugs in here without reshaping the
   * framework.
   *
   * @param quoteId the overridden quote id (the future nudge subject)
   */
  @Transactional
  public void onPriceOverridden(UUID quoteId) {
    if (!overrideNudgeEnabled) {
      log.debug(
          "OverrideNudge disabled (no tier model yet) — ignoring PriceOverridden for {}", quoteId);
      return;
    }
    // Seam only (Q4/BR6, DL-0036): the distance-to-next-tier and retroactive-gain computation needs
    // the governed commission-tier table, which does not exist yet. Until it does, even with the
    // flag
    // on we produce NO insight (no fake data) and surface the gap as a warning rather than failing
    // the
    // override's transaction. Wiring this in is tracked by spec 0013 Open Question Q4.
    log.warn(
        "OverrideNudge flag is on but the commission-tier model (Q4) does not exist yet — "
            + "no nudge generated for quote {} (see docs/specs/0013 Q4 / DL-0036)",
        quoteId);
  }

  /**
   * Consumes a {@code CampaignConverted} fact from Marketing (SPEC-0019 BR5; redesign 8.2-F): a
   * booking with a campaign code was confirmed — i.e. a campaign turned into a sale. Intelligence
   * is a consumer-leaf that advises, never commands (SPEC-0013 BR2): it only reads this signal. The
   * full campaign-ROI read-model (8.2-F "PromoConversion") belongs to a later Intelligence slice;
   * here we record the conversion as an observable business signal (counted by the {@code
   * campaign_conversions_total} metric the Marketing side emits) without inventing an out-of-scope
   * insight table (Rule Zero).
   *
   * @param campaignId the converted campaign id
   * @param bookingId the booking that converted
   */
  @Transactional(readOnly = true)
  public void onCampaignConverted(UUID campaignId, UUID bookingId) {
    log.info(
        "CampaignConversionObserved campaignId={} bookingId={} (DSS attribution signal, SPEC-0019)",
        campaignId,
        bookingId);
  }

  /** Fetches an insight by id. */
  @Transactional(readOnly = true)
  public InsightView getById(UUID insightId) {
    return insights
        .findById(insightId)
        .map(Insight::toView)
        .orElseThrow(InsightNotFoundException::new);
  }

  /** Lists insights filtered by type/subjectRef/status, ordered by estimated gain descending. */
  @Transactional(readOnly = true)
  public Page<InsightView> list(
      InsightType type, String subjectRef, InsightStatus status, Pageable pageable) {
    return insights.search(type, subjectRef, status, pageable).map(Insight::toView);
  }

  /**
   * Records a human decision on an insight (BR4) — never triggers an automatic action (BR2). The
   * decision must be one of {@code ACCEPTED}, {@code REJECTED} or {@code DISMISSED}; anything else
   * (including {@code NEW} or an unknown value) is rejected.
   *
   * @param insightId the insight id
   * @param decision the raw decision value (validated against the enum, minus {@code NEW})
   * @param decidedBy who decided (audit)
   * @return the updated insight view
   * @throws InsightNotFoundException when the insight does not exist
   * @throws InsightDecisionInvalidException when the decision is outside {ACCEPTED, REJECTED,
   *     DISMISSED}
   */
  @Transactional
  public InsightView decide(UUID insightId, String decision, String decidedBy) {
    InsightStatus parsed = parseDecision(decision);
    Insight insight = insights.findById(insightId).orElseThrow(InsightNotFoundException::new);
    Instant now = clock.instant();
    insight.decide(parsed, decidedBy, now);
    insights.save(insight);
    events.publishEvent(new InsightDecided(insightId, parsed, decidedBy, now));
    log.info(
        "InsightDecided insightId={} decision={} decidedBy={} subjectRef={}",
        insightId,
        parsed,
        decidedBy,
        insight.subjectRef());
    return insight.toView();
  }

  private InsightStatus parseDecision(String decision) {
    if (decision == null) {
      throw new InsightDecisionInvalidException();
    }
    InsightStatus status;
    try {
      status = InsightStatus.valueOf(decision.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw new InsightDecisionInvalidException();
    }
    if (status == InsightStatus.NEW) {
      throw new InsightDecisionInvalidException();
    }
    return status;
  }

  private BookingAttribution attribution(UUID bookingId) {
    return attributions
        .findById(bookingId)
        .orElseGet(() -> BookingAttribution.forBooking(bookingId, clock.instant()));
  }

  /**
   * Rolls a mapped booking's not-yet-applied buffered facts into its agency accrual, incrementally
   * (DL-0034). Facts arrive across several events/transactions and in any order; this applies only
   * the delta since the last roll, so nothing is double-counted and nothing is lost. A no-op until
   * the account is known.
   */
  private void applyIfReady(BookingAttribution attribution) {
    if (!attribution.mapped()) {
      return;
    }
    BigDecimal subsidyDelta = attribution.unappliedSubsidyBrl();
    BigDecimal gapDelta = attribution.unappliedGapBrl();
    boolean countVolume = attribution.shouldCountVolume();
    if (subsidyDelta.signum() == 0 && gapDelta.signum() == 0 && !countVolume) {
      return;
    }
    Instant now = clock.instant();
    AgencyFxAccrual accrual =
        accruals
            .findById(attribution.accountId())
            .orElseGet(() -> AgencyFxAccrual.forAccount(attribution.accountId(), now));
    accrual.roll(subsidyDelta, gapDelta, countVolume, now);
    accruals.save(accrual);
    attribution.markApplied(countVolume, now);
    attributions.save(attribution);
    refreshPromoFxInsight(accrual.accountId(), accrual);
  }

  /** Runs the deterministic advisor over the agency totals and upserts its insight (BR5/BR7). */
  private void refreshPromoFxInsight(UUID accountId, AgencyFxAccrual accrual) {
    PromoFxSignal signal =
        new PromoFxSignal(
            Money.of(accrual.accruedSubsidyBrl(), CURRENCY),
            Money.of(accrual.realizedGapBrl(), CURRENCY),
            accrual.volumeAttracted(),
            PROMO_FX_SOURCES);
    Optional<PromoFxAssessment> assessment = PromoFxAdvisor.assess(signal);
    if (assessment.isEmpty()) {
      return;
    }
    PromoFxAssessment advice = assessment.get();
    if (!isValid(advice)) {
      log.warn(
          "PromoFx assessment for account {} failed validation — falling back to no insight",
          accountId);
      return;
    }
    String subjectRef = accountId.toString();
    InsightEvidence evidence =
        new InsightEvidence(
            signal.accruedSubsidy(),
            signal.realizedGap(),
            signal.volumeAttracted(),
            advice.sources());
    String action = narrator.narratePromoFx(SubjectKind.AGENCY, subjectRef, advice);
    Instant now = clock.instant();

    Insight insight =
        insights
            .findByTypeAndSubjectKindAndSubjectRef(
                InsightType.PROMO_FX_ADVISOR, SubjectKind.AGENCY, subjectRef)
            .map(
                existing -> {
                  existing.refresh(evidence, advice, action, now);
                  return existing;
                })
            .orElseGet(
                () ->
                    Insight.promoFx(SubjectKind.AGENCY, subjectRef, evidence, advice, action, now));
    insights.save(insight);

    events.publishEvent(
        new InsightGenerated(
            insight.id(), InsightType.PROMO_FX_ADVISOR, subjectRef, advice.estimatedGain(), now));
    log.info(
        "InsightGenerated insightId={} type=PROMO_FX_ADVISOR subjectRef={} verdict={} estimatedGain={}",
        insight.id(),
        subjectRef,
        advice.verdict(),
        advice.estimatedGain());
  }

  /**
   * Validates an assessment before it can affect state (BR7, messaging-and-integrations.md AI):
   * verdict present, a gain or risk in BRL, non-empty provenance, and a guardrail only on
   * QUEIMA_MARGEM. An invalid assessment is rejected (fallback to no insight), never persisted.
   */
  private boolean isValid(PromoFxAssessment advice) {
    if (advice.verdict() == null || advice.sources().isEmpty()) {
      return false;
    }
    boolean hasGain =
        advice.estimatedGain() != null && CURRENCY.equals(advice.estimatedGain().currency());
    boolean hasRisk =
        advice.estimatedRisk() != null && CURRENCY.equals(advice.estimatedRisk().currency());
    if (!hasGain && !hasRisk) {
      return false;
    }
    return switch (advice.verdict()) {
      case CONVERTE -> !advice.hasGuardrail();
      case QUEIMA_MARGEM -> advice.hasGuardrail();
    };
  }

  private BigDecimal toBrl(Money money) {
    if (!CURRENCY.equals(money.currency())) {
      throw new IllegalArgumentException("expected BRL, got " + money.currency());
    }
    return money.amount();
  }
}
