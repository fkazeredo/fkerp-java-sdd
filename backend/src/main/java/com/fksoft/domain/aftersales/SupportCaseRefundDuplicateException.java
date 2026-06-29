package com.fksoft.domain.aftersales;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a refund encaminhamento would create a second Payout for a case that already has a
 * linked refund (SPEC-0018 Error Behavior {@code aftersales.refund.duplicate}; BR3/DL-0054 — no
 * double refund). Mapped to {@code 409 Conflict}.
 */
public class SupportCaseRefundDuplicateException extends DomainException {

  public SupportCaseRefundDuplicateException() {
    super("aftersales.refund.duplicate");
  }
}
