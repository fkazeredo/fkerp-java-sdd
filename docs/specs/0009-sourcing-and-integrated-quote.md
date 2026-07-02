# 0009 - Sourcing + Primeira Integração (ACL do Site de Cotação, ramo INTEGRATED)

Status: Approved
Related ADRs: 0010, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Resiliência de integração (timeout, retry, circuit breaker,
> classificação de falha, ACL) segue `messaging-and-integrations.md`; webhook é contrato externo sério
> (assinatura, idempotência, versionamento) — `modules-and-apis.md`.

## Goal

Formalizar **de onde vem a oferta + o nível de integração** (`Sourcing`) e provar o **ramo INTEGRATED**
do Quoting com a **primeira ACL real**: o site de cotação gratuita entrega um preço **fechado**, que
cria uma cotação no ERP **sem rodar o motor de sugestão** (`trustExternalPrice = true`) — ativando o
gancho que ficou adormecido na SPEC-0005 (redesenho 7.6 e Parte 10).

## Scope

**Em escopo:** o agregado `SourcedOffer` (texto-livre do produto + preço-base + origem + nível de
integração + referência externa); um **adaptador ACL de entrada** para o site de cotação (webhook
assinado, idempotente) que traduz o payload externo em comando de domínio e **cria um Quote
INTEGRATED**; classificação de falhas e observabilidade da integração; `Platform` monitorando a saúde
do conector.

**Fora de escopo:** recompor preço integrado (fica adormecido — Parte 4.3); integrações de
fornecedor/GDS (Locadora Internacional) e portais próprios (Portal de Experiências/Locação) — outras
specs; o motor de sugestão (já existe e roda **só** no MANUAL, SPEC-0005).

## Business Context

O mundo é híbrido (Parte 3.3): há portais integrados, sites externos, catálogos físicos e demanda
crua. `Sourcing` registra essa procedência sem deformar o domínio — **vendor DTO não vaza** para
dentro (ACL). O site de cotação é a integração mais simples ("entra", a construir) e por isso é a
**primeira ACL**: prova o caminho INTEGRATED de ponta a ponta com risco baixo.

## Business Rules

```txt
BR1  SourcedOffer MUST ter productText (texto livre, não-vazio), basePrice (Money), origin ∈
     {PORTAL_API, EXTERNAL_SITE, THIRD_PARTY_CATALOG, RAW_DEMAND} e integrationLevel ∈
     {NONE, INBOUND, BIDIRECTIONAL}. Texto livre é oferta válida (não exige catálogo estruturado).
BR2  O ramo INTEGRATED MUST criar o Quote com priceOrigin = INTEGRATED e trustExternalPrice = true:
     suggestedAmount == appliedAmount == preço externo; o motor de sugestão NÃO roda; nenhum
     OverrideRecord é necessário (não há divergência contra sugestão).
BR3  A ACL de entrada MUST validar a assinatura do webhook; payload inválido/sem assinatura =>
     401/400 e nada é criado.
BR4  A entrada MUST ser idempotente por externalQuotationId: reentrega do mesmo id NÃO cria Quote
     duplicado (retorna o mesmo resultado).
BR5  Falhas de integração MUST ser classificadas (TIMEOUT, UNAVAILABLE, INVALID_RESPONSE,
     AUTHENTICATION_FAILED, …) e nunca produzir resultado de negócio enganoso (sem fallback que
     finja preço — `messaging-and-integrations.md`).
BR6  O domínio MUST NOT depender do DTO do site externo: a tradução vive na ACL (infra/integration);
     só um comando de domínio cruza a fronteira.
BR7  ASSUMIDO (2026-06-29, ver DL-0017): se a Account do documento do inbound NÃO existir, a entrada é
     REJEITADA com `integration.account.not-found` (422) e nada é criado — não cria conta provisória
     nem enfileira para curadoria (decisão de negócio reversível).
     **REVISADA na Fase 19b (ver DL-0120 e BR10):** o contrato externo (422, nada criado no núcleo)
     permanece, mas o payload rejeitado passa a ser PRESERVADO em quarentena para replay operacional
     — a rejeição não perde mais o dado na fronteira.
BR8  ASSUMIDO (2026-06-29, ver DL-0016): a assinatura do webhook é HMAC-SHA256 do corpo bruto com
     segredo compartilhado, no header `X-Signature` (hex, prefixo `sha256=` aceito), comparação em
     tempo constante. A versão do contrato trafega no path (v1 implícita).
BR9  ASSUMIDO (2026-06-29, ver DL-0018): o ramo INTEGRATED reusa o agregado `Quote`
     (`composeIntegrated`); override não se aplica a INTEGRATED (`quoting.override.not-applicable`,
     409). ASSUMIDO (ver DL-0019): resiliência proporcional — a ACL é de entrada (sem chamada de
     saída), logo classificação de falha + idempotência + observabilidade, sem circuit breaker.
BR10 ASSUMIDO (2026-07-02, ver DL-0120 — Fase 19b, revisa a DL-0017): **quarentena de inbound**
     (exception queue). Uma rejeição de NEGÓCIO do inbound (conta desconhecida) MUST preservar o
     payload traduzido em `inbound_quarantine` (transação própria — a linha sobrevive ao 422),
     idempotente por `externalQuotationId` (um pendente por id; índice único parcial). O operador
     (papel OPERATIONS) MUST poder listar, **reprocessar** (roda o fluxo normal; sucesso marca
     REPLAYED e vincula o quote; causa persistente mantém QUARANTINED) e **descartar** (DISCARDED).
     Transição sobre entrada resolvida => 409 `sourcing.quarantine.not-pending`. Falha de
     assinatura/payload NÃO quarentena nada (payload não-autenticado não persiste). O status
     (QUARANTINED→REPLAYED|DISCARDED) é máquina de estado e permanece enum (critério Fase 18).
```

