package com.fksoft.infra.integration.nfse;

import java.math.BigDecimal;

/**
 * The <strong>external</strong> vendor shape of the municipal NFS-e webservice (ABRASF-like RPS
 * envelope), used only inside this ACL adapter (SPEC-0016; DL-0046). It deliberately mirrors a
 * municipal contract (RPS number/series, taxable values, ISS, the signed XML) and MUST NOT leak
 * into the domain — an ArchUnit boundary test proves no domain class depends on this package. The
 * domain speaks {@code NfseIssueRequest}/{@code NfseIssuance}; this adapter translates to/from this
 * envelope.
 *
 * @param rpsNumber the RPS (Recibo Provisório de Serviço) number sent to the municipality
 * @param municipalityCode the IBGE municipality code
 * @param serviceCode the municipal service code
 * @param serviceAmount the taxable service amount (the commission base)
 * @param issAmount the ISS amount
 * @param currency the currency code
 * @param signedXml the signed NFS-e XML bytes (e-CNPJ)
 */
public record MunicipalNfseEnvelope(
    String rpsNumber,
    String municipalityCode,
    String serviceCode,
    BigDecimal serviceAmount,
    BigDecimal issAmount,
    String currency,
    byte[] signedXml) {}
