package com.fksoft.application.api;

import com.fksoft.domain.exchange.FxPositionService;
import com.fksoft.domain.exchange.FxPositionView;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST read endpoint for a single FX position and its subsidy × drift decomposition (SPEC-0011).
 * Positions are not created by API — they are born from {@code BookingConfirmed} — so this exposes
 * only the read keyed by booking.
 */
@RestController
@RequestMapping("/api/exchange/positions")
@RequiredArgsConstructor
public class ExchangePositionController {

  private final FxPositionService fxPositionService;

  @GetMapping("/{bookingId}")
  public FxPositionView byBooking(@PathVariable UUID bookingId) {
    return fxPositionService.getByBooking(bookingId);
  }
}
