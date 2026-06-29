package com.fksoft.domain.aftersales;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an after-sales case was resolved (SPEC-0018 Events; BR5). Published in-process by
 * the AfterSales module. Consumed by Intelligence to attribute the cost-to-serve to the real margin
 * (it carries the total cost so the DSS does not need to read AfterSales state).
 *
 * @param caseId the resolved case id
 * @param bookingId the referenced booking (value)
 * @param type the case type
 * @param resolution the outcome
 * @param costToServeTotal the total cost-to-serve attributed to the case (BRL)
 * @param occurredAt when it was resolved
 */
public record SupportCaseResolved(
    UUID caseId,
    String bookingId,
    SupportCaseType type,
    CaseResolution resolution,
    Money costToServeTotal,
    Instant occurredAt) {}
