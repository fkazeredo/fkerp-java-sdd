package com.fksoft.domain.marketing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a campaign is missing required data (segment or code — SPEC-0019). Mapped to {@code
 * 400 Bad Request}.
 */
public class CampaignInvalidException extends DomainException {

  public CampaignInvalidException() {
    super("marketing.campaign.invalid");
  }
}
