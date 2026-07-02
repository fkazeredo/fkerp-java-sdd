package com.fksoft.domain.aftersales;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an after-sales case was opened (SPEC-0018 Events). Published in-process by the
 * AfterSales module. Consumed by Intelligence (cost-to-serve / product-supplier signals).
 *
 * @param caseId the opened case id
 * @param bookingId the referenced booking (value)
 * @param type the case type
 * @param occurredAt when it was opened
 */
public record SupportCaseOpened(UUID caseId, String bookingId, String type, Instant occurredAt) {}
