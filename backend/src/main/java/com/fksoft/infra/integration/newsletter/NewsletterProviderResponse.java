package com.fksoft.infra.integration.newsletter;

/**
 * The external newsletter provider's response shape (SPEC-0019; DL-0055). The <strong>vendor
 * DTO</strong>: confined to this ACL package, never crossing into the domain. The adapter validates
 * it and translates the accepted reference to the domain {@code NewsletterSendResult}.
 *
 * @param accepted whether the provider accepted the message
 * @param providerMessageId the provider's opaque message id when accepted
 */
record NewsletterProviderResponse(boolean accepted, String providerMessageId) {}