## Input/Output Examples

```http
POST /api/integration/quotation-site/inbound        (webhook assinado)
Headers: X-Signature: ...
{ "externalQuotationId":"QS-2026-555",
  "product":"City Tour Rio - full day", "price":{"amount":"480.00","currency":"BRL"},
  "account":{"document":"12345678000195"} }
202 Accepted
{ "quoteId":"q90...", "priceOrigin":"INTEGRATED", "appliedAmount":{"amount":"480.00","currency":"BRL"} }

POST .../inbound   (mesmo externalQuotationId)
202 Accepted  -> retorna o MESMO quoteId (idempotente, BR4)

POST .../inbound   (assinatura inválida)
401 Unauthorized { "code":"integration.signature.invalid", "message":"...", "fields":[] }
```

## API Contracts

- `POST /api/integration/quotation-site/inbound` — **webhook** do site de cotação: assinatura
  obrigatória, corpo do contrato externo (versão no header/url), idempotência por
  `externalQuotationId`. Traduz via ACL → cria Quote INTEGRATED. → 202 | 400 | 401 | 409.
- `POST /api/sourcing/offers` — registro **manual** de uma oferta sourced (origem/nível) quando o
  operador quer rastrear a procedência → 201.
- `GET /api/sourcing/offers/{id}` → 200 | 404 `sourcing.offer.not-found`.
- OpenAPI atualizada; **contract-first** para o webhook (consumidor externo depende do contrato).

## Events

- `OfferSourced` — `{offerId, origin, integrationLevel, occurredAt}`. Produtor: `sourcing`.
- `IntegratedQuoteCreated` — `{quoteId, externalQuotationId, occurredAt}`. Produtor: `sourcing`
  (após a ACL). Consumidor: `intelligence` (funil por canal, 8.2-F).
- O `QuoteComposed` continua sendo evento do **Quoting**; no ramo INTEGRATED ele carrega
  `priceOrigin=INTEGRATED`.

## Persistence Changes

> **Nota (numeração real das migrações):** a última migração aplicada era **V8**; esta fase usa
> **V9/V10/V11** (o esboço abaixo mantém o conteúdo, a numeração foi ajustada na implementação).

