package com.fksoft.domain.marketing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Command to create a campaign over a segment (SPEC-0019). The {@code code} is the public
 * attribution token (UTM) and is unique across campaigns.
 *
 * @param segmentId the targeted segment id (value)
 * @param code the public attribution code (required, unique)
 * @param contentRef pointer to the external creative, or {@code null}
 * @param windowFrom start of the send window, or {@code null}
 * @param windowTo end of the send window, or {@code null}
 */
public record CreateCampaignCommand(
    UUID segmentId, String code, String contentRef, LocalDate windowFrom, LocalDate windowTo) {

  public CreateCampaignCommand {
    if (segmentId == null || code == null || code.isBlank()) {
      throw new CampaignInvalidException();
    }
    code = code.trim();
  }
}
