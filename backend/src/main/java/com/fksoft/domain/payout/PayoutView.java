package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public read view of a payout aggregate (SPEC-0017). Carries the kind, payee, the amount in its
 * original currency and — when foreign — the {@code settlementRate} (scale 6) and {@code
 * settledBrl} (the BRL baixa = amount × rate, DL-0049). The {@code proofDocumentId} is the receipt
 * of a single (à vista) payout; for an installment plan each installment carries its own receipt.
 *
 * @param id the payout id
 * @param kind the payout kind (payout-kind cadastro code)
 * @param payee who is paid
 * @param bookingId the related booking (value), or {@code null}
 * @param originRef the origin obligation reference (required for REFUND), or {@code null}
 * @param amount the amount in its original currency
 * @param settlementRate the BRL settlement rate (scale 6) when foreign, or {@code null}
 * @param settledBrl the BRL settled amount (amount × rate) when foreign, or {@code null}
 * @param status the payout lifecycle status (EXECUTED only when every installment is EXECUTED)
 * @param proofDocumentId the receipt document id for a single payout, or {@code null}
 * @param installments the installment plan (always at least one, the implicit single one)
 * @param createdAt when the payout was created
 */
public record PayoutView(
    UUID id,
    String kind,
    Payee payee,
    String bookingId,
    String originRef,
    Money amount,
    BigDecimal settlementRate,
    Money settledBrl,
    PayoutStatus status,
    UUID proofDocumentId,
    List<InstallmentView> installments,
    Instant createdAt) {}
