package com.fksoft.domain.intelligence.internal;

import com.fksoft.domain.intelligence.IntelligenceService;
import com.fksoft.domain.quoting.PriceOverridden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal, read-only consumer of the quoting {@code PriceOverridden} event for the
 * OverrideNudge seam (SPEC-0013 BR6, DL-0036). It consumes only the EXPOSED event type of the
 * quoting module and forwards it to {@link IntelligenceService}, which short-circuits while the
 * OverrideNudge feature flag is off (the default, until the commission-tier model exists). It NEVER
 * calls back into quoting — intelligence advises, never commands (BR2).
 */
@Component
@RequiredArgsConstructor
class OverrideEventsListener {

  private final IntelligenceService intelligenceService;

  @EventListener
  void onPriceOverridden(PriceOverridden event) {
    intelligenceService.onPriceOverridden(event.quoteId());
  }
}
