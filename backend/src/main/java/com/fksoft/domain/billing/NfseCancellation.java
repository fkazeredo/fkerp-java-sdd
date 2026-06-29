package com.fksoft.domain.billing;

import java.util.UUID;

/**
 * Domain request to cancel an issued NFS-e at the municipality (SPEC-0016 BR6).
 *
 * @param invoiceId the invoice whose NFS-e is being cancelled
 * @param number the municipal NFS-e number to cancel
 * @param reason the cancellation reason
 */
public record NfseCancellation(UUID invoiceId, String number, String reason) {}
