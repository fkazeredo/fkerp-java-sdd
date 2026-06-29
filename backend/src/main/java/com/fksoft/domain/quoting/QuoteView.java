package com.fksoft.domain.quoting;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read view of a composed quote with its frozen provenance and override history, returned to the
 * delivery layer (entity never leaves the module). For an INTEGRATED quote (SPEC-0009) the
 * MANUAL-only fields {@code fxRate}, {@code baseConverted}, {@code commission} and {@code markup}
 * are {@code null} (the external price is trusted, not recomposed).
 *
 * @param id quote id
 * @param accountId the account this quote belongs to
 * @param priceOrigin price origin (MANUAL or INTEGRATED)
 * @param basePrice the external/manual base price (supplier currency)
 * @param fxRate the frozen sell rate used in the composition ({@code null} for INTEGRATED)
 * @param baseConverted the base converted to the sale currency ({@code null} for INTEGRATED)
 * @param commission the two-sided commission decomposition ({@code null} for INTEGRATED)
 * @param markup the applied markup ({@code null} for INTEGRATED)
 * @param suggestedAmount the system-suggested sale price (immutable; equals applied for INTEGRATED)
 * @param appliedAmount the currently applied sale price (changes via override; MANUAL only)
 * @param status quote status
 * @param validUntil optional validity instant
 * @param provenance frozen provenance references
 * @param overrides the chained override records (oldest first)
 */
public record QuoteView(
    UUID id,
    UUID accountId,
    PriceOrigin priceOrigin,
    Money basePrice,
    BigDecimal fxRate,
    Money baseConverted,
    CommissionView commission,
    MarkupView markup,
    Money suggestedAmount,
    Money appliedAmount,
    QuoteStatus status,
    Instant validUntil,
    ProvenanceView provenance,
    List<OverrideRecordView> overrides) {

  /** The two-sided commission decomposition that travels with the quote. */
  public record CommissionView(Money supplier, Money agent, Money spread, boolean spreadNegative) {}

  /** The markup applied to the converted base. */
  public record MarkupView(BigDecimal pct, Money amount, String source) {}

  /** Frozen provenance references: which rate and which policy source were used. */
  public record ProvenanceView(UUID rateId, String policySource) {}

  /** A single override step: from-amount, to-amount, reason and who/when. */
  public record OverrideRecordView(
      Money fromAmount, Money toAmount, String reason, String performedBy, Instant performedAt) {}
}
