# Caderno de testes — Slice 9c · NoShowPolicy (SPEC-0010 BR6)

## Escopo

A `NoShowPolicy` (`fee`, `waivedIfFlightCancelled`), lida do snapshot congelado. Endpoint
`POST /api/bookings/{id}/no-show` (corpo opcional `{flightCancelledProof}`): cobra a fee, **dispensa**
com prova de voo cancelado quando a política permite (BR6); grava `Charge` `NO_SHOW` quando cobrada;
publica `BookingNoShow` + `NoShowCharged {bookingId, fee, waived, occurredAt}`. A verificação de
conformidade do documento de prova é do **Compliance** (fora de escopo — DL-0023): o flag é
rastreável. Sem migração nova (reusa `cancellation_charges` e o snapshot da 9b).

## Casos de teste

### Unitário — `NoShowPolicyTest` (4 casos)
| Caso | Verifica | Regra |
|---|---|---|
| chargesTheFeeWhenNoProofIsGiven | sem prova → cobra a fee; `isWaived=false` | BR6 |
| waivesTheFeeWithProofWhenThePolicyAllows | `waivedIfFlightCancelled=true` + prova → fee dispensada (null); `isWaived=true` | BR6 |
| doesNotWaiveWithProofWhenThePolicyDoesNotAllowIt | `waivedIfFlightCancelled=false` + prova → cobra mesmo assim | BR6 |
| chargesNothingWhenNoFeeIsConfigured | sem fee configurada → nada a cobrar | BR6 |

### Integração (Testcontainers/Postgres) — `NoShowIntegrationTest` (2 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| noShowChargesTheConfiguredFee | scope com fee 90,00 BRL, no-show sem prova → `NoShowResult` com NO_SHOW 90,00; 1 linha em `cancellation_charges` | "No-show de carro cobra fee" |
| noShowWaivesTheFeeWithProofOfACancelledFlight | scope `waivedIfFlightCancelled=true`, no-show com prova → `waived=true`, sem charge; 0 linhas | "dispensada com prova de voo cancelado" |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 157` (+6 da fatia). Portões verdes:
ArchUnit, Spring Modulith (10 módulos), Spotless, Checkstyle (0 violações).

## Cobertura — o que NÃO está coberto (e por quê)

- **Conformidade do documento de prova** (é hábil/idôneo? no cofre? na retenção?): do **Compliance**
  (SPEC-0008); aqui a prova é um fato informado (DL-0023). Troca para uma porta `ProofVerifier` é
  barata e localizada.
- **Consumidor** de `NoShowCharged`: Finance/Payout (fases futuras); evento in-process.
- Tela Angular: backend-first.

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=NoShowPolicyTest                # unit (domínio puro)
cd backend && ./mvnw verify -Dtest=NoShowIntegrationTest         # integração (Docker up)
cd backend && ./mvnw verify                                      # tudo + portões
```
