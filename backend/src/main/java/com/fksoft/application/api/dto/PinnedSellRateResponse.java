package com.fksoft.application.api.dto;

import com.fksoft.domain.exchange.PinnedSellRateView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for pinned-rate endpoints, built from the domain {@link PinnedSellRateView}. The
 * currency pair is rendered in canonical {@code BASE/QUOTE} text.
 */
public record PinnedSellRateResponse(
    UUID id,
    String currencyPair,
    BigDecimal rate,
    Instant effectiveFrom,
    String setBy,
    String note) {

  /** Maps a domain view to the response DTO. */
  public static PinnedSellRateResponse from(PinnedSellRateView view) {
    return new PinnedSellRateResponse(
        view.id(),
        view.currencyPair().asText(),
        view.rate(),
        view.effectiveFrom(),
        view.setBy(),
        view.note());
  }
}
