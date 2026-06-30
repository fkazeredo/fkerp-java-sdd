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
                        + " POST /assets/flag-expiring.")
                .version("0.16.0"));
  }
}
