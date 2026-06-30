package com.fksoft.domain.portfolio;

import java.time.Instant;

/**
 * Business fact: a representation contract was registered for a brand (SPEC-0020 Events). Published
 * in-process. Consumers: Intelligence (supplier leverage) and CommercialPolicy (scope). Carries the
 * brandRef (value) — not personal data.
 *
 * @param brandRef the brand the contract covers (value)
 * @param occurredAt when the contract was registered
 */
public record RepresentationContractRegistered(String brandRef, Instant occurredAt) {}
