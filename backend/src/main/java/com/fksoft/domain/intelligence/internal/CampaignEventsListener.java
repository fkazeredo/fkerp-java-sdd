package com.fksoft.domain.intelligence.internal;

import com.fksoft.domain.intelligence.IntelligenceService;
import com.fksoft.domain.marketing.CampaignConverted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Module-internal, read-only consumer of the Marketing {@link CampaignConverted} event (SPEC-0019
 * BR5; redesign 8.2-F). It consumes only the EXPOSED event type of the producing module ({@code
 * marketing}) and forwards it to {@link IntelligenceService}, which only reads the signal — it
 * NEVER calls back into Marketing. Intelligence is a consumer-leaf that advises, never commands
 * (SPEC-0013 BR2), so this keeps the "advises, never commands" ArchUnit rule green.
 */
@Component
@RequiredArgsConstructor
class CampaignEventsListener {

  private final IntelligenceService intelligenceService;

  @EventListener
  void onCampaignConverted(CampaignConverted event) {
    intelligenceService.onCampaignConverted(event.campaignId(), event.bookingId());
  }
}
