# Plano da Fase 8f — Marketing (SPEC-0019)

> Modo autônomo (RUN-PHASE). FASE-ALVO = 8, **escopo SPEC-0019 apenas**. Implementa o contexto de
> Marketing B2B: **consentimento LGPD** como cidadão de primeira classe (pré-condição de envio),
> **segmentação** sobre dados existentes, **campanha** + disparo via **porta de newsletter (ACL)**
> com mock rastreável, e **atribuição** (campanha→reserva) publicando `CampaignConverted` para a
> Intelligence. Versão alvo: **0.14.0** (próximo MINOR após 0.13.0, ADR 0015).

## Contexto e fronteiras

Novo módulo `com.fksoft.domain.marketing` (16º módulo Modulith). Agregados: `Consent` (log
append-only, DL-0056), `Segment` (critério jsonb validado, DL-0059), `Campaign` (+ `campaign_sends`
idempotência), `Attribution` (intake próprio, DL-0057).

Marketing **governa o consentimento, não é CRM** (DL-0059): entrega consentimento + segmentação
simples + campanha/disparo (ACL) + atribuição. CRM pleno = comprar (Out of Scope da spec).

Direção de dependências (grafo **acíclico**, Spring Modulith verify):

- `marketing → booking` **só por evento** (`BookingConfirmed`, para confirmar atribuição, DL-0057).
- `marketing → money`/`error` (kernels).
- porta `NewsletterSender` no módulo; adaptador `infra.integration.newsletter` (mock rastreável,
  DL-0055) — o **DTO do provedor nunca cruza** para o domínio (regra ArchUnit nova).
- `intelligence → marketing` (consome o evento `CampaignConverted`; consumidor-folha, SPEC-0013 BR2).
- Marketing **NÃO** depende de Accounts/Finance/Compliance no v1 (preview começa pela base de
  consentimento; seam rastreável para enriquecer com Accounts quando necessário, DL-0059).

LGPD (security.md): dado pessoal mascarado em log; evento de negócio logado com `campaignId` +
correlation id, **sem PII**; exclusão preserva tombstone de revogação (DL-0058). Sem Spring Security
de papéis ainda (SPEC-0024/Fase 13 adia OIDC); o ator vem do `UserContextProvider` (stub) e a
autorização do erasure usa o papel exposto no `UserContext` (como o Diretor da CommercialPolicy).

## Fatias (uma feature branch por fatia → merge --no-ff em feature/8f-integration)

### 8f-1 — Consent (log append-only) + API + LGPD  ·  BR1/BR6 (parcial)
- Agregado `Consent` (`internal`): id, subjectId, subjectType (ACCOUNT|AGENT), purpose
  (NEWSLETTER), legalBasis (CONSENT|LEGITIMATE_INTEREST…), status (GRANTED|REVOKED), source,
  createdAt, createdBy. **Linha imutável**; revogar/reconsentir = nova linha (DL-0056).
- `ConsentRepository` (última por `(subjectType, subjectId, purpose)`); `ConsentState`/`ConsentView`.
- `MarketingService.grantConsent / revokeConsent / consentState / consentHistory`; evento
  `ConsentRevoked`. Exceção `ConsentNotFoundException` (404).
- Migração `V24` (parte): `consents` + índice `(subject_type, subject_id, purpose, created_at DESC)`.
- Controller: `POST /api/marketing/consents`, `DELETE /consents/{id}` (revoga),
  `GET /consents?subject=&purpose=` (estado atual + histórico).
- i18n (`marketing.consent.not-found`), `HttpErrorMapping`, OpenAPI.
- Testes: unit (transição GRANTED→REVOKED→GRANTED preserva histórico; estado = última linha);
  integração (persistência append, GET estado+histórico, revogar).

### 8f-2 — Segment + Campaign + disparo via NewsletterSender (ACL)  ·  BR2/BR3/BR4
- `Segment` (`internal`): id, name, `criteriaJson` (jsonb), audit, version. Value object
  `SegmentCriteria` validado contra catálogo fechado (DL-0059); campo desconhecido →
  `SegmentInvalidException` (`marketing.segment.invalid`, 400).
- `Campaign` (`internal`): id, segmentId (valor), `code` (único, atribuição), contentRef,
  windowFrom/To, status, audit, version. `campaign_sends (campaign_id, recipient_ref) UNIQUE` (BR4).
- Porta `NewsletterSender` + value `NewsletterMessage`/`NewsletterSendResult`; adaptador
  `infra.integration.newsletter.SimulatedNewsletterSender` (mock rastreável, classifica falha).
