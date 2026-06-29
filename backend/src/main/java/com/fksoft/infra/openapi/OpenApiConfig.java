package com.fksoft.infra.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated contract (springdoc exposes it at {@code /v3/api-docs} and the
 * Swagger UI). Keeps the API documented from the foundation, as required by modules-and-apis.md.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI acmeTravelOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Acme Travel ERP API")
                .description(
                    "ERP Acme Travel — modular monolith. Phase 8b extends Finance (SPEC-0015, the"
                        + " full generic seam — a cash book, not a full general ledger): AP/AR ledger"
                        + " entries are now posted automatically and idempotently from the Booking"
                        + " charge events (cancellation penalties, refunds, the merchant-trap supplier"
                        + " obligation, no-show fees), and a period trial-balance endpoint reports the"
                        + " operational balance per currency (payable/receivable/net) and counts per"
                        + " status. The Compliance close-veto stays intact.")
                .version("0.10.0"));
  }
}
