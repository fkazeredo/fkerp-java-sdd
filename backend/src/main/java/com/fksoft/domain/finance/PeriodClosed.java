package com.fksoft.domain.finance;

import java.time.Instant;

/**
 * Business fact: an accounting period was closed (SPEC-0015 Events). Published in-process once the
 * Compliance veto passed; consumed by Billing, Intelligence and Platform (audit).
 *
 * @param period the closed period ({@code YYYY-MM})
 * @param occurredAt when it was closed
 */
public record PeriodClosed(String period, Instant occurredAt) {}
