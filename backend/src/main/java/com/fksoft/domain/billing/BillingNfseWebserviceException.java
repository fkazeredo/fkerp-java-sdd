package com.fksoft.domain.billing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when the municipal NFS-e webservice could not be reached or did not respond usably
 * (SPEC-0016 BR7): a {@link NfseFailureClass#TIMEOUT} or {@link NfseFailureClass#UNAVAILABLE} from
 * the ACL. Mapped to {@code 502 Bad Gateway} — never reported as issued. The transport failure
 * class is carried as a message argument for observability (never sensitive data).
 */
public class BillingNfseWebserviceException extends DomainException {

  private final transient NfseFailureClass failureClass;

  /**
   * @param failureClass the transport failure classification (TIMEOUT/UNAVAILABLE)
   */
  public BillingNfseWebserviceException(NfseFailureClass failureClass) {
    super("billing.nfse.webservice-failure", failureClass.name());
    this.failureClass = failureClass;
  }

  /** The transport failure classification. */
  public NfseFailureClass failureClass() {
    return failureClass;
  }
}
