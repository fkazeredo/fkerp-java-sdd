package com.fksoft.application.api;

import com.fksoft.infra.integration.payment.PaymentWebhookReceiver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The inbound webhook of the payment provider (SPEC-0017; ADR 0006; DL-0048). This delivery adapter
 * takes the <strong>raw body</strong> and the {@code X-Payment-Signature} header and delegates
 * signature verification, the ACL translation and the idempotent application to {@link
 * PaymentWebhookReceiver} (so the external vendor shape stays in infra). Returns {@code 202
 * Accepted}. The endpoint is the same one the mock POSTs to and a real provider would POST to:
 * signed, idempotent, versioned via the path.
 */
@RestController
@RequestMapping("/api/webhooks/payouts")
@RequiredArgsConstructor
public class PayoutWebhookController {

  private final PaymentWebhookReceiver receiver;

  @PostMapping(path = "/mock", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> mock(
      @RequestBody byte[] rawBody,
      @RequestHeader(value = "X-Payment-Signature-Timestamp", required = false) String timestamp,
      @RequestHeader(value = "X-Payment-Signature", required = false) String signature) {
    receiver.receive(rawBody, timestamp, signature);
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
