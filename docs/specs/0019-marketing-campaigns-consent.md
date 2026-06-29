# 0019 - Marketing (Campanha, Segmentação, Newsletter e Consentimento LGPD)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. O envio externo é **ACL** (provedor de e-mail/newsletter); o
> **consentimento LGPD é cidadão de primeira classe** (`security.md`); a atribuição campanha→reserva
> alimenta a **Intelligence (SPEC-0013)**.

## Goal

Permitir **campanhas segmentadas** para a base B2B (agências/agentes) com **newsletter externa**,
governadas por **consentimento LGPD** verificável, e medir **atribuição** (o que de fato virou venda) —
redesenho linha 159 e 328.

## Scope

**Em escopo:** o agregado `Consent` (titular, finalidade, base legal, status GRANTED/REVOKED, origem,
timestamp) como **pré-condição de envio**; `Segment` (critérios sobre dados comerciais já existentes:
tipo de conta, volume, rota, etc.); `Campaign` (segmento, conteúdo, janela) e o disparo via **porta de
newsletter** (ACL); `Attribution` (UTM/código → reserva) para ligar campanha a `BookingConfirmed`.

**Fora de escopo:** criação/gestão do conteúdo criativo; CRM completo (comprar, se exigido); envio para
quem **não** consentiu (proibido); dados de pessoas físicas além do necessário (minimização).

## Business Context

A Acme fala com **agências/agentes** (B2B). Mandar newsletter exige **consentimento** e respeito a
**revogação** (LGPD). Medir atribuição fecha o ciclo: *quanto a campanha trouxe de venda* (8.2-F),
insumo do DSS. Tratar consentimento como objeto evita o risco regulatório de "mandar para todo mundo".

## Business Rules

```txt
BR1  Consent MUST registrar titular (id+tipo, valor), purpose (ex.: NEWSLETTER), legalBasis,
     status ∈ {GRANTED, REVOKED}, source e timestamp. Revogação é append (history preservado).
     ASSUMIDO (ver DL-0056): tabela append-only; estado atual = última linha por (titular, finalidade);
     revogar/reconsentir = nova linha imutável. Modelo single opt-in no v1 (ASSUMIDO, ver DL-0055).
BR2  Nenhum envio de campanha MUST ocorrer para um titular sem Consent GRANTED para aquela finalidade.
     Tentativa => recipiente é **excluído** do disparo (não é erro global; é filtro + log).
BR3  Segment MUST ser definido por critérios sobre dados já existentes (Accounts/eventos), sem coletar
     dado novo desnecessário (minimização). ASSUMIDO (ver DL-0059): critério em criteria_json (jsonb)
     validado contra um catálogo fechado de campos permitidos (campo desconhecido => 400).
BR4  Campaign dispara via porta de newsletter (ACL) com idempotência por (campaignId, recipient) —
     não envia duas vezes ao mesmo destinatário. ASSUMIDO (ver DL-0055): porta NewsletterSender +
     adaptador mock rastreável em infra/integration/newsletter; DTO do provedor não cruza p/ o domínio.
BR5  Attribution liga um código/UTM a uma reserva: ao receber BookingConfirmed com código de campanha,
     registra a atribuição; publica CampaignConverted para a Intelligence. ASSUMIDO (ver DL-0057): o
     vínculo código→reserva é registrado por intake próprio do Marketing (POST /attribution, UNIQUE
     (campaign_code, booking_id)); ao receber BookingConfirmed com código pré-registrado, confirma a
     conversão. O evento BookingConfirmed NÃO é alterado no v1 (seam para UTM nativo no Booking).
BR6  Pedido de exclusão/portabilidade do titular MUST ser atendível (LGPD): consultar/remover dados de
     marketing do titular, preservando o que outra base legal obrigue a manter (ex.: fiscal no Compliance).
     ASSUMIDO (ver DL-0058): o erasure remove PII de marketing e anonimiza o consent, preservando um
     tombstone de revogação (status=REVOKED) para a supressão futura (BR2); attributions/métricas (sem
     PII) permanecem. Alcance exato a confirmar com o DPO (Confiança=Baixa).
```

## Input/Output Examples

```http
POST /api/marketing/consents
{ "subject":{"id":"8f1c...","type":"ACCOUNT"}, "purpose":"NEWSLETTER", "legalBasis":"CONSENT",
  "source":"signup-form" }
201 Created  { "id":"cs1...", "status":"GRANTED" }

POST /api/marketing/campaigns/{id}/send
200 OK  { "campaignId":"cmp9...", "targeted": 120, "suppressedNoConsent": 14, "queued": 106 }
```

## API Contracts

