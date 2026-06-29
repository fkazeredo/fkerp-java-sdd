package com.fksoft.infra.integration.newsletter;

import com.fksoft.domain.marketing.NewsletterException;
import com.fksoft.domain.marketing.NewsletterMessage;
import com.fksoft.domain.marketing.NewsletterSendResult;
import com.fksoft.domain.marketing.NewsletterSender;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The Anti-Corruption Layer adapter for the external newsletter provider (SPEC-0019 Scope;
 * DL-0055), implementing the domain {@link NewsletterSender}. It translates the domain {@link
 * NewsletterMessage} into the provider request ({@link NewsletterProviderRequest}), "dispatches"
 * it, validates the {@link NewsletterProviderResponse} and translates the accepted reference back
 * to the domain {@link NewsletterSendResult}. The vendor shape stays in this package (an ArchUnit
 * boundary test proves it never reaches the domain).
 *
 * <p>The real provider (Mailchimp/RD/SES…) is out of scope; this is the <strong>traceable
 * mock</strong> of that integration ({@code simulation-and-mocking.md}). It is deterministic and
 * supports <strong>fault injection</strong> via a recognizable recipient prefix so tests can
 * exercise the failure paths without a live dependency: a recipient starting with {@code
 * "FAIL_TIMEOUT"} → TIMEOUT, {@code "FAIL_UNAVAILABLE"} → UNAVAILABLE, {@code "FAIL_REJECT"} →
 * REJECTED. Every call logs an integration line with no recipient PII beyond the masked handle.
 */
@Slf4j
@Component
public class SimulatedNewsletterSender implements NewsletterSender {

  @Override
  public NewsletterSendResult send(NewsletterMessage message) {
    long started = System.nanoTime();
    // 1) Translate the domain message to the provider request (ACL).
    NewsletterProviderRequest request = toProviderRequest(message);
    // 2) "Dispatch" and get the external response (the live client is out of scope — DL-0055).
    NewsletterProviderResponse response = dispatch(message.recipientRef(), request);
    // 3) Validate and translate back to the domain result.
    if (!response.accepted() || response.providerMessageId() == null) {
      throw new NewsletterException(NewsletterException.Kind.REJECTED);
    }
    long latencyMs = (System.nanoTime() - started) / 1_000_000;
    log.info(
        "NewsletterDispatched campaignId={} latencyMs={} providerRef={}",
        message.campaignId(),
        latencyMs,
        response.providerMessageId());
    return new NewsletterSendResult(response.providerMessageId());
  }

  private NewsletterProviderRequest toProviderRequest(NewsletterMessage message) {
    return new NewsletterProviderRequest(
        message.campaignId() + ":" + message.recipientRef(),
        message.recipientRef(),
        message.contentRef());
  }

  /**
   * Deterministic mock dispatch with fault injection by recipient prefix (DL-0055). A real adapter
   * would call the provider's API here, with a timeout and retry/circuit-breaker as configured.
   */
  private NewsletterProviderResponse dispatch(
      String recipientRef, NewsletterProviderRequest request) {
    if (recipientRef.startsWith("FAIL_TIMEOUT")) {
      throw new NewsletterException(NewsletterException.Kind.TIMEOUT);
    }
    if (recipientRef.startsWith("FAIL_UNAVAILABLE")) {
      throw new NewsletterException(NewsletterException.Kind.UNAVAILABLE);
    }
    if (recipientRef.startsWith("FAIL_REJECT")) {
      return new NewsletterProviderResponse(false, null);
    }
    return new NewsletterProviderResponse(true, "msg-" + UUID.randomUUID());
  }
}
