package com.fksoft.domain.reconciliation;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteSnapshot;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Reconciliation case aggregate (SPEC-0007): the frozen expected values copied from the quote, the
 * realized settlement values, and the derived realized spread (BR4), FX gain/loss (BR5) and
 * discrepancy (BR7). Derivations are computed here, never typed in. Module-internal.
 */
@Entity
@Table(name = "reconciliation_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class ReconciliationCase {

  private static final int MONEY_SCALE = 2;

  @Id private UUID id;

  private UUID bookingId;

  private BigDecimal baseAmount;
  private String baseCurrency;
  private String saleCurrency;

  private BigDecimal pinnedRate;
  private BigDecimal baseBrl;

  private BigDecimal expectedSupplierCommissionBrl;
  private BigDecimal expectedAgentCommissionBrl;
  private BigDecimal expectedSpreadBrl;

  private BigDecimal amountReceivedFromAgencyBrl;
  private BigDecimal supplierSettlementRate;
  private BigDecimal supplierPaidBrl;
  private BigDecimal commissionReceivedFromSupplierBrl;
  private BigDecimal commissionPaidToAgentBrl;

  private BigDecimal realizedSpreadBrl;
  private BigDecimal fxGainLossBrl;
  private BigDecimal discrepancyBrl;

  @Enumerated(EnumType.STRING)
  private CaseStatus status;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Opens a case copying the frozen provenance from a quote snapshot (BR1).
   *
   * @param bookingId the booking that triggered the case
   * @param quote the frozen quote snapshot
   * @param now creation instant (UTC)
   * @param actor the actor (audit)
   * @return a new, persistable OPEN case
   */
  public static ReconciliationCase open(
      UUID bookingId, QuoteSnapshot quote, Instant now, String actor) {
    ReconciliationCase reconciliationCase = new ReconciliationCase();
    reconciliationCase.id = UUID.randomUUID();
    reconciliationCase.bookingId = bookingId;
    reconciliationCase.baseAmount = quote.basePrice().amount();
    reconciliationCase.baseCurrency = quote.basePrice().currency();
    reconciliationCase.saleCurrency = quote.baseConverted().currency();
    reconciliationCase.pinnedRate = quote.pinnedRate();
    reconciliationCase.baseBrl = quote.baseConverted().amount();
    reconciliationCase.expectedSupplierCommissionBrl = quote.expectedSupplierCommission().amount();
    reconciliationCase.expectedAgentCommissionBrl = quote.expectedAgentCommission().amount();
    reconciliationCase.expectedSpreadBrl = quote.expectedSpread().amount();
    reconciliationCase.discrepancyBrl = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    reconciliationCase.status = CaseStatus.OPEN;
    reconciliationCase.createdAt = now;
    reconciliationCase.updatedAt = now;
    reconciliationCase.createdBy = actor;
    reconciliationCase.updatedBy = actor;
    return reconciliationCase;
  }

  /** Marks the case CANCELLED (its booking was cancelled — BR2). */
  public void cancel(Instant now, String actor) {
    status = CaseStatus.CANCELLED;
    updatedAt = now;
    updatedBy = actor;
  }

  /**
   * Records the realized settlement values and recomputes the derivations (BR3-BR7). Money values
   * must be in the sale currency. When all legs are present the case is SETTLED (or DISCREPANCY if
   * the realized spread exceeds tolerance), otherwise PARTIALLY_SETTLED.
   *
   * @param input the realized values (any subset)
   * @param toleranceFloor the absolute tolerance floor
   * @param tolerancePct the proportional tolerance (of the expected spread)
   * @param now the settlement instant (UTC)
   * @param actor the actor (audit)
   */
  public void settle(
      SettlementInput input,
      BigDecimal toleranceFloor,
      BigDecimal tolerancePct,
      Instant now,
      String actor) {
    amountReceivedFromAgencyBrl =
        mergeMoney(amountReceivedFromAgencyBrl, input.amountReceivedFromAgency());
    supplierPaidBrl = mergeMoney(supplierPaidBrl, input.supplierPaidAmount());
    commissionReceivedFromSupplierBrl =
        mergeMoney(commissionReceivedFromSupplierBrl, input.commissionReceivedFromSupplier());
    commissionPaidToAgentBrl = mergeMoney(commissionPaidToAgentBrl, input.commissionPaidToAgent());
    if (input.supplierSettlementRate() != null) {
      supplierSettlementRate = input.supplierSettlementRate();
    }

    if (allLegsPresent()) {
      realizedSpreadBrl =
          amountReceivedFromAgencyBrl
              .subtract(supplierPaidBrl)
              .subtract(commissionPaidToAgentBrl)
              .add(commissionReceivedFromSupplierBrl)
              .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      fxGainLossBrl =
          pinnedRate
              .subtract(supplierSettlementRate)
              .multiply(baseAmount)
              .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      discrepancyBrl = realizedSpreadBrl.subtract(expectedSpreadBrl).abs();
      BigDecimal tolerance = tolerance(toleranceFloor, tolerancePct);
      status =
          discrepancyBrl.compareTo(tolerance) > 0 ? CaseStatus.DISCREPANCY : CaseStatus.SETTLED;
    } else {
      if (supplierSettlementRate != null) {
        fxGainLossBrl =
            pinnedRate
                .subtract(supplierSettlementRate)
                .multiply(baseAmount)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      }
      status = CaseStatus.PARTIALLY_SETTLED;
    }
    updatedAt = now;
    updatedBy = actor;
  }

  /** Whether the realized spread was computed and exceeded tolerance (status DISCREPANCY). */
  public boolean isDiscrepancy() {
    return status == CaseStatus.DISCREPANCY;
  }

  /** Projects the case to its public read view, reconstructing money in the right currencies. */
  public ReconciliationCaseView toView() {
    return new ReconciliationCaseView(
        id,
        bookingId,
        Money.of(baseAmount, baseCurrency),
        pinnedRate,
        Money.of(baseBrl, saleCurrency),
        Money.of(expectedSupplierCommissionBrl, saleCurrency),
        Money.of(expectedAgentCommissionBrl, saleCurrency),
        Money.of(expectedSpreadBrl, saleCurrency),
        realizedSpreadBrl == null ? null : Money.of(realizedSpreadBrl, saleCurrency),
        fxGainLossBrl == null ? null : Money.of(fxGainLossBrl, saleCurrency),
        Money.of(discrepancyBrl, saleCurrency),
        status);
  }

  private boolean allLegsPresent() {
    return amountReceivedFromAgencyBrl != null
        && supplierPaidBrl != null
        && commissionReceivedFromSupplierBrl != null
        && commissionPaidToAgentBrl != null
        && supplierSettlementRate != null;
  }

  private BigDecimal tolerance(BigDecimal floor, BigDecimal pct) {
    BigDecimal proportional =
        expectedSpreadBrl.abs().multiply(pct).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return floor.max(proportional);
  }

  private BigDecimal mergeMoney(BigDecimal current, Money provided) {
    if (provided == null) {
      return current;
    }
    if (!provided.currency().equals(saleCurrency)) {
      throw new com.fksoft.domain.reconciliation.ReconciliationCurrencyMismatchException();
    }
    return provided.amount();
  }
}
