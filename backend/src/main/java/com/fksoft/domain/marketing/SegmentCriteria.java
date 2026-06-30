package com.fksoft.domain.marketing;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The validated criteria of a {@link com.fksoft.domain.marketing.Segment} (SPEC-0019 BR3; DL-0059):
 * a small map of <strong>allowed field → value</strong> over data that already exists in the
 * commercial base (minimization — no new personal data is collected). The set of allowed fields is
 * a <strong>closed catalog</strong> ({@link #ALLOWED_FIELDS}); an unknown field makes the criteria
 * invalid ({@link SegmentInvalidException}), so the jsonb column never becomes a free-form bag and
 * never authorizes collecting something new.
 *
 * <p>The shape is intentionally a flat {@code Map<String,String>} (e.g. {@code
 * accountType=AGENCY}): the v1 segments are simple predicates over existing attributes. Richer
 * expressions (ranges, boolean trees) are a future, additive evolution — Rule Zero keeps v1
 * minimal.
 *
 * @param fields the validated criteria fields (kept sorted for a stable serialization)
 */
public record SegmentCriteria(Map<String, String> fields) {

  /**
   * The closed catalog of allowed criteria fields (DL-0059). Each maps to an attribute the
   * marketing base already knows or can read for a projection — never a request to collect new
   * personal data.
   */
  public static final Set<String> ALLOWED_FIELDS =
      Set.of("accountType", "purpose", "minVolume", "route", "region");

  public SegmentCriteria {
    if (fields == null) {
      throw new SegmentInvalidException();
    }
    Map<String, String> normalized = new TreeMap<>();
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      String key = entry.getKey();
      if (key == null || !ALLOWED_FIELDS.contains(key)) {
        throw new SegmentInvalidException();
      }
      if (entry.getValue() == null || entry.getValue().isBlank()) {
        throw new SegmentInvalidException();
      }
      normalized.put(key, entry.getValue().trim());
    }
    if (normalized.isEmpty()) {
      throw new SegmentInvalidException();
    }
    fields = Map.copyOf(normalized);
  }
}
