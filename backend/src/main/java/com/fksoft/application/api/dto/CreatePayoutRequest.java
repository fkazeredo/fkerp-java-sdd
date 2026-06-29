package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.PayeeType;
import com.fksoft.domain.payout.PayoutKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request body for {@code POST /api/payouts} (SPEC-0017): creates a repass/settlement/refund, with
 * an optional installment plan. For a foreign settlement give {@code settlementRate} (scale 6, &gt;
 * 0, BR1). For a REFUND give {@code originRef} (BR7). The plan is optional (DL-0050): give an
 * explicit {@code installments} list OR an {@code installmentCount} OR neither (à vista).
 *
 * @param kind the payout kind (required)
 * @param payee who is paid (required)
 * @param bookingId the related booking id (value), or {@code null}
 * @param originRef the origin obligation reference — required for a REFUND (BR7)
 * @param amount the amount in its original currency (required)
 * @param settlementRate the BRL settlement rate when foreign (scale 6, &gt; 0), or {@code null}
 * @param installmentCount the number of equal installments to split into, or {@code null}
 * @param installments an explicit installment plan (due date + amount each), or {@code null}
 */
public record CreatePayoutRequest(
    @NotNull PayoutKind kind,
    @NotNull @Valid PayeeRequest payee,
    String bookingId,
    String originRef,
    @NotNull Money amount,
    BigDecimal settlementRate,
    Integer installmentCount,
    List<@Valid InstallmentRequest> installments) {

  /**
   * The payee of a payout.
   *
   * @param id the payee's external id (required)
   * @param type the payee kind (required)
   */
  public record PayeeRequest(@NotNull String id, @NotNull PayeeType type) {}

  /**
   * One explicit installment.
   *
   * @param dueDate the installment due date (required)
   * @param amount the installment amount (required)
   */
  public record InstallmentRequest(@NotNull LocalDate dueDate, @NotNull Money amount) {}

  /** The explicit due dates of the plan, or {@code null} when none/equal-split with defaults. */
  public List<LocalDate> dueDates() {
    return installments == null
        ? null
        : installments.stream().map(InstallmentRequest::dueDate).toList();
  }

  /** The explicit per-installment amounts, or {@code null} when an equal split is wanted. */
  public List<Money> amounts() {
    return installments == null
        ? null
        : installments.stream().map(InstallmentRequest::amount).toList();
  }
}
