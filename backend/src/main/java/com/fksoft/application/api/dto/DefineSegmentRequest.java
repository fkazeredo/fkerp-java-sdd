package com.fksoft.application.api.dto;

import com.fksoft.domain.marketing.DefineSegmentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request body for {@code POST /api/marketing/segments} (SPEC-0019 BR3). The criteria are a flat
 * map validated against the closed catalog (DL-0059).
 *
 * @param name the segment name
 * @param criteria the criteria field map (e.g. {@code {"accountType":"AGENCY"}})
 */
public record DefineSegmentRequest(@NotBlank String name, @NotNull Map<String, String> criteria) {

  /** Translates this request to the domain command. */
  public DefineSegmentCommand toCommand() {
    return new DefineSegmentCommand(name, criteria);
  }
}
