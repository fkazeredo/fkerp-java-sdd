package com.fksoft.domain.commercialpolicy;

/**
 * Port that supplies the markup to apply when composing a quote (consumed by Quoting, SPEC-0005). A
 * real port (another module depends on it). <strong>Graduated by SPEC-0014</strong>: the Phase-1
 * stub that always returned the system default is replaced by the governed precedence engine
 * (Directive &gt; Promotion &gt; Contract &gt; Policy &gt; Default). The decision now reflects the
 * <em>winning</em> layer in its {@code source}; with no rule above the default it still returns a
 * zero markup with source {@code SYSTEM_DEFAULT} (back-compat, DL-0040). Quoting stays unaware of
 * precedence — it just asks for the markup at a scope.
 */
public interface MarkupProvider {

  /**
   * The markup decision for a given scope: resolves {@code MARKUP_PCT} through the precedence
   * engine (SPEC-0014 BR2).
   *
   * @param scope the account/product/channel the quote is for
   * @return the governed markup with the winning layer as its source
   */
  MarkupDecision currentMarkup(MarkupScope scope);

  /**
   * The markup decision at global scope. Defaults to {@link #currentMarkup(MarkupScope)} with
   * {@link MarkupScope#global()} so existing callers keep working unchanged (DL-0040).
   *
   * @return the governed markup at global scope
   */
  default MarkupDecision currentMarkup() {
    return currentMarkup(MarkupScope.global());
  }
}
