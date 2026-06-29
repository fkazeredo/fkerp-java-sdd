package com.fksoft.domain.marketing;

import java.util.Map;

/**
 * Command to define a segment (SPEC-0019 BR3). The criteria are validated against the closed
 * catalog by {@link SegmentCriteria}.
 *
 * @param name the segment name (required)
 * @param criteria the raw criteria field map (validated on use)
 */
public record DefineSegmentCommand(String name, Map<String, String> criteria) {

  public DefineSegmentCommand {
    if (name == null || name.isBlank()) {
      throw new SegmentInvalidException();
    }
    name = name.trim();
  }
}
