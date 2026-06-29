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
                    "ERP Acme Travel — modular monolith. Phase 8c adds Billing (SPEC-0016): it issues"
                        + " the commission NFS-e (nota fiscal de serviço) over the commission — the real"
                        + " revenue — computing ISS parametrized by tax regime (Simples Nacional default,"
                        + " swappable) and municipality. The taxable base is the commission, never the"
                        + " gross package. The municipal NFS-e webservice is an external integration"
                        + " behind a domain port with a traceable mock (ACL); an issued invoice is"
                        + " archived in the Compliance vault (satisfying the commission entry's document"
                        + " requirement so the month can close) and its ISS is posted to Finance via an"
                        + " idempotent event. Endpoints: POST /api/billing/invoices (draft),"
                        + " /{id}/issue, /{id}/cancel, GET /{id}.")
                .version("0.11.0"));
  }
}
