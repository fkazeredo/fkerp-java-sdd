package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.CreateCampaignCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/marketing/campaigns} (SPEC-0019).
 *
 * @param segmentId the targeted segment id
 * @param code the public attribution code (unique)
 * @param contentRef pointer to the external creative, or {@code null}
 * @param windowFrom start of the send window, or {@code null}
 * @param windowTo end of the send window, or {@code null}
 */
public record CreateCampaignRequest(
    @NotNull UUID segmentId,
    @NotBlank String code,
    String contentRef,
    LocalDate windowFrom,
    LocalDate windowTo) {

  /** Translates this request to the domain command. */
  public CreateCampaignCommand toCommand() {
    return new CreateCampaignCommand(segmentId, code, contentRef, windowFrom, windowTo);
  }
}
