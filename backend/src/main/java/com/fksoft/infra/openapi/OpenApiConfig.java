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
                    "ERP Acme Travel — modular monolith. Phase 8a adds CommercialPolicy: the"
                        + " governed-parameter precedence engine (DIRECTIVE > PROMOTION > CONTRACT >"
                        + " POLICY > SYSTEM_DEFAULT) with provenance, runtime self-service for rules"
                        + " and directives (audited; director role + justification for directives),"
                        + " and graduates the markup seam so a Quote's markup flows from the engine"
                        + " carrying the winning layer as its source.")
                .version("0.9.0"));
  }
}
