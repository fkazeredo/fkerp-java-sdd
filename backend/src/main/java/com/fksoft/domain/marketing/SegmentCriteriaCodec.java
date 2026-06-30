package com.fksoft.domain.marketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serializes the validated {@link SegmentCriteria} to/from the {@code criteria_json} jsonb column
 * (SPEC-0019 Persistence; DL-0059). The criteria are a small validated map of allowed field→value;
 * a single shared {@link ObjectMapper} renders the canonical (sorted) JSON object. Decoding re-runs
 * the closed-catalog validation through the {@link SegmentCriteria} constructor, so a malformed
 * stored value surfaces as the domain's {@code marketing.segment.invalid} error rather than a raw
 * parse exception.
 */
final class SegmentCriteriaCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<TreeMap<String, String>> MAP_TYPE = new TypeReference<>() {};

  private SegmentCriteriaCodec() {}

  /** Encodes the validated criteria to a canonical JSON object string. */
  static String encode(SegmentCriteria criteria) {
    try {
      return MAPPER.writeValueAsString(new TreeMap<>(criteria.fields()));
    } catch (JsonProcessingException badJson) {
      throw new SegmentInvalidException();
    }
  }

  /** Decodes the stored JSON object back into validated criteria (re-running the catalog check). */
  static SegmentCriteria decode(String json) {
    if (json == null || json.isBlank()) {
      throw new SegmentInvalidException();
    }
    try {
      Map<String, String> fields = MAPPER.readValue(json, MAP_TYPE);
      return new SegmentCriteria(fields);
    } catch (JsonProcessingException badJson) {
      throw new SegmentInvalidException();
    }
  }
}
