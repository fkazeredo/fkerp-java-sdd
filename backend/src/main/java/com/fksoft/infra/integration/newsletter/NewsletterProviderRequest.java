package com.fksoft.infra.integration.newsletter;

/**
 * The external newsletter provider's request shape (SPEC-0019; DL-0055). This is the <strong>vendor
 * DTO</strong>: it lives only in this ACL package and must never cross into the domain (an ArchUnit
 * boundary test proves it). A real provider (Mailchimp/RD/SES…) would have its own list id,
 * template id and merge fields; this mock keeps a minimal shape.
 *
 * @param listMessageId the provider's correlation of campaign+recipient
 * @param toRecipient the provider-facing recipient handle
 * @param templateRef the provider-facing template/content reference
 */
record NewsletterProviderRequest(String listMessageId, String toRecipient, String templateRef) {}
