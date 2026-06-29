package com.fksoft.domain.marketing;

/**
 * The provider-agnostic result of asking the {@link NewsletterSender} port to dispatch one message
 * (SPEC-0019; DL-0055): the provider's opaque message reference, for traceability/audit.
 *
 * @param providerMessageRef the provider's reference for the dispatched message
 */
public record NewsletterSendResult(String providerMessageRef) {}
