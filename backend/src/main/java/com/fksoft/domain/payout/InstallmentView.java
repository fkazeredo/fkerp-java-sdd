package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of a payout installment (SPEC-0017 BR6). Each installment executes and is
 * receipted individually; {@code proofDocumentId} is filled once EXECUTED.
 *
 * @param id the installment id
 * @param seq the 1-based sequence within the payout
 * @param dueDate the due date
 * @param amount the installment amount
 * @param status the installment lifecycle status
 * @param executedAt when it executed, or {@code null}
 * @param proofDocumentId the archived receipt document id, or {@code null} while not executed
 */
public record InstallmentView(
    UUID id,
    int seq,
    LocalDate dueDate,
    Money amount,
    PayoutStatus status,
    Instant executedAt,
    UUID proofDocumentId) {}
