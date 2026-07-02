package com.fksoft.application.api;

import com.fksoft.application.api.dto.RegisterForwardRequest;
import com.fksoft.domain.exchange.ForwardContractView;
import com.fksoft.domain.exchange.ForwardService;
import com.fksoft.domain.exchange.ForwardStatus;
import com.fksoft.infra.security.UserContextProvider;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for FX forward contracts (SPEC-0032, Fase 19h): the treasury desk registers a
 * forward (locks a rate for a notional/maturity), settles it at the effective rate or cancels it,
 * and lists the book's coverage. Writes require the treasury roles (Director/Finance — 19a matrix).
 */
@Tag(name = "Exchange Forwards", description = "Hedge cambial: contratos a termo (forwards)")
@RestController
@RequestMapping("/api/exchange/forwards")
@RequiredArgsConstructor
public class ExchangeForwardController {

  private final ForwardService forwardService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<ForwardContractView> register(
      @Valid @RequestBody RegisterForwardRequest request) {
    ForwardContractView view =
        forwardService.register(
            request.currency(),
            request.notional(),
            request.contractRate(),
            request.tradeDate(),
            request.maturityDate(),
            request.counterparty(),
            userContextProvider.currentUser().username());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  /** Body for settle: the effective market rate at maturity. */
  public record SettleForwardRequest(@NotNull @Positive BigDecimal effectiveRate) {}

  @PostMapping("/{id}/settle")
  public ForwardContractView settle(
      @PathVariable UUID id, @Valid @RequestBody SettleForwardRequest request) {
    return forwardService.settle(
        id, request.effectiveRate(), userContextProvider.currentUser().username());
  }

  @PostMapping("/{id}/cancel")
  public ForwardContractView cancel(@PathVariable UUID id) {
    return forwardService.cancel(id, userContextProvider.currentUser().username());
  }

  @GetMapping
  public List<ForwardContractView> list(
      @RequestParam(value = "status", required = false) ForwardStatus status) {
    return forwardService.list(status);
  }
}
