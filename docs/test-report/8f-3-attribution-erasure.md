# Caderno de testes — Slice 8f-3 (Marketing: Atribuição + CampaignConverted + erasure LGPD)

- **Spec:** SPEC-0019 (BR5 atribuição campanha→reserva + `CampaignConverted`; BR6 exclusão LGPD).
- **Decisões:** DL-0057 (intake próprio + confirmação na `BookingConfirmed`; não altera o evento do
  Booking), DL-0058 (erasure preserva tombstone de revogação anonimizado).

## Escopo

Terceira (e última) fatia do módulo `marketing`. Entrega:
- **Attribution** (`code→booking`, UNIQUE `(campaign_code, booking_id)`): intake próprio do Marketing
  (`POST /attribution`); ao receber **`BookingConfirmed`** com código pré-registrado, **confirma a
  conversão** e publica **`CampaignConverted`** para a Intelligence (consumidor-folha) — DL-0057. O
  evento `BookingConfirmed` **não** foi alterado (seam para UTM nativo no Booking).
- **Intelligence** consome `CampaignConverted` (sinal de conversão para o DSS, 8.2-F) sem inventar
  read-model fora de escopo (Rule Zero); a regra ArchUnit "aconselha, nunca comanda" segue verde.
- **Erasure LGPD** (`POST /erasure`, BR6/DL-0058): remove a PII de marketing do titular e **anonimiza**
  o log de consentimento, **preservando um tombstone de revogação** (status REVOKED, `subject_id`
  pseudonimizado por SHA-256) para a supressão futura; `attributions` (sem PII) **permanecem**; não
  toca outras bases legais (fiscal/financeiro/reserva — fora do módulo).

Endpoints: `POST /attribution`, `GET /attribution?campaignCode=`, `POST /erasure`. Migração V24
(tabela `attributions`).

Acceptance Criteria cobertos: "Uma reserva com código de campanha é atribuída e vira sinal de
conversão para o DSS." + "Pedido de exclusão do titular é atendido sem apagar o que a lei manda guardar."

## Casos de teste

### Integração (Testcontainers + `@RecordApplicationEvents`) — `AttributionAndErasureIntegrationTest` (5)
| Caso | Verifica | Regra |
|---|---|---|
| `confirmedBookingWithCodeIsAttributedAndEmitsCampaignConverted` | código pré-registrado + `BookingConfirmed` → atribuição `converted=true` + `CampaignConverted(campaignId,bookingId)` publicado | **BR5/DL-0057** |
| `confirmingTheSameBookingTwiceEmitsCampaignConvertedOnlyOnce` | reentrega de `BookingConfirmed` → `CampaignConverted` **uma vez** (idempotente) | DL-0057 |
| `confirmedBookingWithoutACodeIsNotAttributed` | `BookingConfirmed` sem código → nenhuma atribuição, nenhum evento (sem atribuição forçada) | DL-0057 |
| `registeringTheSameAttributionTwiceIsIdempotent` | 2º intake do mesmo `(code,booking)` → mesma linha (UNIQUE) | BR5 |
| `erasureSuppressesTheSubjectButPreservesTombstoneAndAttributions` | erasure → titular suprimido (REVOKED), `subject_id` original some, **tombstone anon-% REVOKED** existe, `attributions` preservadas | **BR6/DL-0058** |

### Arquitetura / regressões — verdes
- `ModularityTests`: grafo acíclico com `marketing → booking` (evento) e `intelligence → marketing`
  (evento `CampaignConverted`); nenhum ciclo.
- `ArchitectureTest` (13 regras): `INTELLIGENCE_ADVISES_NEVER_COMMANDS` verde — Intelligence só
  consome o **evento** exposto de Marketing, não a fachada/internal.
- `IntelligencePromoFxIntegrationTest` (regressão DSS): permanece verde após o novo consumo.
- `HttpErrorMappingCompletenessTest`: todas as exceções de marketing mapeadas.

## Resultado

```
AttributionAndErasureIntegrationTest  5 OK
ModularityTests                       1 OK
ArchitectureTest                     13 OK
IntelligencePromoFxIntegrationTest    3 OK (regressão DSS)
HttpErrorMappingCompletenessTest      1 OK
BUILD SUCCESS
```

Total acumulado do backend após a fatia: **341 testes** (336 da 8f-2 + 5 desta fatia).
O `./mvnw verify` completo da fase roda na branch de integração (resultado no relatório final).

## Cobertura / o que NÃO está coberto

- **Read-model de ROI de campanha (PromoConversion, 8.2-F):** território da Intelligence (Fase 7);
  aqui só o **sinal** de conversão é entregue (Rule Zero).
- **UTM nativo no Booking:** adiado (seam DL-0057); a fonte do código troca sem mexer em `attributions`.
- **Política fina de apagamento:** o alcance exato é decisão do DPO (DL-0058, Confiança=Baixa).

## Como reproduzir

```bash
cd backend
./mvnw test -Dtest=AttributionAndErasureIntegrationTest
./mvnw test -Dtest=IntelligencePromoFxIntegrationTest,ArchitectureTest,ModularityTests
./mvnw verify
```
