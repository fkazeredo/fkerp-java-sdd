package com.fksoft.domain.marketing;

import java.util.UUID;

/**
 * Command to register a campaign→booking attribution intake (SPEC-0019 BR5; DL-0057): the carrier
 * of the campaign code (landing page/UTM/agent) declares that a booking originated from a campaign.
 * The conversion is only <em>confirmed</em> later when the booking is confirmed.
 *
 * @param campaignCode the campaign's public code (required)
 * @param bookingId the booking the code is attributed to (required)
 */
public record RegisterAttributionCommand(String campaignCode, UUID bookingId) {

  public RegisterAttributionCommand {
    if (campaignCode == null || campaignCode.isBlank() || bookingId == null) {
      throw new CampaignInvalidException();
    }
    campaignCode = campaignCode.trim();
  }
}
