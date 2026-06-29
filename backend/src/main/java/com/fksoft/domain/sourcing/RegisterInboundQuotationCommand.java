package com.fksoft.domain.sourcing;

import com.fksoft.domain.money.Money;

/**
 * The translated, validated domain command the inbound ACL passes to the sourcing module (SPEC-0009
 * BR6): it is the <strong>only</strong> shape that crosses the integration boundary. The external
 * vendor payload ({@code ExternalQuotationPayload}) is translated to this command in the infra
 * adapter and never reaches the domain. The signature has already been verified by the time this
 * command exists.
 *
 * @param externalQuotationId the external quotation id (idempotency key — BR4)
 * @param productText the free-text product description from the external site
 * @param price the trusted, closed external price
 * @param accountDocument the account document to resolve the {@code Account} (DL-0017)
 */
public record RegisterInboundQuotationCommand(
    String externalQuotationId, String productText, Money price, String accountDocument) {}
