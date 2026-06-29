package com.fksoft.application.api.dto;

import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/billing/invoices} (SPEC-0016): creates a draft commission
 * invoice from a Finance commission entry. The {@code base} is the commission (the taxable base —
 * BR1), never the gross package.
 *
 * @param commissionEntryId the referenced Finance commission entry id (required)
 * @param base the commission amount (the taxable base, required)
 * @param municipality the IBGE municipality code of incidence (required)
 * @param serviceCode the municipal service code (optional)
 */
public record CreateCommissionInvoiceRequest(
    @NotNull UUID commissionEntryId,
    @NotNull Money base,
    @NotBlank String municipality,
    String serviceCode) {}
