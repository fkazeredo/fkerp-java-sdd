package com.fksoft.domain.commercialpolicy;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * <strong>STUB (traceable, replaceable):</strong> Phase-1 implementation of {@link MarkupProvider}
 * that always returns a zero markup with source {@code SYSTEM_DEFAULT} (DL-0009). The governed
 * precedence engine (Directive &gt; Promotion &gt; Contract &gt; Policy &gt; Default) is owned by
 * <strong>SPEC-0014</strong>; this stand-in must be replaced when that spec is implemented
 * (simulation-and-mocking.md). It must never ship as the production policy source.
 */
@Service
public class SystemDefaultMarkupProvider implements MarkupProvider {

  private static final MarkupDecision DEFAULT =
      new MarkupDecision(BigDecimal.ZERO, MarkupDecision.SYSTEM_DEFAULT);

  @Override
  public MarkupDecision currentMarkup() {
    return DEFAULT;
  }
}
