package com.fksoft.infra.integration.quotationsite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.sourcing.IntegrationPayloadInvalidException;
import com.fksoft.domain.sourcing.RegisterInboundQuotationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The Anti-Corruption Layer for the quotation-site webhook (SPEC-0009): it verifies the signature
 * (BR3) and translates the <strong>external</strong> {@link ExternalQuotationPayload} into the
 * domain {@link RegisterInboundQuotationCommand} (BR6). The vendor shape stays here in {@code
 * infra.integration}; only the domain command leaves this adapter, so the external DTO never
 * reaches the domain (enforced by an ArchUnit boundary test).
 *
 * <p>The real quotation site is out of scope; this adapter is the <strong>traceable mock</strong>
 * of that integration ({@code simulation-and-mocking.md}) — it speaks the documented external
 * contract and is signed with the shared secret (DL-0016), proving the ACL translation and the
 * INTEGRATED branch end to end without a live external dependency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotationSiteInboundAdapter {

  private final QuotationSiteSignatureVerifier signatureVerifier;
  private final ObjectMapper objectMapper;

  /**
   * Verifies the signature over the raw body and translates the external payload to a domain
   * command. The external shape never escapes this method.
   *
   * @param rawBody the exact request bytes received
   * @param timestampHeader the {@code X-Signature-Timestamp} header (anti-replay)
   * @param signatureHeader the {@code X-Signature} header
   * @return the translated domain command (signature already verified)
   * @throws com.fksoft.domain.sourcing.IntegrationSignatureInvalidException when the signature is
   *     missing/invalid (BR3)
   * @throws IntegrationPayloadInvalidException when the payload is malformed or incomplete
   */
  public RegisterInboundQuotationCommand verifyAndTranslate(
      byte[] rawBody, String timestampHeader, String signatureHeader) {
    signatureVerifier.verify(rawBody, timestampHeader, signatureHeader);
    ExternalQuotationPayload payload = parse(rawBody);
    validate(payload);
    Money price;
    try {
      price = Money.of(payload.price().amount(), payload.price().currency());
    } catch (IllegalArgumentException invalid) {
      throw new IntegrationPayloadInvalidException();
    }
    return new RegisterInboundQuotationCommand(
        payload.externalQuotationId().trim(),
        payload.product().trim(),
        price,
        payload.account().document().trim());
  }

  private ExternalQuotationPayload parse(byte[] rawBody) {
    try {
      return objectMapper.readValue(rawBody, ExternalQuotationPayload.class);
    } catch (java.io.IOException malformed) {
      throw new IntegrationPayloadInvalidException();
    }
  }

  private static void validate(ExternalQuotationPayload payload) {
    if (payload == null
        || isBlank(payload.externalQuotationId())
        || isBlank(payload.product())
        || payload.price() == null
        || payload.price().amount() == null
        || isBlank(payload.price().currency())
        || payload.account() == null
        || isBlank(payload.account().document())) {
      throw new IntegrationPayloadInvalidException();
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
