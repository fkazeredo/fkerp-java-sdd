package com.fksoft.domain.commercialpolicy;

/**
 * Port that supplies the markup to apply when composing a quote (consumed by Quoting, SPEC-0005). A
 * real port (another module depends on it) and a deliberate seam: the implementation is a Phase-1
 * stub returning a governed default; SPEC-0014 replaces it with the precedence engine.
 */
public interface MarkupProvider {

  /**
   * The markup decision to apply now.
   *
   * @return the governed markup (Phase 1: always the system default)
   */
  MarkupDecision currentMarkup();
}
