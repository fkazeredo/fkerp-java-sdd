package com.fksoft.domain.aftersales;

/**
 * Command to open an after-sales case (SPEC-0018 {@code POST /api/aftersales/cases}). Carries the
 * referenced booking (value, never an FK — Modulith), the case type and an optional human summary.
 * The SLA deadlines are derived from the type and the governed policy at open time (BR1, DL-0052),
 * not supplied here.
 *
 * @param bookingId the referenced booking id (value, required)
 * @param type the case type (required)
 * @param summary an optional human-readable summary
 */
public record OpenCaseCommand(String bookingId, SupportCaseType type, String summary) {}
