package com.fksoft.domain.exchange;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FX forward contract aggregate (SPEC-0032, Fase 19h/DL-0130): a treasury instrument that locks a
 * future exchange rate for a foreign-currency notional — the market practice travel wholesalers use
 * to hedge the exposure the book accumulates (OVERVIEW 7.2). Registration is
 * <strong>manual</strong> (no bank integration); an OPEN forward reduces the
 * <strong>unhedged</strong> exposure the drift alert watches. Settling records the effective rate
 * (the realized side); cancelling closes it without effect. Status is a state machine ({@code OPEN
 * → SETTLED | CANCELLED}) and stays an enum (Fase 18 keep criterion). Module-internal.
 */
@Entity
@Table(name = "fx_forward_contracts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class ForwardContract {

  @Id private UUID id;

  /** The foreign currency being bought forward (e.g. USD). */
  private String currency;

  /** The notional amount in the foreign currency. */
  private BigDecimal notional;

  /** The locked rate (foreign → BRL), scale 6. */
  @Column(name = "contract_rate")
  private BigDecimal contractRate;

  private LocalDate tradeDate;
  private LocalDate maturityDate;
  private String counterparty;

  @Enumerated(EnumType.STRING)
  private ForwardStatus status;

  /** The effective market rate at settlement (scale 6); null until settled. */
  private BigDecimal settledRate;

  private Instant settledAt;
  private Instant cancelledAt;
  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String resolvedBy;

  @Version private Long version;

  /**
   * Registers a forward, enforcing the invariants: positive notional and rate, a 3-letter currency
   * and a maturity after the trade date.
   *
   * @throws ForwardContractInvalidException when an invariant is violated (400)
   */
  public static ForwardContract register(
      String currency,
      BigDecimal notional,
      BigDecimal contractRate,
      LocalDate tradeDate,
      LocalDate maturityDate,
      String counterparty,
      Instant now,
      String actor) {
    if (currency == null || !currency.matches("[A-Z]{3}")) {
      throw new ForwardContractInvalidException();
    }
    if (notional == null || notional.signum() <= 0) {
      throw new ForwardContractInvalidException();
    }
    if (contractRate == null || contractRate.signum() <= 0) {
      throw new ForwardContractInvalidException();
    }
    if (tradeDate == null || maturityDate == null || !maturityDate.isAfter(tradeDate)) {
      throw new ForwardContractInvalidException();
    }
    if (counterparty == null || counterparty.isBlank()) {
      throw new ForwardContractInvalidException();
    }
    ForwardContract forward = new ForwardContract();
    forward.id = UUID.randomUUID();
    forward.currency = currency;
    forward.notional = notional.setScale(2, java.math.RoundingMode.HALF_UP);
    forward.contractRate = contractRate;
    forward.tradeDate = tradeDate;
    forward.maturityDate = maturityDate;
    forward.counterparty = counterparty.trim();
    forward.status = ForwardStatus.OPEN;
    forward.createdAt = now;
    forward.updatedAt = now;
    forward.createdBy = actor;
    return forward;
  }

  /**
   * Settles the forward at the effective rate (OPEN → SETTLED). The realized result feeds the FX
   * reports; the contract itself only records the fact.
   *
   * @throws ForwardContractNotOpenException when not OPEN (409)
   * @throws ForwardContractInvalidException when the rate is not positive (400)
   */
  public void settle(BigDecimal effectiveRate, Instant when, String actor) {
    requireOpen();
    if (effectiveRate == null || effectiveRate.signum() <= 0) {
      throw new ForwardContractInvalidException();
    }
    this.status = ForwardStatus.SETTLED;
    this.settledRate = effectiveRate;
    this.settledAt = when;
    this.resolvedBy = actor;
    this.updatedAt = when;
  }

  /**
   * Cancels the forward (OPEN → CANCELLED) — it stops counting as coverage.
   *
   * @throws ForwardContractNotOpenException when not OPEN (409)
   */
  public void cancel(Instant when, String actor) {
    requireOpen();
    this.status = ForwardStatus.CANCELLED;
    this.cancelledAt = when;
    this.resolvedBy = actor;
    this.updatedAt = when;
  }

  private void requireOpen() {
    if (status != ForwardStatus.OPEN) {
      throw new ForwardContractNotOpenException();
    }
  }

  /**
   * The realized settlement result in BRL (scale 2, HALF_UP): {@code (settledRate − contractRate) ×
   * notional}. Positive when locking the rate was cheaper than the market at settlement (the hedge
   * paid off for a buyer of foreign currency). Null until settled.
   */
  public BigDecimal settlementResultBrl() {
    if (settledRate == null) {
      return null;
    }
    return settledRate
        .subtract(contractRate)
        .multiply(notional)
        .setScale(2, java.math.RoundingMode.HALF_UP);
  }

  /** Read view of this forward. */
  public ForwardContractView toView() {
    return new ForwardContractView(
        id,
        currency,
        notional,
        contractRate,
        tradeDate,
        maturityDate,
        counterparty,
        status,
        settledRate,
        settlementResultBrl(),
        settledAt,
        cancelledAt,
        createdAt);
  }
}
