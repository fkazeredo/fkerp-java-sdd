# Caderno de testes — Slice 9a · CancellationPolicy como objeto + fonte administrável (SPEC-0010)

## Escopo

Política de cancelamento como **objeto** (`CancellationPolicy`: `CancellationType` enum com
comportamento, `PenaltyWindow`, `CostBearer`, `NoShowPolicy`, `Charge`/`ChargeKind`), com o
**cálculo da multa** pela janela aplicável (BR2/BR4, Money scale 2 HALF_UP) e a resolução do
`costBearer` merchant-of-record (BR8/DL-0021). Fonte **administrável por `scope_ref`**:
`CancellationPolicySource` (internal) + `V12__create_cancellation_policies.sql` + endpoints
`GET/PUT /api/products/{ref}/cancellation-policy` (`CancellationPolicyAdminService`). Erro
`cancellation.policy.invalid` → 400. Decisões: DL-0020 (mora no `booking`), DL-0021, DL-0022.
Cobre os Acceptance Criteria de configuração da SPEC-0010.

## Casos de teste

### Unitário — `CancellationPolicyTest` (6 casos)
| Caso | Verifica | Regra |
|---|---|---|
| picksTheTightestApplicableWindowForThePenalty | janela cujo `hoursBefore` é o menor ≥ horas-até-serviço; 10h→50%, 48h→25%, 24h inclusivo→50% | BR2 |
| chargesNoPenaltyWhenNoWindowApplies | 100h sem janela aplicável → multa 0 | BR2 |
| standardWithoutWindowsAndCustomWithoutWindowsChargeZero | STANDARD/CUSTOM sem janelas → 0 | BR2/BR4 |
| roundsThePenaltyHalfUpToScaleTwo | 33,33% de 100,00 → 33,33 (HALF_UP scale 2) | convenção Money |
| allSalesFinalChargesNoWindowPenaltyButResolvesCostBearerByMerchantFlag | ALL_SALES_FINAL não usa janela; afiliado→SUPPLIER, merchant→ACME | BR3/BR8 |
| rejectsMalformedWindows | `hoursBefore<0`, `pct>1`, `pct<0` → `CancellationPolicyInvalidException` | Error Behavior |

### Integração (Testcontainers/Postgres) — `CancellationPolicyAdminIntegrationTest` (4 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| returnsSafeDefaultForAnUnknownScope | scope desconhecido → default seguro (STANDARD, afiliado, sem janelas, sem fee) | API GET |
| upsertsAndReadsBackTheAdministeredPolicy | PUT grava (janelas + no-show fee + waiver) e GET lê de volta | API PUT/GET |
| storesTheMerchantOfRecordFlagForAllSalesFinal | ALL_SALES_FINAL com `merchantOfRecord=true` persiste o flag | BR8 |
| rejectsAMalformedWindow | janela com `penaltyPct=1.50` → 400 `cancellation.policy.invalid` | Error Behavior |

### Arquitetura
`HttpErrorMappingCompletenessTest` continua verde com a nova exceção mapeada (400).

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 145` (+10 da fatia). Portões verdes:
ArchUnit, Spring Modulith (10 módulos — o cancelamento mora no `booking`, DL-0020), Spotless,
Checkstyle (0 violações), completude do `HttpErrorMapping`.

## Cobertura — o que NÃO está coberto (e por quê)

- Congelamento na confirmação e os encargos: são a **fatia 9b** (esta fatia só modela a política e a
  fonte administrável).
- Aplicação do no-show: **fatia 9c**.
- Autorização administrativa real do PUT: o `UserContextProvider` de dev faz as vezes até a SPEC-0024
  (Identity).
- Tela Angular: backend-first (como Fases 2–3).

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=CancellationPolicyTest                       # unit (domínio puro)
cd backend && ./mvnw verify -Dtest=CancellationPolicyAdminIntegrationTest     # integração (Docker up)
cd backend && ./mvnw verify                                                   # tudo + portões
```
