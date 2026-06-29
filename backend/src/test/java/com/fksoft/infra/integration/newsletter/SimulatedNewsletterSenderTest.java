package com.fksoft.infra.integration.newsletter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.marketing.NewsletterException;
import com.fksoft.domain.marketing.NewsletterMessage;
import com.fksoft.domain.marketing.NewsletterSendResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the newsletter ACL adapter (SPEC-0019; DL-0055): a normal recipient is accepted
 * and returns a provider reference; the recognizable fault-injection prefixes classify the failure
 * (TIMEOUT/UNAVAILABLE/REJECTED) so the caller never sees a false "sent".
 */
class SimulatedNewsletterSenderTest {

  private final SimulatedNewsletterSender sender = new SimulatedNewsletterSender();

  @Test
  void acceptsANormalRecipientAndReturnsAProviderRef() {
    NewsletterSendResult result =
        sender.send(new NewsletterMessage(UUID.randomUUID(), "acc-1", "content-1"));
    assertThat(result.providerMessageRef()).startsWith("msg-");
  }

  @Test
  void classifiesTimeoutFailure() {
    assertThatThrownBy(
            () -> sender.send(new NewsletterMessage(UUID.randomUUID(), "FAIL_TIMEOUT_x", "c")))
        .isInstanceOf(NewsletterException.class)
        .extracting(e -> ((NewsletterException) e).kind())
        .isEqualTo(NewsletterException.Kind.TIMEOUT);
  }

  @Test
  void classifiesRejectedFailure() {
    assertThatThrownBy(
            () -> sender.send(new NewsletterMessage(UUID.randomUUID(), "FAIL_REJECT_x", "c")))
        .isInstanceOf(NewsletterException.class)
        .extracting(e -> ((NewsletterException) e).kind())
        .isEqualTo(NewsletterException.Kind.REJECTED);
  }
}
