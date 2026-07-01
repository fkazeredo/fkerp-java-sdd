package com.fksoft.domain.assets;

import com.fksoft.domain.money.Money;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to register an internal asset (SPEC-0021 BR1/BR2). The {@code documentId} and {@code
 * financeEntryId} reference the Compliance vault and the Finance ledger by value (never an FK).
 *
 * @param type the asset-type cadastro code (required)
 * @param identifier the human-readable identification/description (required)
 * @param acquisitionDate when it was acquired (required)
 * @param acquisitionCost the acquisition cost (required, Money)
 * @param expiresAt the license expiry date — required for the {@code SOFTWARE_LICENSE} code (BR1)
 * @param supplierRef the supplier reference (value), or {@code null}
 * @param documentId the Compliance document id (value), or {@code null}
 * @param financeEntryId the Finance cost ledger entry id (value), or {@code null}
 */
public record RegisterAssetCommand(
    String type,
    String identifier,
    LocalDate acquisitionDate,
    Money acquisitionCost,
    LocalDate expiresAt,
    String supplierRef,
    UUID documentId,
    UUID financeEntryId) {}