- `MarketingService.defineSegment / previewSegment / createCampaign / sendCampaign`:
  `sendCampaign` **consulta o consentimento ANTES de enfileirar** (BR2) — só `GRANTED` para a
  finalidade entram; os demais são **suprimidos e contados**; idempotência por `(campaignId,
  recipient)` (BR4). Evento `CampaignSent {campaignId, targeted, suppressed, occurredAt}`.
- Migração `V24` (parte): `segments`, `campaigns`, `campaign_sends`.
- Controller: `POST /segments`, `GET /segments/{id}/preview`, `POST /campaigns`,
  `POST /campaigns/{id}/send`. i18n, OpenAPI.
- Métricas: `campaign_sends_total`, `consent_suppressed_total`.
- Testes: unit (filtro de consentimento: sem GRANTED → suprimido; critério inválido → 400);
  integração com **provedor fake** (envia só p/ quem consentiu, registra suprimidos; reenvio não
  duplica — idempotência); regressão LGPD (revogar → suprimido no próximo disparo: falha antes,
  passa depois). Regra ArchUnit: DTO da newsletter não cruza para o domínio.

### 8f-3 — Attribution (campanha→reserva) + CampaignConverted + erasure LGPD  ·  BR5/BR6
- `Attribution` (`internal`): id, campaignCode, bookingId, attributedAt; `UNIQUE (campaign_code,
  booking_id)` (idempotência, DL-0057).
- `MarketingService.attribute(campaignCode, bookingId)` (intake); listener interno
  `BookingEventsListener` consome `BookingConfirmed` e, se houver código pré-registrado p/ aquele
  booking, **confirma a conversão** publicando `CampaignConverted {campaignId, bookingId,
  occurredAt}`. Sem código → nada (sem atribuição forçada).
- `intelligence` consome `CampaignConverted` (read-model de conversão por campanha; advises-never-
  commands). Reusa o padrão de `FxSalesEventsListener`.
- `MarketingService.erase(subject, actor)` (BR6/DL-0058): anonimiza PII de marketing do titular,
  encerra consentimento preservando **tombstone de revogação** (supressão futura); `attributions`
  (sem PII) permanecem. Autorização por papel (UserContext).
- Migração `V24` (parte): `attributions` + UNIQUE.
- Controller: `POST /attribution`, `GET /attribution?campaignId=`, `POST /erasure`.
- Métrica: `campaign_conversions_total`. i18n, OpenAPI.
- Testes: integração (`BookingConfirmed` com código pré-registrado → atribuição + `CampaignConverted`
  consumido pela Intelligence; sem código → nenhuma atribuição); idempotência de atribuição;
  erasure (suprime do próximo disparo, mas mantém prova de revogação; attributions preservadas).

## Definition of Done por fatia (TUTORIAL §3)
Critérios de aceite da spec → testes verdes; `./mvnw verify` verde (ArchUnit + Modulith);
migração Flyway idempotente (V24); `DomainException` code==chave i18n (pt-BR + fallback); sem
exceção crua de banco; OpenAPI atualizada; observabilidade (evento de negócio logado, PII mascarada,
correlation id); costura adiada = mock rastreável (NewsletterSender; seam de UTM no Booking);
Spotless aplicado; Conventional Commits; caderno de testes atualizado.

## Migração
**Única migração `V24__create_marketing.sql`** (próxima após V23), idempotente, cobrindo as 5
tabelas (`consents`, `segments`, `campaigns`, `campaign_sends`, `attributions`). Nunca editar V1–V23
já aplicadas.

## Riscos / pontos de atenção
- **DL-0058 (Baixa/Cara):** alcance do apagamento LGPD é decisão de DPO; o expurgo é destrutivo.
  Mitigação: anonimizar + preservar tombstone (defensável); marcar para confirmação do dono.
- **Atribuição sem UTM nativo no Booking (DL-0057):** intake próprio + seam rastreável; quando o
  Quoting capturar UTM, troca-se a fonte do código sem mexer na tabela.
- **jsonb do Segment:** validado por catálogo fechado para não virar saco de gato nem violar
  minimização (BR3).

## O que NÃO entra (Out of Scope da spec / Rule Zero)
Conteúdo criativo; CRM completo (comprar); double opt-in (modelo comporta, não implementado);
envio sem consentimento (proibido); coleta de dado pessoal novo; leitura cross-contexto pesada no
preview (começa pela base de consentimento).
