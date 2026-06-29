package com.fksoft.application.api;

import com.fksoft.domain.sourcing.ConnectorHealthView;
import com.fksoft.domain.sourcing.InboundQuotationResult;
import com.fksoft.domain.sourcing.RegisterInboundQuotationCommand;
import com.fksoft.domain.sourcing.SourcingService;
import com.fksoft.infra.integration.quotationsite.QuotationSiteInboundAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound webhook of the quotation-site connector (SPEC-0009). This delivery adapter takes the
 * <strong>raw body</strong> and the {@code X-Signature} header, delegates signature verification
 * and the ACL translation to {@link QuotationSiteInboundAdapter} (so the external vendor shape
 * stays in infra — BR6), then drives {@link SourcingService} to create a Quote INTEGRATED
 * idempotently (BR4). Returns {@code 202 Accepted}. The connector is a webhook (a serious external
 * contract): signed, idempotent, versioned via the path.
 */
@RestController
@RequestMapping("/api/integration/quotation-site")
@RequiredArgsConstructor
public class QuotationSiteInboundController {

  /** Audit actor for connector-driven actions (no human principal on a webhook). */
  private static final String CONNECTOR_ACTOR = "quotation-site-connector";

  private final QuotationSiteInboundAdapter inboundAdapter;
  private final SourcingService sourcingService;

  @PostMapping(path = "/inbound", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<InboundQuotationResult> inbound(
      @RequestBody byte[] rawBody,
      @RequestHeader(value = "X-Signature", required = false) String signature) {
    RegisterInboundQuotationCommand command = inboundAdapter.verifyAndTranslate(rawBody, signature);
    InboundQuotationResult result = sourcingService.processInbound(command, CONNECTOR_ACTOR);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
  }

  @GetMapping("/health")
  public ConnectorHealthView health() {
    return sourcingService.connectorHealth();
  }
}
