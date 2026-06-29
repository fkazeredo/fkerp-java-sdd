package com.fksoft.application.api;

import com.fksoft.application.api.dto.RegisterSourcedOfferRequest;
import com.fksoft.domain.sourcing.SourcedOfferView;
import com.fksoft.domain.sourcing.SourcingService;
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
 * REST endpoints for sourced offers (SPEC-0009): manually register an offer's provenance and fetch
 * it. The delivery layer resolves the acting user for audit. The inbound webhook (the ACL) is a
 * separate controller ({@code QuotationSiteInboundController}, Slice 8c).
 */
@RestController
@RequestMapping("/api/sourcing")
@RequiredArgsConstructor
public class SourcingController {

  private final SourcingService sourcingService;
  private final UserContextProvider userContextProvider;

  @PostMapping("/offers")
  public ResponseEntity<SourcedOfferView> register(
      @Valid @RequestBody RegisterSourcedOfferRequest request) {
    String actor = userContextProvider.currentUser().username();
    SourcedOfferView view =
        sourcingService.register(
            request.productText(),
            request.basePrice(),
            request.origin(),
            request.integrationLevel(),
            request.externalRef(),
            actor);
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @GetMapping("/offers/{id}")
  public SourcedOfferView get(@PathVariable UUID id) {
    return sourcingService.getById(id);
  }
}
