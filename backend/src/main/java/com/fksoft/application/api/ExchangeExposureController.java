package com.fksoft.application.api;

import com.fksoft.domain.exchange.ExchangeExposureService;
import com.fksoft.domain.exchange.LiveExposureView;
import com.fksoft.domain.exchange.PromoFxResultView;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST read endpoints for the FX exposure reports (SPEC-0011, slice 10c): the book's live exposure
 * (aggregate subsidy + drift with the drift alert) and the promo-fx result for a period (subsidy ×
 * drift × gap). Both are read-models/projections.
 */
@Tag(name = "Exchange Exposure", description = "Exposição do livro: subsídio e drift")
@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeExposureController {

  private final ExchangeExposureService exchangeExposureService;

  @GetMapping("/exposure")
  public LiveExposureView exposure() {
    return exchangeExposureService.liveExposure();
  }

  @GetMapping("/reports/promo-fx")
  public PromoFxResultView promoFx(@RequestParam String period) {
    return exchangeExposureService.promoFxResult(period);
  }
}
