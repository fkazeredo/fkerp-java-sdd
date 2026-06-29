package com.fksoft.application.api.dto;

import com.fksoft.domain.exchange.MarketRateSource;
import com.fksoft.domain.exchange.MarketRateView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for market-rate endpoints, built from the domain {@link MarketRateView}. The
 * currency pair is rendered in canonical {@code BASE/QUOTE} text.
 */
public record MarketRateResponse(
    UUID id, String currencyPair, BigDecimal rate, Instant observedAt, MarketRateSource source) {

  /** Maps a domain view to the response DTO. */
  public static MarketRateResponse from(MarketRateView view) {
    return new MarketRateResponse(
        view.id(), view.currencyPair().asText(), view.rate(), view.observedAt(), view.source());
  }
}
