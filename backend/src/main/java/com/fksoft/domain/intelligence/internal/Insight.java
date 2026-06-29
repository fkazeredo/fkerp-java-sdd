package com.fksoft.domain.intelligence.internal;

import com.fksoft.domain.intelligence.InsightEvidence;
import com.fksoft.domain.intelligence.InsightGuardrail;
import com.fksoft.domain.intelligence.InsightRecommendation;
import com.fksoft.domain.intelligence.InsightStatus;
import com.fksoft.domain.intelligence.InsightType;
import com.fksoft.domain.intelligence.InsightView;
import com.fksoft.domain.intelligence.PromoFxAssessment;
import com.fksoft.domain.intelligence.SubjectKind;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Insight read-model aggregate (SPEC-0013 BR1): the prescriptive advice projected from consumed
 * events. It holds the evidence (numbers + provenance), the recommendation (verdict + action +
 * estimated gain/risk), the guardrail crossed (if any) and the human-decision status. It is a
 * projection — it MUST NOT command or mutate any other module (BR2/BR3). Module-internal.
 *
 * <p>Evidence/recommendation/guardrail are stored as fixed structured columns rather than a jsonb
 * blob (Rule Zero): the shape is small and known, so a jsonb dependency would be overengineering
 * (same posture as the booking penalty-windows codec). The {@code sources} provenance is a short
 * comma-separated list (codec below).
 */
@Entity
@Table(name = "insights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Insight {

  private static final String CURRENCY = "BRL";

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private InsightType type;

  @Enumerated(EnumType.STRING)
  private SubjectKind subjectKind;

  private String subjectRef;

  // evidence (numbers + provenance)
  private BigDecimal accruedSubsidyBrl;
  private BigDecimal realizedGapBrl;
  private long volumeAttracted;
  private String evidenceSources;

  // recommendation
  @Enumerated(EnumType.STRING)
  private com.fksoft.domain.intelligence.Verdict verdict;

  private String recommendationAction;
  private BigDecimal estimatedGainBrl;
  private BigDecimal estimatedRiskBrl;

  // guardrail (alert, never blocks)
  private String guardrailDescription;
  private BigDecimal guardrailThresholdBrl;

  @Enumerated(EnumType.STRING)
  private InsightStatus status;

  private Instant generatedAt;
  private String decidedBy;
  private Instant decidedAt;

  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  /**
   * Builds a fresh PROMO_FX_ADVISOR insight from an assessment and its evidence (BR1/BR5).
   *
   * @param subjectKind the axis (AGENCY in v1)
   * @param subjectRef the subject reference
   * @param evidence the numbers + provenance
   * @param assessment the advisor verdict + gain/risk + guardrail
   * @param action the narrated recommendation action text
   * @param now the generation instant (UTC)
   * @return a new NEW insight
   */
  public static Insight promoFx(
      SubjectKind subjectKind,
      String subjectRef,
      InsightEvidence evidence,
      PromoFxAssessment assessment,
      String action,
      Instant now) {
    Insight insight = new Insight();
    insight.id = UUID.randomUUID();
    insight.type = InsightType.PROMO_FX_ADVISOR;
    insight.subjectKind = subjectKind;
    insight.subjectRef = subjectRef;
    insight.applyEvidence(evidence);
    insight.applyRecommendation(assessment, action);
    insight.status = InsightStatus.NEW;
    insight.generatedAt = now;
    insight.createdAt = now;
    insight.updatedAt = now;
    return insight;
  }

  /**
   * Refreshes the evidence and recommendation of an existing insight from new facts (on-event
   * recompute, DL-0036), resetting the decision to NEW so the human re-evaluates the updated
   * advice.
   */
  public void refresh(
      InsightEvidence evidence, PromoFxAssessment assessment, String action, Instant now) {
    applyEvidence(evidence);
    applyRecommendation(assessment, action);
    status = InsightStatus.NEW;
    decidedBy = null;
    decidedAt = null;
    generatedAt = now;
    updatedAt = now;
  }

  /** Records the human decision (BR4) — never triggers an automatic action (BR2). */
  public void decide(InsightStatus decision, String decidedBy, Instant now) {
    this.status = decision;
    this.decidedBy = decidedBy;
    this.decidedAt = now;
    this.updatedAt = now;
  }

  private void applyEvidence(InsightEvidence evidence) {
    this.accruedSubsidyBrl = evidence.accruedSubsidy().amount();
    this.realizedGapBrl = evidence.realizedGap().amount();
    this.volumeAttracted = evidence.volumeAttracted();
    this.evidenceSources = SourcesCodec.encode(evidence.sources());
  }

  private void applyRecommendation(PromoFxAssessment assessment, String action) {
    this.verdict = assessment.verdict();
    this.recommendationAction = action;
    this.estimatedGainBrl =
        assessment.estimatedGain() == null ? null : assessment.estimatedGain().amount();
    this.estimatedRiskBrl =
        assessment.estimatedRisk() == null ? null : assessment.estimatedRisk().amount();
    if (assessment.hasGuardrail()) {
      this.guardrailDescription = "burn-threshold-crossed";
      this.guardrailThresholdBrl = assessment.guardrailThresholdCrossed().amount();
    } else {
      this.guardrailDescription = null;
      this.guardrailThresholdBrl = null;
    }
  }

  /** Projects the insight to its public read view. */
  public InsightView toView() {
    List<String> sources = SourcesCodec.decode(evidenceSources);
    InsightEvidence evidence =
        new InsightEvidence(
            Money.of(accruedSubsidyBrl, CURRENCY),
            Money.of(realizedGapBrl, CURRENCY),
            volumeAttracted,
            sources);
    InsightRecommendation recommendation =
        new InsightRecommendation(
            verdict,
            recommendationAction,
            estimatedGainBrl == null ? null : Money.of(estimatedGainBrl, CURRENCY),
            estimatedRiskBrl == null ? null : Money.of(estimatedRiskBrl, CURRENCY));
    InsightGuardrail guardrail =
        guardrailThresholdBrl == null
            ? null
            : new InsightGuardrail(guardrailDescription, Money.of(guardrailThresholdBrl, CURRENCY));
    return new InsightView(
        id,
        type,
        subjectKind,
        subjectRef,
        evidence,
        recommendation,
        guardrail,
        status,
        generatedAt,
        decidedBy,
        decidedAt);
  }
}
