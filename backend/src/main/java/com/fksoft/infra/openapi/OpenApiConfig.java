package com.fksoft.infra.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated contract (springdoc exposes it at {@code /v3/api-docs} and the
 * Swagger UI). Keeps the API documented from the foundation, as required by modules-and-apis.md.
 *
 * <p>Security scheme (SPEC-0024 Phase 13 / DL-0104): the API is protected by a <strong>bearer JWT
 * issued by the external OIDC IdP</strong> (Keycloak), validated by the resource server via JWKS.
 * The {@code bearerAuth} scheme documents the {@code Authorization: Bearer <jwt>} requirement;
 * obtain the token by logging in at the IdP (Authorization Code + PKCE).
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI acmeTravelOpenApi() {
    return new OpenAPI()
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "OIDC bearer token issued by the external IdP (Keycloak), validated by"
                                + " the resource server via JWKS (SPEC-0024 Phase 13).")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
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
                        + " POST /api/webhooks/payouts/mock. AfterSales (SPEC-0018) adds the post-sale"
                        + " context: support cases with a lifecycle state machine and governed SLA"
                        + " deadlines (resolved from CommercialPolicy; an SLA breach is a non-blocking"
                        + " alert), resolving a case can forward a refund to Payout (idempotently, without"
                        + " cancelling the supplier obligation — merchant trap) or a cancellation to"
                        + " Booking, and it accrues the cost-to-serve. Endpoints: POST /api/aftersales/cases,"
                        + " /{id}/assign|progress|wait|resolve|close, GET /{id}, GET /api/aftersales/cases"
                        + " (list). Marketing (SPEC-0019) adds the B2B marketing context, governing LGPD"
                        + " consent as a first-class citizen: consent is an append-only log (current state"
                        + " is the latest row per subject+purpose); a campaign only sends to subjects with a"
                        + " GRANTED consent (the others are suppressed and counted) through a NewsletterSender"
                        + " ACL (traceable mock), idempotently per recipient; segments use validated criteria"
                        + " over existing data (minimization); attribution links a campaign code to a booking"
                        + " and, on BookingConfirmed, publishes CampaignConverted for the DSS; and an LGPD"
                        + " erasure removes marketing PII while preserving an anonymized revocation tombstone."
                        + " Endpoints: POST /api/marketing/consents, DELETE /consents/{id}, GET /consents,"
                        + " POST /segments, GET /segments/{id}/preview, POST /campaigns, GET /campaigns/{id},"
                        + " POST /campaigns/{id}/send, POST /attribution, GET /attribution, POST /erasure."
                        + " Portfolio (SPEC-0020) adds the representation context: the brands/suppliers"
                        + " the Acme represents (unique brandRef, ACTIVE/INACTIVE), the representation"
                        + " contracts (validity + a Compliance document referenced by value, with a"
                        + " controlled-clock expiry alert that publishes RepresentationExpiring), and the"
                        + " goals per brand (VOLUME/REVENUE) whose realized-vs-goal progress is a read-model"
                        + " projection over sales events (BookingConfirmed/SpreadRealized) matched to a brand"
                        + " by a Portfolio-owned sale-attribution intake — Portfolio never prices nor blocks"
                        + " a sale (selling without an in-force contract only alerts). Endpoints:"
                        + " POST /api/portfolio/brands, GET /brands/{id}, GET /brands, DELETE /brands/{id},"
                        + " POST /brands/{brandRef}/contracts, GET /brands/{brandRef}/contract-coverage,"
                        + " POST /contracts/flag-expiring, POST /brands/{brandRef}/goals,"
                        + " GET /brands/{id}/goals/{period}/progress, POST /brands/{brandRef}/sales."
                        + " Assets (SPEC-0021) adds the internal-patrimony context: a lean registry of the"
                        + " Acme's own equipment, software licenses and other goods (type EQUIPMENT |"
                        + " SOFTWARE_LICENSE | OTHER; a license requires an expiresAt), with a basic"
                        + " lifecycle (ACTIVE/RETIRED, retirement audited), the acquisition document"
                        + " (Compliance) and cost ledger entry (Finance) referenced by value (never an FK),"
                        + " and a controlled-clock alert that publishes AssetLicenseExpiring once per"
                        + " expiring license — it is patrimony, not a product (it never prices a sale), and"
                        + " there is no depreciation/maintenance here (buy a full asset-management system if"
                        + " needed). Endpoints: POST /api/assets, GET /assets/{id},"
                        + " GET /assets?type=&status=&expiringWithinDays=, POST /assets/{id}/retire,"
                        + " POST /assets/flag-expiring."
                        + " People (SPEC-0022) adds the minimal HR context built on the operational"
                        + " point snapshot (SPEC-0012, same module): collaborators (Employee — unique"
                        + " identifier, admission, contracted journey HH:mm, ACTIVE/ON_LEAVE/TERMINATED,"
                        + " contract document by value), the processed period Journey and time-bank"
                        + " balance computed from the operational snapshot consumed by value (worked minus"
                        + " contracted minutes; positive extras, negative faltas — the snapshot is never a"
                        + " legal document, BR6/DL-0069/DL-0070), and journey discrepancies"
                        + " (ODD_PUNCH/MISSING_PUNCH/INCOHERENT_JOURNAL) raised as non-blocking alerts in a"
                        + " treatment queue without auto-correcting (BR4/DL-0071); the processed payslip is"
                        + " archived in the Compliance vault (PAYROLL, 5-year retention, personal data)"
                        + " referenced by value (DL-0072). Heavy payroll (eSocial/FGTS/13o) is out of scope"
                        + " — buy/integrate. Endpoints: POST /api/people/employees, GET /employees/{id},"
                        + " GET /employees?status=, POST /employees/{id}/journey,"
                        + " GET /employees/{id}/journey?period=, GET /employees/{id}/timebank?period=,"
                        + " GET /api/people/discrepancies, POST /employees/{id}/payslip."
                        + " Platform (SPEC-0023) adds the operated-infra context (TI): the e-CNPJ"
                        + " certificate custody — the secret material is encrypted at rest (AES-256-GCM,"
                        + " master key outside the database) and only metadata (subject, validity,"
                        + " days-to-expiry, status) is ever returned, never the private key/password; an"
                        + " expiry alert (CertificateExpiring) is raised by a controlled-clock sweep. Job"
                        + " governance — every important job runs through a registry with idempotency by"
                        + " (job, window), a Postgres advisory lock (one instance at a time; a concurrent"
                        + " run gets 409 locked) and a JobRun history (start/finish/status/items/"
                        + " correlation); a failed job is recorded FAILED, never masked as success; the"
                        + " job's logic stays in its owner module. System audit — an append-only"
                        + " consolidation of security/integration/job events (metadata only, no secrets)."
                        + " Endpoints: GET /api/platform/certificate/status, POST /api/platform/certificate,"
                        + " GET /api/platform/jobs, GET /api/platform/jobs/runs?job=&status=,"
                        + " POST /api/platform/jobs/{name}/trigger,"
                        + " GET /api/platform/audit?actor=&type=&from=&to=."
                        + " Identity (SPEC-0024, graduated to OIDC in Phase 13) makes the backend an"
                        + " OAuth2 Resource Server that validates JWTs issued by an EXTERNAL OIDC IdP"
                        + " (Keycloak) via JWKS (RS256, key rotation), with the backend as the single"
                        + " authorization authority. Login happens at the IdP (Authorization Code + PKCE);"
                        + " the in-house POST /api/identity/login of phase 8k was REMOVED (breaking). Send"
                        + " the IdP access token as Authorization: Bearer <jwt>; the real"
                        + " UserContextProvider resolves the user/roles from realm_access.roles."
                        + " Authorization is by role: sensitive actions require the corresponding role"
                        + " (issue NF -> ROLE_FINANCE, close period -> ROLE_FINANCE, trigger job / custody"
                        + " certificate -> ROLE_IT, directive -> ROLE_DIRECTOR, rule ->"
                        + " ROLE_DIRECTOR/ROLE_POLICY_ADMIN); a denial is 403 and is audited, an"
                        + " absent/invalid token is a generic 401. The first authenticated touch and"
                        + " denials are recorded in the Platform append-only system_audit"
                        + " (AUTH_LOGIN/ACCESS_DENIED), never a token/secret. Endpoints:"
                        + " GET /api/identity/me, GET /api/identity/roles,"
                        + " GET /api/identity/access-audit?actor=&type=&from=&to=."
                        + " Admin (SPEC-0025) is the administrative desk: a lean registry of"
                        + " administrative suppliers (utilities, software/service PJ, self-employed) and"
                        + " their contracts, which feeds expense entries into the Finance ledger and"
                        + " references the supporting documents in the Compliance vault (full procurement"
                        + " is out of scope — buy it if required). Registering a recurring expense posts a"
                        + " PAYABLE entry through the Finance facade (the entry type mapped from the kind:"
                        + " UTILITY->UTILITY_EXPENSE/UTILITY_BILL, autonomous->AUTONOMOUS_SERVICE/RPA,"
                        + " PJ service->SERVICE/NFSE, other->OTHER_EXPENSE) and lists the required"
                        + " documents; it is idempotent per (supplier, period, kind). Admin only"
                        + " generates the entry and references the document — it never imposes the"
                        + " document rule nor closes a period (the veto stays Finance+Compliance). Writes"
                        + " require ROLE_FINANCE and every change is audited. A contract-expiry alert runs"
                        + " on a controlled-clock governed job (alert, never a block). Endpoints:"
                        + " POST /api/admin/suppliers, GET /api/admin/suppliers/{id},"
                        + " GET /api/admin/suppliers?type=&status=,"
                        + " POST /api/admin/suppliers/{id}/contracts,"
                        + " GET /api/admin/suppliers/{id}/contracts, POST /api/admin/expenses,"
                        + " POST /api/admin/contracts/flag-expiring."
                        + " Phase 10 (SPEC-0026) is a frontend-only UX upgrade (professional SaaS shell,"
                        + " PrimeNG/Tailwind, command palette, light/dark theme, login with silent session"
                        + " revalidation via GET /api/identity/me, real loading/empty/error/permission"
                        + " states and a KPI dashboard composed client-side from the existing list"
                        + " endpoints) — it adds no new API endpoints and changes no contract."
                        + " Phase 11 (SPEC-0027) adds observability: Micrometer + Spring Boot Actuator"
                        + " with a Prometheus registry (GET /actuator/prometheus and /actuator/metrics"
                        + " require ROLE_IT; /actuator/health and /actuator/info stay public), JSON"
                        + " structured logs with the correlation id, a docker-compose monitoring stack"
                        + " (Prometheus + Loki + Grafana Alloy + Grafana) under infra/, business metrics"
                        + " derived from already-published domain events, and a public GET /api/version"
                        + " returning { version, gitCommit, buildTime } (build metadata only)."
                        + " Phase 12 (SPEC-0028) is internal quality tooling only: backend coverage"
                        + " gate (JaCoCo), frontend coverage gate (Vitest/v8), an isolated ephemeral"
                        + " Playwright E2E stack (port 4201) and a CI E2E job — it adds no API endpoint"
                        + " and changes no contract."
                        + " Phase 13 (SPEC-0024 graduation) replaces the in-house JWT issuer with an"
                        + " external OIDC IdP (Keycloak): the backend validates the IdP's bearer JWT via"
                        + " JWKS and maps realm roles to authorities. BREAKING: POST /api/identity/login"
                        + " is removed (login moves to the IdP); the security scheme is now an OIDC"
                        + " bearer token."
                        + " Phase 16a (SPEC-0029) is frontend-only: it adds the Finance/Billing/Payout/"
                        + " Compliance operator screens over the existing APIs — no new endpoint, no"
                        + " contract change."
                        + " Phase 16b (SPEC-0029) is frontend-only too: it adds the AfterSales, Sourcing,"
                        + " Exchange FX desk (market rates/positions/exposure) and Cancellation-policy"
                        + " operator screens over the existing APIs — no new endpoint, no contract"
                        + " change.")
                .version("0.26.0"));
  }
}
