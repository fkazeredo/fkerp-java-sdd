package com.fksoft.domain.quoting.internal;

import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.PriceOrigin;
import com.fksoft.domain.quoting.QuoteOverrideCurrencyMismatchException;
import com.fksoft.domain.quoting.QuoteOverrideReasonRequiredException;
import com.fksoft.domain.quoting.QuoteSnapshot;
import com.fksoft.domain.quoting.QuoteStatus;
import com.fksoft.domain.quoting.QuoteView;
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
 * Module-internal.
 */
@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote {

  @Id private UUID id;

  private UUID accountId;

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
   * Applies a price override: records an {@link OverrideRecord} and moves {@code appliedAmount}
   * (BR6). The suggested amount is never touched (BR5).
   *
   * @param newApplied the new applied amount (must be in the sale currency — BR7)
   * @param reason the mandatory, non-empty reason (BR6)
   * @param performedBy who performed the override
   * @param when when it happened
   * @throws QuoteOverrideReasonRequiredException when the reason is empty (BR6)
   * @throws QuoteOverrideCurrencyMismatchException when the currency differs from the suggestion
   *     (BR7)
   */
  public void applyOverride(Money newApplied, String reason, String performedBy, Instant when) {
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

  /** Projects this aggregate to its public read view, reconstructing money in the sale currency. */
  public QuoteView toView() {
    String sale = saleCurrency();
    QuoteView.CommissionView commission =
        new QuoteView.CommissionView(
            Money.of(supplierCommission, sale),
            Money.of(agentCommission, sale),
            Money.of(spread, sale),
            spreadNegative);
    QuoteView.MarkupView markup =
        new QuoteView.MarkupView(markupPct, Money.of(markupAmount, sale), markupSource);
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
        Money.of(baseConvertedAmount, sale),
        commission,
        markup,
        Money.of(suggestedAmount, sale),
        Money.of(appliedAmount, sale),
        status,
        validUntil,
        provenance,
        overrideViews);
  }

  /** The frozen cross-module snapshot (account + expected financials). */
  public QuoteSnapshot toSnapshot() {
    String sale = saleCurrency();
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

  private String saleCurrency() {
    return CurrencyPair.parse(currencyPair).quote();
  }
}
