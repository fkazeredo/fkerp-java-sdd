package com.fksoft.domain.marketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the segment criteria validation (SPEC-0019 BR3; DL-0059): only fields in the
 * closed catalog are accepted (minimization — no new data); unknown fields, blank values and empty
 * criteria are rejected as {@code marketing.segment.invalid}.
 */
class SegmentCriteriaTest {

  @Test
  void acceptsKnownCatalogFields() {
    SegmentCriteria criteria =
        new SegmentCriteria(Map.of("accountType", "AGENCY", "region", "SUDESTE"));
    assertThat(criteria.fields()).containsEntry("accountType", "AGENCY");
    assertThat(criteria.fields()).containsEntry("region", "SUDESTE");
  }

  @Test
  void rejectsUnknownField() {
    Map<String, String> withUnknown = Map.of("ssn", "123"); // not in the catalog (would be new PII)
    assertThatThrownBy(() -> new SegmentCriteria(withUnknown))
        .isInstanceOf(SegmentInvalidException.class);
  }

  @Test
  void rejectsBlankValue() {
    Map<String, String> blank = new HashMap<>();
    blank.put("accountType", "  ");
    assertThatThrownBy(() -> new SegmentCriteria(blank))
        .isInstanceOf(SegmentInvalidException.class);
  }

  @Test
  void rejectsEmptyCriteria() {
    assertThatThrownBy(() -> new SegmentCriteria(Map.of()))
        .isInstanceOf(SegmentInvalidException.class);
  }
}
