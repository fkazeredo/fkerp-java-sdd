package com.fksoft.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeTypeCodes;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutKindCodes;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutStatus;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.infra.integration.payment.PayoutExecutionService;
import com.fksoft.infra.integration.payment.PayoutWebhookSignature;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end HTTP tests for the payment webhook endpoint (SPEC-0017; ADR 0006; DL-0048): a valid
 * signed callback confirms the installment (202) and an invalid/missing signature is rejected (401)
 * with nothing applied. Signs the body with the same shared secret the mock uses (the scheme a real
 * provider would use), proving the signature ACL end to end over HTTP.
 */
class PayoutWebhookHttpIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private PayoutService payoutService;
  @Autowired private PayoutExecutionService executionService;
  @Autowired private PayoutWebhookSignature signature;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM processed_payout_webhooks");
    jdbcTemplate.execute("DELETE FROM mock_payout_jobs");
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
  }

  @Test
  void aValidSignedWebhookConfirmsTheInstallment() {
    PayoutView created = createAgentCommission();
    executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);
    String providerRef =
        jdbcTemplate.queryForObject(
            "SELECT provider_ref FROM mock_payout_jobs WHERE payout_id = ?",
            String.class,
            created.id());
    String body = webhookBody(providerRef, created.id(), 1, "SUCCEEDED");
    String ts = signature.now();

    ResponseEntity<Void> response =
        post(body, ts, signature.sign(ts, body.getBytes(StandardCharsets.UTF_8)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTED);
  }

  @Test
  void anInvalidSignatureIsRejectedAndNothingIsApplied() {
    PayoutView created = createAgentCommission();
    executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);
    String body = webhookBody("mock-x", created.id(), 1, "SUCCEEDED");

    ResponseEntity<Void> response = post(body, signature.now(), "deadbeef"); // wrong signature

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTING);
    Integer processed =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_payout_webhooks", Integer.class);
    assertThat(processed).isZero();
  }

  private ResponseEntity<Void> post(String body, String timestamp, String signatureHeader) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Payment-Signature-Timestamp", timestamp);
    headers.set("X-Payment-Signature", signatureHeader);
    return restTemplate.postForEntity(
        "/api/webhooks/payouts/mock", new HttpEntity<>(body, headers), Void.class);
  }

  private static String webhookBody(String providerRef, UUID payoutId, int seq, String status) {
    return "{\"providerRef\":\""
        + providerRef
        + "\",\"payoutId\":\""
        + payoutId
        + "\",\"installmentSeq\":"
        + seq
        + ",\"status\":\""
        + status
        + "\"}";
  }

  private PayoutView createAgentCommission() {
    return payoutService.create(
        new CreatePayoutCommand(
            PayoutKindCodes.AGENT_COMMISSION,
            new Payee("ag-1", PayeeTypeCodes.AGENT),
            null,
            null,
            Money.of(new BigDecimal("405.00"), "BRL"),
            null,
            null,
            null,
            null),
        "dev");
  }
}
