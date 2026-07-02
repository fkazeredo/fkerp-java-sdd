package com.fksoft.infra.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated contract (springdoc exposes it at {@code /v3/api-docs} and the
 * Swagger UI). The contract is documented from the foundation (modules-and-apis.md); Fase 19d
 * (DL-0126) makes the docs real: every controller carries an {@code @Tag}, the stable error
 * envelope is documented globally ({@link GlobalErrorResponsesCustomizer}), and the Swagger UI
 * <strong>Authorize</strong> button drives the genuine OAuth2 Authorization Code + PKCE flow
 * against the self-hosted Authorization Server (ADR-0018) — so an operator can log in and try a
 * role-gated endpoint from the UI.
 *
 * <p>The long per-phase changelog that used to live in the {@code Info.description} now lives where
 * changelogs belong — {@code docs/release-notes/} (Rule Zero: one source of truth).
 */
@Configuration
public class OpenApiConfig {

  static final String OAUTH_SCHEME = "oauth2";

  private final String issuer;

  public OpenApiConfig(@Value("${app.oidc.issuer:http://localhost:8080}") String issuer) {
    this.issuer = (issuer == null || issuer.isBlank()) ? "http://localhost:8080" : issuer;
  }

  @Bean
  public OpenAPI acmeTravelOpenApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(OAUTH_SCHEME, oauthScheme())
                .addSecuritySchemes("bearerAuth", bearerScheme()))
        .addSecurityItem(new SecurityRequirement().addList(OAUTH_SCHEME))
        .info(
            new Info()
                .title("Acme Travel ERP API")
                .version("0.36.0")
                .description(
                    """
                    ERP Acme Travel — modular monolith (Spring Boot, hexagonal, Spring Modulith).

                    The API is protected by an OIDC bearer JWT issued by the SELF-HOSTED \
                    Authorization Server embedded in this app (ADR-0018) and validated by the \
                    resource server via JWKS. Use the **Authorize** button to log in with the \
                    Authorization Code + PKCE flow, or paste a bearer token.

                    Authorization is by role (SPEC-0024 / DL-0119): a write endpoint requires the \
                    owning desk's role (Finance / Operations / IT / Director / Policy Admin); an \
                    unmapped write is denied by default. Every endpoint returns the stable error \
                    envelope `{ code, message, fields }` (ADR 0011).

                    The per-release history lives in `docs/release-notes/` (CHANGELOG.en-US.md).\
                    """));
  }

  /**
   * OAuth2 Authorization Code + PKCE against the self-hosted AS (the Swagger UI Authorize flow).
   */
  private SecurityScheme oauthScheme() {
    OAuthFlow authorizationCode =
        new OAuthFlow()
            .authorizationUrl(issuer + "/oauth2/authorize")
            .tokenUrl(issuer + "/oauth2/token")
            .scopes(new Scopes().addString("openid", "OpenID").addString("profile", "Profile"));
    return new SecurityScheme()
        .type(SecurityScheme.Type.OAUTH2)
        .description(
            "OIDC Authorization Code + PKCE against the self-hosted Authorization Server "
                + "(ADR-0018). Client id: acme-erp-web (public, PKCE).")
        .flows(new OAuthFlows().authorizationCode(authorizationCode));
  }

  /** A plain bearer scheme for pasting a token directly (e.g. from a script). */
  private static SecurityScheme bearerScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.HTTP)
        .scheme("bearer")
        .bearerFormat("JWT")
        .description("Paste an OIDC access token issued by the self-hosted IdP (ADR-0018).");
  }
}
