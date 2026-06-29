package com.fksoft.domain.marketing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the external newsletter provider fails (SPEC-0019; DL-0055;
 * messaging-and-integrations.md "classify failures"). The adapter classifies the provider failure
 * (TIMEOUT/UNAVAILABLE/REJECTED) and raises this so the caller never sees a false "sent". Mapped to
 * {@code 502 Bad Gateway}. The message never leaks recipient PII.
 */
public class NewsletterException extends DomainException {

  /** Classified failure kinds at the newsletter ACL boundary. */
  public enum Kind {
    TIMEOUT,
    UNAVAILABLE,
    REJECTED
  }

  private final transient Kind kind;

  public NewsletterException(Kind kind) {
    super("marketing.newsletter.failure");
    this.kind = kind;
  }

  /** The classified failure kind. */
  public Kind kind() {
    return kind;
  }
}
