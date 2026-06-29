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
                    "ERP Acme Travel — modular monolith. Phase 8d adds Payout (SPEC-0017): it executes"
                        + " the financial outflows — agent commission repass, supplier settlement (foreign"
                        + " currency at the real settlement rate, with the BRL baixa) and customer refund"
                        + " — supporting installments and a receipt. The payment to the outside world is an"
                        + " ACL: a PaymentGateway domain port with a traceable mock that confirms/fails"
                        + " asynchronously via a signed webhook (ADR 0006), processed idempotently so a"
                        + " re-delivered callback never double-pays. Settling a supplier publishes the"
                        + " SupplierSettled event consumed once by Finance (AP posting); refunds archive a"
                        + " REFUND_PROOF and never cancel the supplier obligation (merchant trap). Endpoints:"
                        + " POST /api/payouts, /{id}/execute, GET /{id}, GET /api/payouts (list),"
                        + " POST /api/webhooks/payouts/mock.")
                .version("0.12.0"));
  }
}
