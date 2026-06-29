# Caderno de testes — Slice 8f-2 (Marketing: Segment + Campaign + disparo via NewsletterSender ACL)

- **Spec:** SPEC-0019 (BR2 filtro de consentimento; BR3 segmentação por dados existentes; BR4
  idempotência de envio).
- **Decisões:** DL-0055 (porta NewsletterSender + mock rastreável), DL-0059 (critério jsonb validado
  por catálogo fechado; fronteira "não é CRM").

## Escopo

Segunda fatia do módulo `marketing`. Entrega:
- **Segment** com `criteria_json` (jsonb) **validado** contra um **catálogo fechado** de campos
  (`accountType, purpose, minVolume, route, region`) — minimização (BR3): campo desconhecido → 400.
- **Campaign** sobre um segmento (valor), com `code` público único (atribuição) e janela.
- **Disparo** (`POST /campaigns/{id}/send`): **filtra por consentimento ANTES de enfileirar** (BR2)
  — só titulares com `GRANTED` vigente entram; os demais são **suprimidos e contados**; **idempotente
  por (campaignId, recipient)** (BR4) — reenvio não duplica. Envio via porta **NewsletterSender**
  (ACL, DL-0055) com adaptador mock rastreável; o **DTO do provedor não cruza para o domínio**
  (regra ArchUnit nova).
- **Preview** (`GET /segments/{id}/preview`): alcance = titulares consentidos (base do módulo).

Endpoints: `POST /segments`, `GET /segments/{id}/preview`, `POST /campaigns`, `GET /campaigns/{id}`,
`POST /campaigns/{id}/send`. Migração V24 (tabelas `segments`, `campaigns`, `campaign_sends`).

Acceptance Criteria cobertos: "Uma campanha só envia para titulares com consentimento; os demais são
suprimidos e contados."

## Casos de teste

### Unitário — `SegmentCriteriaTest` (4)
| Caso | Verifica | Regra |
|---|---|---|
| `acceptsKnownCatalogFields` | Campos do catálogo são aceitos | BR3/DL-0059 |
| `rejectsUnknownField` | Campo fora do catálogo (ex.: `ssn`) → `SegmentInvalidException` (minimização) | BR3 |
| `rejectsBlankValue` | Valor em branco → inválido | BR3 |
| `rejectsEmptyCriteria` | Critério vazio → inválido | BR3 |

### Unitário — `SimulatedNewsletterSenderTest` (3, ACL)
| Caso | Verifica | Regra |
|---|---|---|
| `acceptsANormalRecipientAndReturnsAProviderRef` | Destinatário normal → `providerMessageRef` | DL-0055 |
| `classifiesTimeoutFailure` | Prefixo `FAIL_TIMEOUT` → `NewsletterException(TIMEOUT)` (sem "enviado" falso) | messaging-and-integrations.md |
| `classifiesRejectedFailure` | Prefixo `FAIL_REJECT` → `NewsletterException(REJECTED)` | idem |

### Integração (Testcontainers + mock) — `CampaignSendIntegrationTest` (5)
| Caso | Verifica | Regra |
|---|---|---|
| `sendsOnlyToConsentedSubjectsAndCountsSuppressed` | 3 alvos, 1 revogado → `targeted=3, suppressed=1, queued=2`; 2 linhas em `campaign_sends` | **BR2** |
| `reSendingDoesNotDoubleMailTheSameRecipient` | 2º disparo → `queued=0`; `campaign_sends` continua 2 (não 4) | **BR4** |
| `revokingConsentExcludesTheSubjectFromTheNextDispatch` | revogar acc-2 → `suppressed=1, queued=1` | **Regressão LGPD** (falha antes, passa depois) |
| `previewCountsCurrentlyConsentedReachableSubjects` | preview = 2 consentidos | BR3/DL-0059 |
| `sendingAMissingCampaignIsNotFound` | campanha inexistente → `CampaignNotFoundException` (404) | Error Behavior |

### Arquitetura (gates) — verdes
- **`ArchitectureTest` (13 regras)**: nova regra `DOMAIN_MUST_NOT_DEPEND_ON_NEWSLETTER_ADAPTER` —
  o DTO do provedor de newsletter (`infra.integration.newsletter`) **não cruza** para o domínio (ACL).
- `ModularityTests`: grafo acíclico com `marketing` consumindo só eventos/kernels.
- `HttpErrorMappingCompletenessTest`: `SegmentInvalidException` (400), `CampaignInvalidException`
  (400), `CampaignNotFoundException` (404), `NewsletterException` (502) mapeadas.

## Resultado

```
SegmentCriteriaTest             4 OK
SimulatedNewsletterSenderTest   3 OK
CampaignSendIntegrationTest     5 OK
ArchitectureTest               13 OK   (nova regra ACL newsletter)
ModularityTests                 1 OK
HttpErrorMappingCompletenessTest 1 OK
BUILD SUCCESS
```

Total acumulado do backend após a fatia: **336 testes** (324 da 8f-1 + 12 desta fatia).

## Cobertura / o que NÃO está coberto

- **Atribuição (BR5) e erasure LGPD (BR6):** fatia 8f-3.
- **Preview com leitura cruzada de Accounts/Booking:** v1 começa pela base de consentimento (seam
  rastreável, DL-0059) — não há varredura cross-contexto pesada.
- **Double opt-in / provedor real:** fora do v1 (DL-0055).

## Como reproduzir

```bash
cd backend
./mvnw test -Dtest=SegmentCriteriaTest,SimulatedNewsletterSenderTest,CampaignSendIntegrationTest
./mvnw test -Dtest=ArchitectureTest,ModularityTests
./mvnw verify
```
