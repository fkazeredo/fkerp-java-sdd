package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/aftersales/cases} (SPEC-0018): opens a support case referencing
 * a booking (value), with a type and an optional summary. SLA deadlines are derived server-side
 * from the governed policy (BR1/DL-0052), not supplied here.
 *
 * @param bookingId the referenced booking id (required)
 * @param type the case type (required)
 * @param summary an optional human-readable summary
 */
public record OpenCaseRequest(@NotBlank String bookingId, @NotBlank String type, String summary) {}
