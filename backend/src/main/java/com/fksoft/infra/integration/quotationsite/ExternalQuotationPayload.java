package com.fksoft.infra.integration.quotationsite;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * The <strong>external</strong> contract of the quotation-site webhook (SPEC-0009). This vendor
 * shape lives <strong>only</strong> in {@code infra.integration} and is translated to the domain
 * command {@code RegisterInboundQuotationCommand} by {@link QuotationSiteInboundAdapter} — it never
 * crosses into the domain (BR6; an ArchUnit test enforces this). Unknown properties are ignored so
 * a minor vendor change does not break ingestion.
 *
 * @param externalQuotationId the external quotation id
 * @param product the free-text product description
 * @param price the closed external price
 * @param account the account reference (document)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExternalQuotationPayload(
    String externalQuotationId, String product, ExternalPrice price, ExternalAccount account) {

  /**
   * The external price shape {@code {amount, currency}}.
   *
   * @param amount the amount as a decimal
   * @param currency the three-letter currency code
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExternalPrice(BigDecimal amount, String currency) {}

  /**
   * The external account reference shape {@code {document}}.
   *
   * @param document the account document (CNPJ/CPF)
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ExternalAccount(String document) {}
}
