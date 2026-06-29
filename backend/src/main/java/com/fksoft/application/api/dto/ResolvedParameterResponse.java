package com.fksoft.application.api.dto;

import com.fksoft.domain.commercialpolicy.ResolvedParameter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response of {@code GET /api/commercial-policy/resolve} (SPEC-0014): the winning value plus its
 * provenance (which layer won, who defined it, when) — the contract shown in the spec's
 * Input/Output example.
 *
 * @param key the parameter key
 * @param value the winning value text
 * @param type the value type
 * @param provenance the winning rule's provenance
 */
public record ResolvedParameterResponse(
    String key, String value, String type, ProvenanceResponse provenance) {

  /** Nested provenance block. */
  public record ProvenanceResponse(
      String layer, UUID ruleId, String definedBy, Instant definedAt, LocalDate validUntil) {}

  /** Maps a domain {@link ResolvedParameter} to the response. */
  public static ResolvedParameterResponse from(ResolvedParameter resolved) {
    var p = resolved.provenance();
    return new ResolvedParameterResponse(
        resolved.key().value(),
        resolved.value(),
        resolved.type().name(),
        new ProvenanceResponse(
            p.layer().name(), p.ruleId(), p.definedBy(), p.definedAt(), p.validUntil()));
  }
}
