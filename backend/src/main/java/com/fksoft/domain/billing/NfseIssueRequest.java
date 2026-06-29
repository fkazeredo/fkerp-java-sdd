package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;
import java.util.UUID;

/**
 * Domain request to issue a NFS-e through the {@link NfseGateway} (SPEC-0016 BR3). It speaks the
 * domain's language (the invoice id, the commission base, the computed ISS, the
 * municipality/service code) — never the municipal vendor's XML/SOAP shape, which lives in the ACL
 * adapter. The signed payload is produced inside the adapter via the {@link CertificateSigner}.
 *
 * @param invoiceId the invoice being issued
 * @param commissionBase the commission (the taxable base)
 * @param iss the computed ISS due
 * @param municipality the IBGE municipality code of incidence
 * @param serviceCode the municipal service code
 */
public record NfseIssueRequest(
    UUID invoiceId, Money commissionBase, Money iss, String municipality, String serviceCode) {}
