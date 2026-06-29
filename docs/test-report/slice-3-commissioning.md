# Caderno de testes — Slice 3: Commissioning (SPEC-0004)

## Escopo

Cálculo puro da comissão de duas pontas (fornecedor a receber, agente a pagar) e o spread derivado,
com % fixos (BR1), `pct ∈ [0,1]` e base ≥ 0 (BR2), spread negativo **exposto** e não bloqueado
(BR3). Fatia **stateless** (sem tabela). Fachada `CommissionCalculator` consumida por Quoting.
Introduz o kernel compartilhado `Money` (`com.fksoft.domain.money`, não-módulo, como `domain.error`).

## Casos de teste

### Unitário — `CommissionService` (`CommissionServiceTest`)
| Caso | Verifica | Regra |
|---|---|---|
| `computesSupplierAgentAndSpread` | USD 500, 15%/10% → 75 / 50 / 25, `spreadNegative=false` | BR1 / AC1 |
| `exposesNegativeSpreadWhenAgentRateExceedsSupplier` | 15%/20% → spread −25, `spreadNegative=true` | BR3 / AC2 |
| `roundsHalfUp` | 1,00 × 0,125 → 0,13 (HALF_UP) | BR1 (arredondamento) |
| `acceptsBoundaryPercentages` | pct 0 e 1 aceitos | BR2 (limites) |
| `rejectsPercentageOutOfRangePointingToField` | pct 1,5 → `commissioning.pct.invalid` (campo `supplierCommissionPct`) | BR2 / AC3 |
| `rejectsNegativeBase` | base negativa → `commissioning.base.invalid` | BR2 |

### Integração (Testcontainers) — `CommissioningIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `previewsTheTwoSidedDecomposition` | POST preview → 200 com 75/50/25, `spreadNegative=false` | AC1 |
| `rejectsOutOfRangePercentageWith400` | pct 1,5 → 400 `commissioning.pct.invalid` | AC3 |

### Arquitetura
Módulo `commissioning` (`@ApplicationModule`) verificado; o kernel `Money` é não-módulo (sem
fronteira Modulith), referenciável por qualquer módulo como o JDK/Spring.

## Resultado

`./mvnw verify` → **BUILD SUCCESS**. `Tests run: 54` (Slice 2: 46 → +8). Spotless clean, Checkstyle
0 violations.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** (preview da decomposição) — **pendente** (leva de frontend da Fase 1).
- **Q4** (faixas de override retroativas) e **Q5** (escopo da comissão do agente) — **adiadas** (ROADMAP
  recomenda fixo/global no v1); o cálculo já recebe os % prontos, então não houve decisão que afete o
  código nesta fatia. Entram com `OverrideTier`/`CommercialPolicy` em spec futura.
- Eventos de accrual/reversal — fora de escopo (disparam em Booking/Reconciliation).

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=CommissionServiceTest
```
