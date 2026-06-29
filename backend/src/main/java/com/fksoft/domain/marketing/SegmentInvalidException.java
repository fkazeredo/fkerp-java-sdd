package com.fksoft.domain.marketing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a segment's criteria are unknown or malformed (SPEC-0019 BR3; DL-0059): a field not
 * in the closed catalog, or a blank/missing value. Mapped to {@code 400 Bad Request}.
 */
public class SegmentInvalidException extends DomainException {

  public SegmentInvalidException() {
    super("marketing.segment.invalid");
  }
}
