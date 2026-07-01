package com.fksoft.domain.admin;

import com.fksoft.domain.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to register an administrative contract for a supplier (SPEC-0025 BR2). The {@code
 * documentId} references the contract document already stored in the Compliance vault (value).
 *
 * @param validFrom the start of validity (required)
 * @param validUntil the end of validity, or {@code null} when open-ended
 * @param recurrence the recurring-charge cadence cadastro code, or {@code null}
 * @param amount the recurring amount (Money), or {@code null}
 * @param documentId the Compliance document id (value), or {@code null}
 */
public record RegisterContractCommand(
    LocalDate validFrom,
    LocalDate validUntil,
    String recurrence,
    Money amount,
    UUID documentId) {}
