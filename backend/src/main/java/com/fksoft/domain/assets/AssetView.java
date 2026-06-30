package com.fksoft.domain.assets;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of an internal asset (SPEC-0021). The delivery layer returns this record, never
 * the {@code Asset} entity (the model stays inside the module).
 *
 * @param id the asset id
 * @param type the asset type
 * @param identifier the human-readable identification/description
 * @param status ACTIVE or RETIRED
 * @param acquisitionDate when it was acquired
 * @param acquisitionCost the acquisition cost (Money)
 * @param expiresAt the license expiry date, or {@code null} for non-licenses
 * @param supplierRef the supplier reference (value), or {@code null}
 * @param documentId the acquisition/contract document id in the Compliance vault (value), or {@code
 *     null}
 * @param financeEntryId the cost ledger entry id in Finance (value), or {@code null}
 * @param retiredAt when it was retired, or {@code null} while ACTIVE
 * @param retiredBy who retired it, or {@code null} while ACTIVE
 * @param retirementReason the retirement reason, or {@code null} while ACTIVE
 * @param createdAt when it was registered
 */
public record AssetView(
    UUID id,
    AssetType type,
    String identifier,
    AssetStatus status,
    LocalDate acquisitionDate,
    Money acquisitionCost,
    LocalDate expiresAt,
    String supplierRef,
    UUID documentId,
    UUID financeEntryId,
    Instant retiredAt,
    String retiredBy,
    String retirementReason,
    Instant createdAt) {}
