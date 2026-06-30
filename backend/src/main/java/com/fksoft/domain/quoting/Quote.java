package com.fksoft.domain.quoting;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Quote aggregate (SPEC-0005): the frozen composition of a MANUAL sale plus its override history.
 * The composition's provenance is frozen at creation (BR4) — later rate/policy changes never alter
 * an existing quote; {@code suggestedAmount} is immutable (BR5); only {@code appliedAmount} moves,
 * always through {@link #applyOverride} which records the divergence (BR6). Amounts in the sale
 * currency are stored as numerics and reconstructed with the pair's quote currency.
 *
 * <p>The INTEGRATED branch (SPEC-0009, DL-0018) reuses this aggregate: {@link #composeIntegrated}
 * trusts a closed external price ({@code suggestedAmount == appliedAmount == externalPrice})
 * without running the suggestion engine, so the MANUAL-only composition fields (FX, commission,
 * markup, currency pair) stay {@code null} and {@link #applyOverride} is refused (BR2).
 * Module-internal.
 */
@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class Quote {

  @Id private UUID id;

  private UUID accountId;

  private UUID sourceOfferId;

  @Enumerated(EnumType.STRING)
  private PriceOrigin priceOrigin;

  private BigDecimal basePriceAmount;
  private String basePriceCurrency;

  private String currencyPair;

  private BigDecimal fxRate;
  private UUID rateId;

  private BigDecimal baseConvertedAmount;

  private BigDecimal supplierPct;
  private BigDecimal agentPct;

  private BigDecimal supplierCommission;
  private BigDecimal agentCommission;
  private BigDecimal spread;
  private boolean spreadNegative;

  private BigDecimal markupPct;
  private BigDecimal markupAmount;
  private String markupSource;

  private BigDecimal suggestedAmount;
  private BigDecimal appliedAmount;

  @Enumerated(EnumType.STRING)
  private QuoteStatus status;

  private Instant validUntil;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("performedAt ASC")
  private List<OverrideRecord> overrides = new ArrayList<>();

  /**
   * Composes a new quote from a fully-computed, frozen composition (BR4). {@code appliedAmount}
   * starts equal to {@code suggestedAmount} (no override yet).
   *
   * @param accountId the account the quote is for
   * @param composition the frozen composition values
   * @param validUntil optional validity instant
   * @param now creation instant (UTC)
   * @param actor who composed it (audit)
   * @return a new, persistable quote
   */
  public static Quote compose(
      UUID accountId, QuoteComposition composition, Instant validUntil, Instant now, String actor) {
    Quote quote = new Quote();
    quote.id = UUID.randomUUID();
    quote.accountId = accountId;
    quote.sourceOfferId = null;
    quote.priceOrigin = composition.priceOrigin();
    quote.basePriceAmount = composition.basePrice().amount();
    quote.basePriceCurrency = composition.basePrice().currency();
    quote.currencyPair = composition.currencyPair().asText();
    quote.fxRate = composition.fxRate();
    quote.rateId = composition.rateId();
    quote.baseConvertedAmount = composition.baseConverted().amount();
    quote.supplierPct = composition.supplierPct();
    quote.agentPct = composition.agentPct();
    quote.supplierCommission = composition.supplierCommission().amount();
    quote.agentCommission = composition.agentCommission().amount();
    quote.spread = composition.spread().amount();
    quote.spreadNegative = composition.spreadNegative();
    quote.markupPct = composition.markupPct();
    quote.markupAmount = composition.markupAmount().amount();
    quote.markupSource = composition.markupSource();
    quote.suggestedAmount = composition.suggestedAmount().amount();
    quote.appliedAmount = composition.suggestedAmount().amount();
    quote.status = QuoteStatus.COMPOSED;
    quote.validUntil = validUntil;
    quote.createdAt = now;
    quote.updatedAt = now;
    quote.createdBy = actor;
    quote.updatedBy = actor;
    return quote;
  }

  /**
   * Creates an INTEGRATED quote from a trusted, closed external price (SPEC-0009 BR2, DL-0018): the
   * suggestion engine does not run, so {@code suggestedAmount == appliedAmount == externalPrice}
   * and the MANUAL-only composition fields stay {@code null}. No {@link OverrideRecord} exists. The
   * external price's currency is stored in {@code basePriceCurrency} and is the quote's sale
   * currency.
   *
   * @param accountId the resolved account the quote is for
   * @param sourceOfferId the sourced offer recording the provenance (nullable)
   * @param externalPrice the trusted, closed external price
   * @param validUntil optional validity instant
   * @param now creation instant (UTC)
   * @param actor who/what created it (audit)
   * @return a new, persistable INTEGRATED quote
   */
  public static Quote composeIntegrated(
      UUID accountId,
      UUID sourceOfferId,
      Money externalPrice,
      Instant validUntil,
      Instant now,
      String actor) {
    Quote quote = new Quote();
    quote.id = UUID.randomUUID();
    quote.accountId = accountId;
    quote.sourceOfferId = sourceOfferId;
    quote.priceOrigin = PriceOrigin.INTEGRATED;
    quote.basePriceAmount = externalPrice.amount();
    quote.basePriceCurrency = externalPrice.currency();
    quote.suggestedAmount = externalPrice.amount();
    quote.appliedAmount = externalPrice.amount();
    quote.status = QuoteStatus.COMPOSED;
    quote.validUntil = validUntil;
    quote.createdAt = now;
    quote.updatedAt = now;
    quote.createdBy = actor;
    quote.updatedBy = actor;
    return quote;
  }

  /**
   * Applies a price override: records an {@link OverrideRecord} and moves {@code appliedAmount}
   * (BR6). The suggested amount is never touched (BR5). Refused on INTEGRATED quotes — the external
   * price is trusted and there is no suggestion to diverge from (SPEC-0009 BR2).
   *
   * @param newApplied the new applied amount (must be in the sale currency — BR7)
   * @param reason the mandatory, non-empty reason (BR6)
   * @param performedBy who performed the override
   * @param when when it happened
   * @throws QuoteOverrideNotApplicableException when the quote is INTEGRATED (SPEC-0009 BR2)
   * @throws QuoteOverrideReasonRequiredException when the reason is empty (BR6)
   * @throws QuoteOverrideCurrencyMismatchException when the currency differs from the suggestion
   *     (BR7)
   */
  public void applyOverride(Money newApplied, String reason, String performedBy, Instant when) {
    if (priceOrigin == PriceOrigin.INTEGRATED) {
      throw new QuoteOverrideNotApplicableException();
    }
    if (reason == null || reason.isBlank()) {
      throw new QuoteOverrideReasonRequiredException();
    }
    if (!newApplied.currency().equals(saleCurrency())) {
      throw new QuoteOverrideCurrencyMismatchException();
    }
    overrides.add(
        OverrideRecord.of(
            this, appliedAmount, newApplied.amount(), reason.trim(), performedBy, when));
    appliedAmount = newApplied.amount();
    updatedAt = when;
    updatedBy = performedBy;
  }

  /**
   * Projects this aggregate to its public read view, reconstructing money in the sale currency. For
   * an INTEGRATED quote (DL-0018) the MANUAL-only sections — commission, markup, FX, converted base
   * and rate provenance — are {@code null}; only the trusted price travels.
   */
  public QuoteView toView() {
    String sale = saleCurrency();
    boolean composed = priceOrigin != PriceOrigin.INTEGRATED;
    QuoteView.CommissionView commission =
        composed
            ? new QuoteView.CommissionView(
                Money.of(supplierCommission, sale),
                Money.of(agentCommission, sale),
                Money.of(spread, sale),
                spreadNegative)
            : null;
    QuoteView.MarkupView markup =
        composed
            ? new QuoteView.MarkupView(markupPct, Money.of(markupAmount, sale), markupSource)
            : null;
    QuoteView.ProvenanceView provenance = new QuoteView.ProvenanceView(rateId, markupSource);
    List<QuoteView.OverrideRecordView> overrideViews =
        overrides.stream()
            .map(
                record ->
                    new QuoteView.OverrideRecordView(
                        Money.of(record.fromAmount(), sale),
                        Money.of(record.toAmount(), sale),
                        record.reason(),
                        record.performedBy(),
                        record.performedAt()))
            .toList();
    return new QuoteView(
        id,
        accountId,
        priceOrigin,
        Money.of(basePriceAmount, basePriceCurrency),
        fxRate,
        composed ? Money.of(baseConvertedAmount, sale) : null,
        commission,
        markup,
        Money.of(suggestedAmount, sale),
        Money.of(appliedAmount, sale),
        status,
        validUntil,
        provenance,
        overrideViews);
  }

  /**
   * The frozen cross-module snapshot (account + expected financials). For an INTEGRATED quote the
   * commission/FX fields are {@code null} (no two-sided commission was computed); the applied price
   * is carried as {@code baseConverted} so consumers still see the trusted amount.
   */
  public QuoteSnapshot toSnapshot() {
    String sale = saleCurrency();
    if (priceOrigin == PriceOrigin.INTEGRATED) {
      return new QuoteSnapshot(
          id,
          accountId,
          Money.of(basePriceAmount, basePriceCurrency),
          null,
          Money.of(appliedAmount, sale),
          null,
          null,
          null);
    }
    return new QuoteSnapshot(
        id,
        accountId,
        Money.of(basePriceAmount, basePriceCurrency),
        fxRate,
        Money.of(baseConvertedAmount, sale),
        Money.of(supplierCommission, sale),
        Money.of(agentCommission, sale),
        Money.of(spread, sale));
  }

  /**
   * The sale currency: the quote currency of the pair for a MANUAL quote, or the external price's
   * currency for an INTEGRATED one (which has no currency pair — DL-0018).
   */
  private String saleCurrency() {
    return currencyPair != null ? CurrencyPair.parse(currencyPair).quote() : basePriceCurrency;
  }
}
