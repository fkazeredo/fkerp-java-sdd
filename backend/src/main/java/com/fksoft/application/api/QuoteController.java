package com.fksoft.application.api;

import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.OverrideQuoteRequest;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.quoting.ComposeQuoteCommand;
import com.fksoft.domain.quoting.QuoteService;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.infra.security.UserContextProvider;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for quotes (SPEC-0005): compose a MANUAL quote, apply a price override with a
 * reason, and fetch a quote with its override history. The delivery layer resolves the acting user.
 */
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

  private final QuoteService quoteService;
  private final UserContextProvider userContextProvider;

  @PostMapping
  public ResponseEntity<QuoteView> compose(@Valid @RequestBody ComposeQuoteRequest request) {
    String actor = userContextProvider.currentUser().username();
    ComposeQuoteCommand command =
        new ComposeQuoteCommand(
            request.accountId(),
            request.basePrice(),
            CurrencyPair.parse(request.currencyPair()),
            request.supplierCommissionPct(),
            request.agentCommissionPct(),
            request.validUntil());
    return ResponseEntity.status(HttpStatus.CREATED).body(quoteService.compose(command, actor));
  }

  @PostMapping("/{id}/override")
  public QuoteView override(
      @PathVariable UUID id, @Valid @RequestBody OverrideQuoteRequest request) {
    String actor = userContextProvider.currentUser().username();
    return quoteService.override(id, request.appliedAmount(), request.reason(), actor);
  }

  @GetMapping("/{id}")
  public QuoteView get(@PathVariable UUID id) {
    return quoteService.getById(id);
  }
}
