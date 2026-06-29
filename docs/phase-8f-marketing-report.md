# Relatório da Fase 8f — Marketing (SPEC-0019)

- **Fase:** 8f (Marketing — subdomínio Supporting). **Release:** 0.14.0 (próximo MINOR após 0.13.0).
- **Base:** `origin/develop` @ 761c159. **Branch de integração:** `feature/8f-integration`.
- **Resultado:** `./mvnw verify` **BUILD SUCCESS** — **342 testes**, 0 falhas; ArchUnit 13 regras;
  Spring Modulith acíclico (**16º módulo** `marketing`); Spotless 0 alterações; Checkstyle 0 violações.

## Fatias entregues

| Fatia | Entrega | BRs |
|---|---|---|
| **8f-1** Consent log | Agregado `Consent` **append-only**; estado = última linha por (titular, finalidade); conceder/revogar/consultar; `ConsentRevoked`; V24 `consents` | BR1 |
| **8f-2** Segment + Campaign + disparo | `Segment` (`criteria_json` validado por catálogo fechado), `Campaign` (`code` único); **disparo filtra por consentimento antes de enfileirar** (suprime+conta), idempotente por (campanha, destinatário); porta `NewsletterSender` (ACL, mock rastreável) | BR2, BR3, BR4 |
| **8f-3** Atribuição + conversão + erasure | `Attribution` (intake `code→booking`, UNIQUE) + confirmação na `BookingConfirmed` → `CampaignConverted` (consumido pela Intelligence); **exclusão LGPD** anonimiza PII e preserva tombstone de revogação | BR5, BR6 |

## Arquivos (alto nível)

- **Domínio (novo módulo `com.fksoft.domain.marketing`):** `MarketingService` (fachada); agregados
  `internal/{Consent, Segment, Campaign, CampaignSend, Attribution}` + repositórios + codec de critério;
  value objects/views/commands/eventos (`SubjectRef`, `SegmentCriteria`, `ConsentState`,
  `CampaignSendResult`, `ErasureResult`, `CampaignSent`, `CampaignConverted`, `ConsentRevoked`);
  porta `NewsletterSender`; exceções (`Consent*`, `Segment*`, `Campaign*`, `Newsletter*`);
  listener `internal/MarketingBookingEventsListener` (consome `BookingConfirmed`).
- **Infra (ACL):** `infra/integration/newsletter/{NewsletterProviderRequest, NewsletterProviderResponse,
  SimulatedNewsletterSender}` (mock rastreável, classifica falha, DTO não cruza p/ domínio).
- **Intelligence:** método `onCampaignConverted` + `internal/CampaignEventsListener` (consumidor-folha).
- **Delivery:** `MarketingController` (11 endpoints) + DTOs de request/response.
- **Transversais:** `HttpErrorMapping` (+4 exceções), i18n pt-BR + fallback, `OpenApiConfig` (0.14.0),
  `ArchitectureTest` (+1 regra ACL newsletter), `pom.xml` (0.14.0).

## Migrações

- **V24__create_marketing.sql** (idempotente): `consents` (+ índice (subject, purpose, created_at DESC)),
  `segments` (criteria_json jsonb), `campaigns` (code único), `campaign_sends` (PK composto =
  idempotência), `attributions` (UNIQUE (campaign_code, booking_id)). Sem FK cross-contexto.

## OpenAPI

- 11 endpoints novos sob `/api/marketing/*` (consents, segments, campaigns, attribution, erasure);
  descrição e versão atualizadas para 0.14.0.

## Testes por tipo (resultado: todos verdes)

- **Unitário (domínio):** `SegmentCriteriaTest` (catálogo fechado/minimização) — 4.
- **Unitário (ACL):** `SimulatedNewsletterSenderTest` (aceitação + classificação de falha) — 3.
- **Integração (Testcontainers + mock):** `ConsentApiIntegrationTest` (5), `CampaignSendIntegrationTest`
  (BR2 suprime+conta, BR4 idempotência, **regressão LGPD** revogar→suprimido) (5),
  `AttributionAndErasureIntegrationTest` (atribuição + `CampaignConverted` idempotente; sem código → nada;
  erasure preserva tombstone, mantém attributions) (5).
- **Arquitetura:** ArchUnit 13 regras (inclui `DOMAIN_MUST_NOT_DEPEND_ON_NEWSLETTER_ADAPTER`), Spring
  Modulith (grafo acíclico), `HttpErrorMappingCompletenessTest`.
- **Regressão DSS:** `IntelligencePromoFxIntegrationTest` permanece verde (novo consumo não quebra a
  regra "aconselha, nunca comanda").

Saída final: `Tests run: 342, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS`.

## Decisões (decision-log)

| DL | Título | Confiança | Reversibilidade |
|---|---|---|---|
| [DL-0055](decision-log/DL-0055-marketing-newsletter-acl-and-single-opt-in.md) | Porta `NewsletterSender` (ACL) + mock rastreável; single opt-in v1 | Média | Moderada |
| [DL-0056](decision-log/DL-0056-marketing-consent-append-history-current-state.md) | Consent append-only; estado = última linha | Alta | Moderada |
| [DL-0057](decision-log/DL-0057-marketing-attribution-intake-and-campaign-converted.md) | Atribuição por intake próprio; `BookingConfirmed` não alterado | Média | Moderada |
| **[DL-0058](decision-log/DL-0058-marketing-lgpd-erasure-preserves-revocation-and-metrics-as-logs.md)** | **Exclusão LGPD preserva tombstone anonimizado** | **Baixa** | **Cara** |
| [DL-0059](decision-log/DL-0059-marketing-segment-criteria-json-and-crm-buy-vs-build.md) | `criteria_json` validado por catálogo; "não é CRM" | Média | Moderada |

> **Destaque (Baixa/Cara): DL-0058** — o alcance exato do apagamento LGPD é decisão de DPO/jurídico e o
> expurgo é destrutivo (PII não volta). O valor adotado (anonimizar + tombstone) é defensável e deve ser
> confirmado com o DPO antes do primeiro uso real.

## Riscos / o que fica para a próxima fase

- **DL-0058:** confirmar a política de apagamento com o DPO.
- **UTM nativo no Booking** (DL-0057) e **double opt-in / provedor real** (DL-0055): evoluções aditivas.
- **Read-model de ROI de campanha** (PromoConversion, 8.2-F): Intelligence/Fase 7 — aqui só o sinal.
- **Preview com leitura cruzada de Accounts/Booking:** v1 começa pela base de consentimento (seam).
