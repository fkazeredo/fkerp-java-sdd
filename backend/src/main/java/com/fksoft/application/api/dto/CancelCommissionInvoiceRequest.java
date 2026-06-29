package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/billing/invoices/{id}/cancel} (SPEC-0016 BR6): cancels an
 * issued NFS-e at the municipality and frees the commission for a re-issue.
 *
 * @param reason the cancellation reason (required)
 */
public record CancelCommissionInvoiceRequest(@NotBlank String reason) {}
