package com.fksoft.domain.admin;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of an administrative contract (SPEC-0025). The delivery layer returns this
 * record, never the {@code AdminContract} entity.
 *
 * @param id the contract id
 * @param supplierId the supplier the contract covers
 * @param validFrom the start of validity
 * @param validUntil the end of validity, or {@code null} when open-ended
 * @param recurrence the recurring-charge cadence cadastro code (was {@code AdminRecurrence}), or
 *     {@code null}
 * @param amount the recurring amount (Money), or {@code null}
 * @param documentId the contract document id in the Compliance vault (value), or {@code null}
 * @param createdAt when it was registered
 */
public record AdminContractView(
    UUID id,
    UUID supplierId,
    LocalDate validFrom,
    LocalDate validUntil,
    String recurrence,
    Money amount,
    UUID documentId,
    Instant createdAt) {}
