package com.fksoft.infra.integration.quotationsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.sourcing.IntegrationPayloadInvalidException;
import com.fksoft.domain.sourcing.IntegrationSignatureInvalidException;
import com.fksoft.domain.sourcing.RegisterInboundQuotationCommand;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for the ACL translation (SPEC-0009 BR6): the external payload is verified and
 * translated to the domain command; a malformed/incomplete payload is rejected. The external shape
 * never escapes the adapter (the method returns a domain command).
 */
class QuotationSiteInboundAdapterTest {

  private static final String SECRET = "test-secret";
  private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");
  private static final String TS = NOW.toString();
  private final QuotationSiteSignatureVerifier verifier =
      new QuotationSiteSignatureVerifier(SECRET, 300, Clock.fixed(NOW, ZoneOffset.UTC));
  private final QuotationSiteInboundAdapter adapter =
      new QuotationSiteInboundAdapter(verifier, new ObjectMapper());

  private static final String VALID_JSON =
      """
      { "externalQuotationId":"QS-2026-555",
        "product":"City Tour Rio - full day",
        "price":{"amount":"480.00","currency":"BRL"},
        "account":{"document":"12345678000195"} }
      """;

  @Test
  void verifiesAndTranslatesAValidPayloadToADomainCommand() {
    byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
    RegisterInboundQuotationCommand command =
        adapter.verifyAndTranslate(body, TS, verifier.sign(TS, body));

    assertThat(command.externalQuotationId()).isEqualTo("QS-2026-555");
    assertThat(command.productText()).isEqualTo("City Tour Rio - full day");
    assertThat(command.price()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));
    assertThat(command.accountDocument()).isEqualTo("12345678000195");
  }

  @Test
  void rejectsAnInvalidSignatureBeforeTranslating() {
    byte[] body = VALID_JSON.getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> adapter.verifyAndTranslate(body, TS, "deadbeef"))
        .isInstanceOf(IntegrationSignatureInvalidException.class);
  }

  @Test
  void rejectsAMalformedJsonPayload() {
    byte[] body = "{not json".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> adapter.verifyAndTranslate(body, TS, verifier.sign(TS, body)))
        .isInstanceOf(IntegrationPayloadInvalidException.class);
  }

  @Test
  void rejectsAnIncompletePayload() {
    String missingPrice =
        "{ \"externalQuotationId\":\"QS-1\", \"product\":\"x\", \"account\":{\"document\":\"1\"} }";
    byte[] body = missingPrice.getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> adapter.verifyAndTranslate(body, TS, verifier.sign(TS, body)))
        .isInstanceOf(IntegrationPayloadInvalidException.class);
  }
}
