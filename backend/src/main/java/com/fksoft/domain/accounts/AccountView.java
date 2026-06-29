package com.fksoft.domain.accounts;

import java.time.Instant;
import java.util.UUID;

/**
 * Read view of an account returned by {@link AccountService} to the delivery layer, so the entity
 * never leaves the module (delivery is entity-free). The delivery layer maps this to its response
 * DTO.
 *
 * @param id account id
 * @param legalType legal type
 * @param documentNumber normalized document digits
 * @param displayName commercial display name
 * @param status lifecycle status
 * @param cadastur optional CADASTUR registration (stored as provided)
 * @param iata optional IATA registration (stored as provided)
 * @param createdAt creation instant (UTC)
 */
public record AccountView(
    UUID id,
    LegalType legalType,
    String documentNumber,
    String displayName,
    AccountStatus status,
    String cadastur,
    String iata,
    Instant createdAt) {}
