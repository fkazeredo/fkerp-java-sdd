package com.fksoft.infra.openapi;

import com.fksoft.infra.web.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documents the project's <strong>stable error contract</strong> ({@link ApiErrorResponse} — {@code
 * {code, message, fields}}, ADR 0011) once, globally, instead of repeating {@code @ApiResponse} on
 * 75 endpoints (Fase 19d, DL-0126). It registers the {@code ApiErrorResponse} schema in the OpenAPI
 * components and adds the common business error responses (400/401/403/404/ 409/422) to every
 * operation that does not already declare them, so the generated docs and the Swagger UI show the
 * real error envelope for each endpoint. The exact per-endpoint status set is a superset here
 * (documentation, not enforcement); the authoritative status mapping stays in {@code
 * HttpErrorMapping}.
 */
@Configuration
public class GlobalErrorResponsesCustomizer {

  private static final String ERROR_SCHEMA = "ApiErrorResponse";
  private static final String JSON = "application/json";

  private static final Map<String, String> COMMON_ERRORS =
      Map.of(
          "400", "Validation error (malformed request or invalid input).",
          "401", "Unauthenticated (missing or invalid bearer token).",
          "403", "Forbidden (authenticated but lacking the required role).",
          "404", "Resource not found.",
          "409", "Conflict (invalid state transition or duplicate).",
          "422", "Unprocessable (business rule rejected the request).");

  @Bean
  public OpenApiCustomizer globalErrorResponses() {
    return openApi -> {
      registerErrorSchema(openApi);
      if (openApi.getPaths() == null) {
        return;
      }
      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem ->
                  pathItem
                      .readOperations()
                      .forEach(operation -> addErrors(operation.getResponses())));
    };
  }

  private static void registerErrorSchema(OpenAPI openApi) {
    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }
    Components components = openApi.getComponents();
    if (components.getSchemas() != null && components.getSchemas().containsKey(ERROR_SCHEMA)) {
      return;
    }
    ModelConverters.getInstance().readAll(ApiErrorResponse.class).forEach(components::addSchemas);
  }

  private static void addErrors(ApiResponses responses) {
    if (responses == null) {
      return;
    }
    Content content =
        new Content()
            .addMediaType(
                JSON,
                new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)));
    COMMON_ERRORS.forEach(
        (status, description) ->
            responses.computeIfAbsent(
                status, key -> new ApiResponse().description(description).content(content)));
  }
}
