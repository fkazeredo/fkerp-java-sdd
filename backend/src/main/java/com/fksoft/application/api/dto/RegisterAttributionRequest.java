package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.RegisterAttributionCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/marketing/attribution} (SPEC-0019 BR5): links a campaign code
 * to a booking.
 *
 * @param campaignCode the campaign's public code
 * @param bookingId the booking the code is attributed to
 */
public record RegisterAttributionRequest(@NotBlank String campaignCode, @NotNull UUID bookingId) {

  /** Translates this request to the domain command. */
  public RegisterAttributionCommand toCommand() {
    return new RegisterAttributionCommand(campaignCode, bookingId);
  }
}
