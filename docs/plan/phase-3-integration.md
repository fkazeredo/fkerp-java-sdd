# Plano da Fase 3 — Primeira integração real (ACL do site de cotação, ramo INTEGRATED)

> Gerado pelo `docs/RUN-PHASE.md` (FASE-ALVO=3), modo autônomo. Convenção: prosa pt-BR, código em
> inglês. Segue o laço do `TUTORIAL.md` (RED → esqueleto → GREEN → refatora → portões → DoD), uma
> fatia por feature branch, `./mvnw verify` verde antes de cada merge `--no-ff` em `develop`.

## Objetivo da fase

Provar o **ramo INTEGRATED** do Quoting com a **primeira ACL de verdade**: o site de cotação entrega
um **preço fechado** que cria uma cotação no ERP **sem rodar o motor de sugestão**
(`trustExternalPrice = true`) — ativando o gancho que ficou adormecido na SPEC-0005. Formaliza também
o `Sourcing` (procedência da oferta). Entrega a spec:

- **SPEC-0009 (Sourcing + Integration)** — agregado `SourcedOffer` (texto-livre + preço-base + origem +
  nível de integração + ref. externa); **ACL de entrada** (webhook assinado, idempotente) que traduz o
  payload externo em comando de domínio e cria um **Quote INTEGRATED**; classificação de falhas e
  observabilidade; capacidade de health do conector.

## Decisões desta fase (decision-log)

- **[DL-0016]** Assinatura do webhook = HMAC-SHA256 + segredo compartilhado (`X-Signature`).
- **[DL-0017]** Account inexistente no inbound → **rejeita (422)**; não cria provisória nem enfileira.
  (Confiança **Baixa** — Open Question de negócio da SPEC-0009.)
- **[DL-0018]** Quote INTEGRATED **reusa o agregado** `Quote`; colunas de composição MANUAL viram
  nulas; `composeIntegrated` não roda sugestão nem cria override.
- **[DL-0019]** Resiliência proporcional: a ACL é de **entrada** (não há chamada de saída) ⇒
  classificação de falha + idempotência + observabilidade, **sem** circuit breaker/timeout/retry.

## Sequência de dependência (fatias)

```
Slice 8a  Sourcing (SourcedOffer + offers API)        ← módulo novo, independente
   └── Slice 8b  Quoting: ramo INTEGRATED              ← composeIntegrated + V10 (colunas nulas + source_offer_id)
          └── Slice 8c  ACL de entrada (webhook)       ← traduz payload externo → composeIntegrated; idempotência; eventos
```

Justificativa da ordem: o `Sourcing` é dono da procedência e do registro manual de oferta (8a, sem
dependência). O ramo INTEGRATED do `Quote` (8b) é o gancho que a ACL aciona — precisa existir antes do
webhook. A ACL (8c) amarra tudo: assinatura → tradução → `composeIntegrated` → `IntegratedQuoteCreated`,
idempotente por `externalQuotationId`.

## Fronteiras de módulo (Spring Modulith, detecção explicitly-annotated — DL-0006)

- **`com.fksoft.domain.sourcing`** (`@ApplicationModule "Sourcing"`), base pública + `internal`.
  - API pública: `SourcingService` (registrar/consultar oferta; processar inbound), o comando de
    domínio `RegisterInboundQuotationCommand` (o **único** que cruza a fronteira da ACL — BR6), o
    enum `IntegrationFailureClass`, views, eventos (`OfferSourced`, `IntegratedQuoteCreated`),
    exceções. `internal`: `SourcedOffer`, `InboundQuotation`, repositórios.
  - Colabora com `Accounts` (resolver conta por documento) e `Quoting` (criar Quote INTEGRATED)
    **só por fachada** (`AccountDirectory`, `QuoteIntegrationPort`).
- **`com.fksoft.infra.integration`** — adaptador ACL do site de cotação: `QuotationSiteSignatureVerifier`
  (HMAC) e o **payload externo** (`ExternalQuotationPayload`) **vivem só aqui** (não vazam — BR6). O
  controller REST (`application.api`) recebe o corpo bruto + header, valida a assinatura via infra,
  traduz para o comando de domínio e chama `SourcingService`.
- **`com.fksoft.domain.quoting`** — ganha `QuoteIntegrationPort` (porta de entrada para a ACL criar um
  Quote INTEGRATED) e `Quote.composeIntegrated`.
- **`com.fksoft.domain.accounts`** — `AccountDirectory.findIdByDocument` (resolver por documento).

