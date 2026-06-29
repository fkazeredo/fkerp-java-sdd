package com.fksoft.domain.intelligence.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes the provenance {@code sources} list (the event types backing an insight's numbers)
 * to/from a compact comma-separated text form. Stored as text rather than a jsonb column on purpose
 * (Rule Zero): it is a short list of stable identifiers with no cross-database JSON query need
 * (same posture as the booking penalty-windows codec).
 */
final class SourcesCodec {

  private SourcesCodec() {}

  /** Encodes the sources to the compact text form (empty string for none). */
  static String encode(List<String> sources) {
    if (sources == null || sources.isEmpty()) {
      return "";
    }
    return String.join(",", sources);
  }

  /** Decodes the compact text form back into a list (empty/null ⇒ empty list). */
  static List<String> decode(String encoded) {
    List<String> sources = new ArrayList<>();
    if (encoded == null || encoded.isBlank()) {
      return sources;
    }
    for (String source : encoded.split(",")) {
      String trimmed = source.trim();
      if (!trimmed.isEmpty()) {
        sources.add(trimmed);
      }
    }
    return sources;
  }
}
