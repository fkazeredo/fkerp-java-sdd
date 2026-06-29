package com.fksoft.domain.marketing;

/**
 * Domain port to the external newsletter provider (SPEC-0019 Scope "disparo via porta de newsletter
 * — ACL"; DL-0055). It is an Anti-Corruption Layer boundary (ADR 0007, mirroring {@code
 * EmailSender}): implementations live in {@code com.fksoft.infra.integration.newsletter}, translate
 * the provider's shape to/from these domain types, apply a timeout and classify failures. The
 * provider DTO never crosses into the domain (enforced by an ArchUnit boundary test).
 *
 * <p>The shipped adapter is a <strong>traceable mock</strong> ({@code SimulatedNewsletterSender});
 * a real provider (Mailchimp/RD/SES…) is a new adapter, no domain change. Consent is checked
 * <strong>before</strong> a message ever reaches this port (BR2), so the sender only ever receives
 * recipients that consented.
 */
public interface NewsletterSender {

  /**
   * Dispatches one newsletter message to one consented recipient (BR2/BR4). Returns the provider's
   * opaque reference on success.
   *
   * @param message the domain-shaped message (campaign, recipient, content pointer)
   * @return the provider's message reference (for traceability)
   * @throws NewsletterException when the provider leg fails — classified, so the caller never sees
   *     a false "sent"
   */
  NewsletterSendResult send(NewsletterMessage message);
}