> O DTO externo **não** cruza para o domínio: a tradução é na borda (controller + infra). Um teste de
> arquitetura confirma que `ExternalQuotationPayload` não é referenciado por nenhuma classe de
> `..domain..` (BR6 / Acceptance Criteria).

## Migrações (Flyway; a última aplicada é V8)

- **`V9__create_sourcing.sql`** — `sourced_offers` (id, product_text, base_amount/currency, origin,
  integration_level, external_ref, auditoria, version) + `inbound_quotations`
  (external_quotation_id PK, quote_id, received_at, account_id, failure_class null) — idempotência BR4.
- **`V10__quotes_integrated_and_source_offer.sql`** — `quotes ADD COLUMN source_offer_id uuid null`;
  afrouxa para NULL as colunas de composição MANUAL (`fx_rate`, `rate_id`, `base_converted_amount`,
  `supplier_pct`, `agent_pct`, comissões, `spread`, `markup_*`) — a integridade do MANUAL passa a ser
  do domínio (DL-0018).

## Endpoints (OpenAPI atualizada)

- `POST /api/integration/quotation-site/inbound` — webhook assinado (`X-Signature`), corpo do contrato
  externo, idempotente por `externalQuotationId`. → **202** | 400 `integration.payload.invalid` | 401
  `integration.signature.invalid` | 422 `integration.account.not-found`.
- `POST /api/sourcing/offers` — registro manual de oferta sourced → **201**.
- `GET /api/sourcing/offers/{id}` → 200 | 404 `sourcing.offer.not-found`.
- `GET /api/integration/quotation-site/health` — health do conector (read-model sobre
  `inbound_quotations`).

## Eventos (in-process)

- `OfferSourced {offerId, origin, integrationLevel, occurredAt}` — produtor `sourcing`.
- `IntegratedQuoteCreated {quoteId, externalQuotationId, occurredAt}` — produtor `sourcing` (após a
  ACL). Consumidor `intelligence` (8.2-F) **ainda não existe** → sem consumidor agora (in-process).
- `QuoteComposed` continua sendo do Quoting; no ramo INTEGRATED carrega `priceOrigin=INTEGRATED`.

## Erros / i18n (pt-BR + fallback) — `HttpErrorMapping`

- `integration.signature.invalid` → 401; `integration.payload.invalid` → 400;
  `integration.account.not-found` → 422; `sourcing.offer.not-found` → 404;
  `quoting.override.not-applicable` → 409 (override não se aplica a INTEGRATED).

## Observabilidade

- Log de integração do inbound: `externalQuotationId`, classe de falha, latência, correlation id; **sem
  dado pessoal** (documento mascarado). Métricas Micrometer `inbound_quotations_total`,
  `integration_failures_total{class}`. Health do conector via endpoint dedicado.

## Testes (proporcionais)

- **Unit:** tradução ACL (payload externo → comando) sem vazar DTO; verificação HMAC (válida/ inválida/
  ausente); classificação de falha; invariantes de `SourcedOffer` (BR1); `Quote.composeIntegrated`
  (suggested==applied==externo; sem override; override recusado).
- **Integração (Testcontainers):** webhook válido cria Quote INTEGRATED (sem sugestão/sem override);
  reentrega idempotente devolve o mesmo `quoteId`; assinatura inválida 401 (nada criado); payload
  inválido 400; conta inexistente 422; `POST/GET /api/sourcing/offers`.
- **Regressão de fronteira (Quoting):** no INTEGRATED o motor de sugestão **não** roda
  (`appliedAmount == preço externo`, sem OverrideRecord). Os testes MANUAL da 0005 seguem verdes.
- **Arquitetura:** `ExternalQuotationPayload` não cruza para `..domain..` (ArchUnit); `sourcing` não
  acessa repositórios de `accounts`/`quoting` (Spring Modulith verify).
- **Smoke:** `/api/system/health` segue verde.

## Definition of Done por fatia

Checklist do `TUTORIAL.md`: aceite → teste; `./mvnw verify` verde (ArchUnit/Modulith/Spotless/
Checkstyle); migração idempotente; `DomainException` com `code == chave i18n`; sem exceção crua de
banco; OpenAPI atualizada; observabilidade; mock rastreável (site externo); Spotless aplicado;
Conventional Commits; `docs/MANUAL.md` atualizado; caderno de testes atualizado antes do merge.

## Git

Uma feature branch por fatia a partir de `develop` → merge `--no-ff` em `develop` → push. Ao fim:
`release/0.4.0` (próximo **MINOR**, ADR 0015) → merge em `main` e `develop` → tag `0.4.0` → push;
release note em `docs/release-notes/0.4.0.md`. O `ROADMAP-STATUS.md` é do supervisor (não tocar).
