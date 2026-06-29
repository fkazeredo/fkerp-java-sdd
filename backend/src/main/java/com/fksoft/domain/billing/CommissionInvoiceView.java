package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public read view of a commission invoice (SPEC-0016). The {@code base} is the commission (BR1);
 * the {@code iss} and {@code withholdings} are present once issued; {@code number}/{@code
 * verificationCode} /{@code documentId} are filled by the issuance (BR3/BR5).
 *
 * @param id the invoice id
 * @param commissionEntryId the referenced Finance commission entry (value, never an FK)
 * @param base the commission base (the taxable base — never the gross package)
 * @param status the lifecycle status
 * @param iss the ISS due, or {@code null} while a draft
 * @param withholdings the withholdings (empty under Simples Nacional)
 * @param regime the tax regime applied
 * @param municipality the IBGE municipality code of incidence
 * @param serviceCode the municipal service code
 * @param number the NFS-e number, or {@code null} while not issued
 * @param verificationCode the NFS-e verification code, or {@code null} while not issued
 * @param documentId the archived vault document id, or {@code null} while not issued
 * @param createdAt when the draft was created
 */
public record CommissionInvoiceView(
    UUID id,
    UUID commissionEntryId,
    Money base,
    InvoiceStatus status,
    Money iss,
    List<Withholding> withholdings,
    TaxRegime regime,
    String municipality,
    String serviceCode,
    String number,
    String verificationCode,
    UUID documentId,
    Instant createdAt) {}