```txt
V9__create_sourced_offers.sql
  sourced_offers(
    id uuid PK, product_text varchar not null,
    base_amount numeric(18,2) not null, base_currency varchar not null,
    origin varchar not null, integration_level varchar not null,
    external_ref varchar null,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null
  )
V10__quotes_integrated_and_source_offer.sql                   -- DL-0018
  ALTER TABLE quotes ADD COLUMN source_offer_id uuid null;    -- aditivo; null p/ quotes 100% manuais
  -- afrouxa p/ NULL as colunas de composição MANUAL (fx_rate, comissões, markup…): INTEGRATED não recompõe
V11__create_inbound_quotations.sql                            -- idempotência do webhook (BR4)
  inbound_quotations(
    external_quotation_id varchar PK,
    quote_id uuid not null, account_id uuid not null,
    received_at timestamptz not null
  )
```

A ACL e o cliente do site externo vivem em `com.fksoft.infra.integration`; expõem para o domínio
apenas uma **porta** (`QuotationSiteInboundPort`/comando). `Platform` observa o conector.

## Validation Rules

- Integração: assinatura do webhook; validação do payload (response/request validation); timeout.
- Application: idempotência (PK em `inbound_quotations.external_quotation_id`, BR4); resolução da
  `Account` pelo documento via `AccountDirectory.findIdByDocument` (fachada do Accounts) —
  inexistente => **rejeita 422** (BR7, ASSUMIDO ver DL-0017).
- Domain: invariantes de `SourcedOffer` (BR1) e do Quote INTEGRATED (BR2, reusando o agregado de 0005).

## Error Behavior

`integration.signature.invalid` → 401; `integration.payload.invalid` → 400;
`sourcing.offer.not-found` → 404; falha de dependência externa → erro classificado + 502/503 conforme
o caso, **sem** vazar detalhe interno (`security.md`). i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar entrada do webhook como **log de integração** (externalQuotationId, classe de falha, latência,
  correlation id) — sem dados sensíveis. Métricas: `inbound_quotations_total`,
  `integration_failures_total{class}`, latência do conector. Health do conector via `Platform`.

## Tests Required

- **Unit:** tradução da ACL (payload externo → comando de domínio) sem vazar DTO; classificação de falha.
- **Integração (Testcontainers + servidor externo fake):** webhook válido cria Quote INTEGRATED;
  reentrega idempotente retorna o mesmo quoteId; assinatura inválida 401; payload inválido 400.
- **Quoting (regressão de fronteira):** no ramo INTEGRATED o motor de sugestão **não** roda
  (`appliedAmount == preço externo`, sem OverrideRecord).

## Acceptance Criteria

- Uma cotação do site externo cria um Quote `INTEGRATED` com `appliedAmount` = preço recebido, sem
  sugestão e sem override.
- Reenviar a mesma cotação não duplica.
- Assinatura inválida é rejeitada e nada é criado.
- `./mvnw verify` verde (ArchUnit confirma que o DTO externo não cruza para o domínio).

## Open Questions

- ~~**Account inexistente** no inbound: criar conta provisória, rejeitar, ou enfileirar para
  curadoria?~~ → **ASSUMIDO** (2026-06-29): **rejeita 422** `integration.account.not-found` (não cria
  provisória nem enfileira). Confiança **Baixa** (decisão de negócio), reversão moderada. Ver
  [DL-0017](../decision-log/DL-0017-inbound-account-not-found-rejects.md). (BR7)
- ~~**Versão do contrato / assinatura** do site de cotação~~ → **ASSUMIDO** (2026-06-29): assinatura
  HMAC-SHA256 + segredo compartilhado (`X-Signature`), versão v1 no path. Ver
  [DL-0016](../decision-log/DL-0016-inbound-webhook-signature-hmac.md). (BR8) — **política de evolução
  do contrato fica a confirmar com o parceiro** quando ele publicar o contrato oficial.
- Q3 (merchant of record) não afeta esta fatia, mas afeta cobrança/reembolso depois (SPEC-0010/0017).

## Out of Scope

Recomposição do preço integrado (adormecido), integrações de GDS/portais próprios, motor de sugestão.
