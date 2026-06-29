# Relatório da Fase 3 — Primeira integração real (ACL do site de cotação)

- **Fase:** 3 (ROADMAP) · **Versão:** 0.4.0 (MINOR, ADR 0015) · **Data:** 2026-06-29
- **Spec:** SPEC-0009 (Sourcing + Integration); ativa o ramo INTEGRATED de SPEC-0005
- **Resultado:** ✅ **SUCCESS** — `Tests run: 135, Failures: 0, Errors: 0, Skipped: 0`, BUILD SUCCESS
  (ArchUnit + Spring Modulith[10 módulos] + Spotless + Checkstyle[0 violações] verdes; Docker up)

## Fatias entregues

| Fatia | Entrega | Testes (acum.) |
|---|---|---|
| **8a — Sourcing** | módulo `sourcing` (10º Modulith); `SourcedOffer` (BR1); `POST/GET /api/sourcing/offers`; `OfferSourced`; `V9` | 114 |
| **8b — Quoting ramo INTEGRATED** | `Quote.composeIntegrated` (sem recompor); `QuoteIntegrationPort`; override recusado em INTEGRATED (409); `V10` | 118 |
| **8c — ACL de entrada (webhook)** | webhook assinado HMAC; tradução ACL (DTO externo não cruza); idempotência; `IntegratedQuoteCreated`; health; `V11` | 135 |

Cada fatia: feature branch a partir de `develop` → laço RED→GREEN→refactor→portões → `./mvnw verify`
verde → merge `--no-ff` em `develop` → push. Branches mergeadas apagadas.

## Arquivos criados/alterados (principais)

- **Sourcing (novo módulo):** `domain/sourcing/` — `SourcingService`, `SourcedOffer`(internal),
  `InboundQuotation`(internal), `OfferOrigin`, `IntegrationLevel`, `IntegrationFailureClass`,
  `RegisterInboundQuotationCommand`, `InboundQuotationResult`, `ConnectorHealthView`, eventos
  `OfferSourced`/`IntegratedQuoteCreated`, 5 exceções, `package-info` (`@ApplicationModule`).
- **Quoting:** `QuoteIntegrationPort` (porta), `Quote.composeIntegrated` + nulos tolerados,
  `QuoteOverrideNotApplicableException`, `QuoteService.createIntegratedQuote`.
- **Accounts:** `AccountDirectory.findIdByDocument` + impl + `AccountRepository.findFirstByDocumentNumber`.
- **Infra (ACL):** `infra/integration/quotationsite/` — `ExternalQuotationPayload` (DTO externo, só
  aqui), `QuotationSiteSignatureVerifier` (HMAC), `QuotationSiteInboundAdapter` (tradução).
- **Delivery:** `SourcingController`, `QuotationSiteInboundController`, `RegisterSourcedOfferRequest`.
- **Infra/web:** `HttpErrorMapping` (+6 exceções), `OpenApiConfig` (0.4.0); i18n pt-BR + fallback.
- **Migrações:** `V9__create_sourced_offers.sql`, `V10__quotes_integrated_and_source_offer.sql`,
  `V11__create_inbound_quotations.sql`.
- **Docs:** plano `docs/plan/phase-3-integration.md`; specs `0009`/`0005` atualizadas; DL-0016..0019 +
  INDEX; caderno `docs/test-report/slice-8a/8b/8c` + INDEX; `docs/MANUAL.md`; release note `0.4.0`.

## Testes por tipo

- **Unitário (domínio/infra):** `SourcedOfferTest` (3), `QuoteAggregateTest` (+2 INTEGRATED),
  `QuotationSiteSignatureVerifierTest` (6), `QuotationSiteInboundAdapterTest` (4 — tradução ACL).
- **Integração (Testcontainers/Postgres):** `SourcingIntegrationTest` (3),
  `IntegratedQuoteIntegrationTest` (2), `QuotationSiteInboundIntegrationTest` (6 — webhook→INTEGRATED,
  idempotência, 401, 400, 422, health).
- **Arquitetura:** ArchUnit `DOMAIN_MUST_NOT_DEPEND_ON_EXTERNAL_INTEGRATION_DTOS` (o DTO externo não
  cruza para o domínio — BR6); `HttpErrorMappingCompletenessTest`; Spring Modulith `verify()`.
- **Regressão:** os testes MANUAL da SPEC-0005 (`QuoteIntegrationTest`, 5) seguem verdes após a
  migração que afrouxou os NOT NULL.

## Impacto em OpenAPI

3 endpoints novos auto-documentados (springdoc): `/api/sourcing/offers` (POST/GET),
`/api/integration/quotation-site/inbound` (POST), `/api/integration/quotation-site/health` (GET).
Metadata bumpada para 0.4.0. Mudança **retrocompatível** (só adições).

## Decisões (decision-log)

| DL | Título | Conf. | Rev. |
|---|---|---|---|
| [DL-0016](decision-log/DL-0016-inbound-webhook-signature-hmac.md) | Webhook: assinatura HMAC-SHA256 + segredo (`X-Signature`) | Média | Moderada |
| [DL-0017](decision-log/DL-0017-inbound-account-not-found-rejects.md) | Inbound: Account inexistente **rejeita 422** | **Baixa** | Moderada |
| [DL-0018](decision-log/DL-0018-integrated-quote-modeling.md) | Quote INTEGRATED reusa o agregado; colunas MANUAL nulas | Alta | Moderada |
| [DL-0019](decision-log/DL-0019-acl-resilience-scope-inbound.md) | ACL de entrada: classificação + observabilidade, sem circuit breaker | Alta | Barata |

> **Destaque (Confiança Baixa):** DL-0017 é decisão de **negócio** (Open Question da SPEC-0009); se o
> dono preferir conta provisória/curadoria, a troca é localizada e reversível.

## Riscos / pendências

- Recomposição do preço integrado **adormecida** (DL-0018, reversão aditiva).
- `IntegratedQuoteCreated` **sem consumidor** (Intelligence ainda não existe) — in-process.
- Resiliência de **saída** (timeout/retry/circuit breaker) fica para a 1ª ACL de saída (DL-0019).
- Política de evolução do **contrato** do site de cotação: confirmar com o parceiro.
- Sem **telas Angular** (webhook é máquina-a-máquina).

## Para a próxima fase

Fase 4 — Cancelamento como objeto + armadilha do *merchant of record* (SPEC-0010); a recomendação Q3
do ROADMAP (`merchantOfRecord` em `RepresentationContract`, default afiliado) entra lá.
