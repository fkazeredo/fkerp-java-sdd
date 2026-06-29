package com.fksoft.infra.integration.payment;

/**
 * The <strong>external</strong> payment-provider webhook shape (ADR 0006; DL-0048). This is the
 * vendor DTO of the payment ACL: it stays entirely in {@code infra.integration.payment} and is
 * translated to domain calls by the adapter — it never crosses into the domain (enforced by an
 * ArchUnit boundary test). A real provider's webhook would map onto this same shape.
 *
 * @param providerRef the provider's payment reference
 * @param payoutId the payout id (as text, the provider's metadata)
 * @param installmentSeq the 1-based installment sequence (provider metadata)
 * @param status the provider's terminal status (e.g. {@code SUCCEEDED}/{@code FAILED})
 */
public record PaymentWebhookPayload(
    String providerRef, String payoutId, int installmentSeq, String status) {}
