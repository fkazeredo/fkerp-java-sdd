package com.fksoft.application.api;

import com.fksoft.domain.sourcing.ConnectorHealthView;
import com.fksoft.domain.sourcing.InboundQuarantineService;
import com.fksoft.domain.sourcing.InboundQuotationResult;
import com.fksoft.domain.sourcing.IntegrationAccountNotFoundException;
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
 *
 * <p><strong>Quarantine (BR10, DL-0120 — revises DL-0017):</strong> a business rejection (unknown
 * account) still answers the same 422, but the translated payload is <strong>kept</strong> in the
 * inbound quarantine (own transaction) for operator replay — never lost at the boundary. A
 * signature/payload failure is NOT quarantined: an unauthenticated or malformed payload must not
 * persist anything.
 */
@RestController
@RequestMapping("/api/integration/quotation-site")
@RequiredArgsConstructor
public class QuotationSiteInboundController {

  /** Audit actor for connector-driven actions (no human principal on a webhook). */
  private static final String CONNECTOR_ACTOR = "quotation-site-connector";

  private final QuotationSiteInboundAdapter inboundAdapter;
  private final SourcingService sourcingService;
  private final InboundQuarantineService quarantineService;

  @PostMapping(path = "/inbound", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<InboundQuotationResult> inbound(
      @RequestBody byte[] rawBody,
      @RequestHeader(value = "X-Signature", required = false) String signature) {
    RegisterInboundQuotationCommand command = inboundAdapter.verifyAndTranslate(rawBody, signature);
    try {
      InboundQuotationResult result = sourcingService.processInbound(command, CONNECTOR_ACTOR);
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    } catch (IntegrationAccountNotFoundException rejected) {
      // Keep the rejected payload for operator replay (BR10/DL-0120); the wire contract of
      // DL-0017 (422, nothing created in the core) is unchanged.
      quarantineService.quarantine(command, rejected.code());
      throw rejected;
    }
  }

  @GetMapping("/health")
  public ConnectorHealthView health() {
    return sourcingService.connectorHealth();
  }
}
