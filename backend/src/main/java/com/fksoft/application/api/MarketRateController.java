package com.fksoft.application.api;

import com.fksoft.application.api.dto.MarketRateResponse;
import com.fksoft.application.api.dto.RecordMarketRateRequest;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.exchange.MarketRateService;
import com.fksoft.domain.exchange.MarketRateSourceCodes;
import com.fksoft.infra.security.UserContextProvider;
import com.fksoft.infra.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the market rate (SPEC-0011, slice 1). The normal path is an external feed via
 * the {@code MarketRateProvider} port (future adapter); this controller is the manual contingency
 * registration (DL-0025) plus reads of the prevailing market rate and the observation history.
 */
@RestController
@RequestMapping("/api/exchange/market-rates")
@RequiredArgsConstructor
public class MarketRateController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final MarketRateService marketRateService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<MarketRateResponse> record(
      @Valid @RequestBody RecordMarketRateRequest request) {
    String actor = userContextProvider.currentUser().username();
    MarketRateResponse response =
        MarketRateResponse.from(
            marketRateService.record(
                CurrencyPair.parse(request.currencyPair()),
                request.rate(),
                request.observedAt(),
                MarketRateSourceCodes.MANUAL,
                actor));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/current")
  public MarketRateResponse current(@RequestParam String pair) {
    return MarketRateResponse.from(marketRateService.currentMarket(CurrencyPair.parse(pair)));
  }

  @GetMapping
  public PageResponse<MarketRateResponse> history(
      @RequestParam String pair,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<MarketRateResponse> result =
        marketRateService.history(CurrencyPair.parse(pair), pageable).map(MarketRateResponse::from);
    return PageResponse.from(result);
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
