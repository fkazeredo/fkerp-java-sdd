package com.fksoft.domain.portfolio;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a represented brand (SPEC-0020).
 *
 * @param id the brand id
 * @param brandRef the unique brand/supplier identifier (value)
 * @param displayName the human-readable name
 * @param status ACTIVE or INACTIVE
 * @param createdAt when it was registered
 * @param updatedAt when it was last changed
 */
public record BrandView(
    UUID id,
    String brandRef,
    String displayName,
    BrandStatus status,
    Instant createdAt,
    Instant updatedAt) {}
