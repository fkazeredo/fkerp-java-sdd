package com.fksoft.domain.portfolio;

import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes the reference commercial {@code terms} of a representation contract to/from the {@code
 * terms_json} jsonb column (SPEC-0020 Persistence). The terms are a small free map of string→string
 * (reference conditions, not prices — BR6); a single shared {@link ObjectMapper} renders the
 * canonical (sorted) JSON object. {@code null}/empty terms encode to {@code null} (no jsonb
 * stored).
 */
final class TermsCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<TreeMap<String, String>> MAP_TYPE = new TypeReference<>() {};

  private TermsCodec() {}

  /** Encodes the terms to a canonical JSON object string, or {@code null} when empty. */
  static String encode(Map<String, String> terms) {
    if (terms == null || terms.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(new TreeMap<>(terms));
    } catch (JacksonException badJson) {
      throw new RepresentationContractInvalidException();
    }
  }

  /** Decodes the stored JSON object back into a terms map (empty when {@code null}). */
  static Map<String, String> decode(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, MAP_TYPE);
    } catch (JacksonException badJson) {
      throw new RepresentationContractInvalidException();
    }
  }
}
