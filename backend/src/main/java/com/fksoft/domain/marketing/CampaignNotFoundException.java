package com.fksoft.domain.marketing;

import com.fksoft.domain.error.DomainException;

/**
 * Raised when a campaign is looked up by an id that does not exist (SPEC-0019). Mapped to {@code
 * 404 Not Found}.
 */
public class CampaignNotFoundException extends DomainException {

  public CampaignNotFoundException() {
    super("marketing.campaign.not-found");
  }
}
