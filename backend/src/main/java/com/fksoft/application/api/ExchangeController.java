package com.fksoft.application.api;

import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.application.api.dto.PinnedSellRateResponse;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.exchange.ExchangeRateService;
import com.fksoft.domain.exchange.PinnedSellRateView;
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
 * REST endpoints for the frozen sell rate (SPEC-0003). Pinning is append-only; reads serve the
 * prevailing rate and the history. The delivery layer resolves the acting user for audit.
 */
@RestController
@RequestMapping("/api/exchange/pinned-rates")
@RequiredArgsConstructor
public class ExchangeController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final ExchangeRateService exchangeRateService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<PinnedSellRateResponse> pin(@Valid @RequestBody PinRateRequest request) {
    String actor = userContextProvider.currentUser().username();
    PinnedSellRateView view =
        exchangeRateService.pin(
            CurrencyPair.parse(request.currencyPair()),
            request.rate(),
            request.effectiveFrom(),
            request.note(),
            actor);
    return ResponseEntity.status(HttpStatus.CREATED).body(PinnedSellRateResponse.from(view));
  }

  @GetMapping("/current")
  public PinnedSellRateResponse current(@RequestParam String pair) {
    return PinnedSellRateResponse.from(exchangeRateService.current(CurrencyPair.parse(pair)));
  }

  @GetMapping
  public PageResponse<PinnedSellRateResponse> history(
      @RequestParam String pair,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<PinnedSellRateResponse> result =
        exchangeRateService
            .history(CurrencyPair.parse(pair), pageable)
            .map(PinnedSellRateResponse::from);
    return PageResponse.from(result);
  }

  private static int clampSize(int requested) {
    if (requested < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