- `POST /api/marketing/consents` / `DELETE .../consents/{id}` (revoga) → 201 / 200.
- `GET /api/marketing/consents?subject=&purpose=` → estado atual + histórico.
- `POST /api/marketing/segments` / `GET .../segments/{id}/preview` → define/estima alcance.
- `POST /api/marketing/campaigns` / `POST .../campaigns/{id}/send` → cria/dispara (filtra por consentimento).
- `GET /api/marketing/attribution?campaignId=` → conversões atribuídas.
- OpenAPI atualizada; provedor de newsletter isolado na ACL.

## Events

- `CampaignSent` — `{campaignId, targeted, suppressed, occurredAt}`. Produtor: `marketing`.
- `CampaignConverted` — `{campaignId, bookingId, occurredAt}`. Consumidor: `intelligence` (atribuição).
- `ConsentRevoked` — `{subjectRef, purpose, occurredAt}`. Consumidor: supressão futura.

## Persistence Changes

```txt
V19__create_marketing.sql
  consents(
    id uuid PK, subject_id varchar not null, subject_type varchar not null,
    purpose varchar not null, legal_basis varchar not null, status varchar not null,
    source varchar null, created_at timestamptz not null, created_by varchar null
  )                                            -- revogação = nova linha (append/history)
  segments( id uuid PK, name varchar not null, criteria_json jsonb not null,
            created_at, updated_at timestamptz not null, version bigint not null )
  campaigns( id uuid PK, segment_id uuid not null, content_ref varchar null,
             window_from date null, window_to date null, status varchar not null,
             created_at, updated_at timestamptz not null, version bigint not null )
  campaign_sends( campaign_id uuid not null, recipient_ref varchar not null, sent_at timestamptz null,
                  UNIQUE (campaign_id, recipient_ref) )            -- idempotência (BR4)
  attributions( id uuid PK, campaign_code varchar not null, booking_id uuid not null,
                attributed_at timestamptz not null, UNIQUE (campaign_code, booking_id) )
```

O provedor de newsletter (`NewsletterSender`) fica em `infra/integration`. Consentimento é consultado
**antes** de enfileirar (BR2). Dados pessoais com **controle de acesso e trilha** (`security.md`).

## Validation Rules

- Application: filtro de consentimento no disparo (BR2); idempotência de envio/atribuição.
- Domain: estados de Consent/Campaign; critérios de Segment válidos.
- LGPD: atender exclusão/portabilidade (BR6) preservando obrigações legais de outras bases.

## Error Behavior

`marketing.campaign.not-found` → 404; `marketing.consent.not-found` → 404;
`marketing.segment.invalid` → 400. i18n em `messages_pt_BR.properties`. Erros não vazam dado pessoal.

## Observability Requirements

- Logar disparo (alvo, suprimidos por consentimento), revogações e conversões como evento de negócio
  (campaignId, correlation id), **sem PII**. Métricas: `campaign_sends_total`, `consent_suppressed_total`,
  `campaign_conversions_total`.

## Tests Required

- **Unit/domain:** filtro por consentimento (sem GRANTED → suprimido); idempotência de envio/atribuição;
  revogação impede envio futuro.
- **Integração (Testcontainers + provedor fake):** disparo envia só para quem consentiu e registra
  suprimidos; `BookingConfirmed` com código gera atribuição e `CampaignConverted`.
- **Regressão (LGPD):** revogar consentimento exclui o titular do próximo disparo (falha antes, passa depois).

## Acceptance Criteria

- Uma campanha só envia para titulares com consentimento; os demais são suprimidos e contados.
- Uma reserva com código de campanha é atribuída e vira sinal de conversão para o DSS.
- Pedido de exclusão do titular é atendido sem apagar o que a lei manda guardar.
- `./mvnw verify` verde.

## Open Questions

- ~~**Provedor de newsletter** e modelo de consentimento (double opt-in?)~~ — **ASSUMIDO (ver
  DL-0055):** porta `NewsletterSender` + adaptador mock rastreável (provedor real = novo adaptador,
  decisão do dono); modelo **single opt-in** no v1 (o modelo de dados já comporta double opt-in sem
  refator). Confirmar o provedor real e se o duplo opt-in será exigido.
- ~~Se a Acme precisa de **CRM completo**, avaliar **comprar**~~ — **ASSUMIDO (ver DL-0059):** este
  módulo é a **camada de consentimento/atribuição** (não CRM); CRM pleno (leads/funil/scoring/
  automações/conteúdo) fica **fora de escopo** (comprar e plugar este módulo). Decisão do dono se/
  quando comprar.
- **Alcance exato do apagamento LGPD (BR6)** — **ASSUMIDO (ver DL-0058, Confiança=Baixa):** anonimiza
  PII e preserva tombstone de revogação. **O que exatamente sobrevive ao expurgo é decisão de DPO/
  jurídico** — confirmar antes do primeiro uso real (expurgo é destrutivo).

## Out of Scope

Conteúdo criativo, CRM completo (comprar), envio sem consentimento (proibido).
