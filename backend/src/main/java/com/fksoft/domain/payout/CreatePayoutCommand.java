package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Command to create a payout (SPEC-0017 {@code POST /api/payouts}). Carries the kind, payee, the
 * amount in its original currency and — when the currency is foreign — the {@code settlementRate}
 * (scale 6, &gt; 0; BR1, DL-0049) so the BRL settled amount can be derived. The {@code originRef}
 * is mandatory for a {@code REFUND} (BR7). The installment plan is optional (DL-0050): give an
 * explicit {@code installments} list OR an {@code installmentCount} OR neither (à vista).
 *
 * @param kind the payout kind
 * @param payee who is paid
 * @param bookingId the related booking (value), or {@code null}
 * @param originRef the origin obligation reference — required for REFUND (BR7), else {@code null}
 * @param amount the amount in its original currency (e.g. USD for a foreign settlement)
 * @param settlementRate the BRL settlement rate (scale 6, &gt; 0) when foreign, else {@code null}
 * @param installmentCount the number of equal installments to split into, or {@code null}
 * @param installmentDueDates the due dates of the installments (size == count or == explicit size)
 * @param installmentAmounts an explicit per-installment amount list, or {@code null} for equal
 *     split
 */
public record CreatePayoutCommand(
    PayoutKind kind,
    Payee payee,
    String bookingId,
    String originRef,
    Money amount,
    BigDecimal settlementRate,
    Integer installmentCount,
    List<LocalDate> installmentDueDates,
    List<Money> installmentAmounts) {}
