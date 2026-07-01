package com.fksoft.domain.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of an administrative supplier (SPEC-0025). The delivery layer returns this
 * record, never the {@code AdminSupplier} entity (the model stays inside the module).
 *
 * @param id the supplier id
 * @param type the supplier-type cadastro code (was {@code AdminSupplierType}; SPEC-0031)
 * @param identifier the legal identifier (CNPJ/CPF), or {@code null}
 * @param displayName the display name
 * @param status ACTIVE or INACTIVE
 * @param createdAt when it was registered
 */
public record AdminSupplierView(
    UUID id,
    String type,
    String identifier,
    String displayName,
    AdminSupplierStatus status,
    Instant createdAt) {}
