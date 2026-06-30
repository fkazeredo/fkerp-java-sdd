# Caderno de testes — Slice 8g-2 (Portfolio: metas + realizado vs meta por eventos de venda)

- **Spec:** SPEC-0020 (Portfolio — representação)
- **Acceptance Criteria cobertos:** definir meta anual de receita; o progresso da meta reflete as
  **vendas confirmadas** da marca; `./mvnw verify` verde.
- **Regras cobertas:** BR3 (`BrandGoal`: brandRef/period/metric/target; única por (marca, período,
  métrica)), BR4 (realizado projetado de eventos de venda — `BookingConfirmed` VOLUME e
  `SpreadRealized` REVENUE — read-model, sem alterar a venda), BR6 (Portfolio não precifica nem
  comanda a venda).
- **Decisão:** DL-0062 (intake próprio `booking→brand` + projeção idempotente; `caseId→booking` via
  `ReconciliationCaseOpened`; **não** altera o evento da venda). Confiança=Baixa.

## Casos de teste

### Unitário — `BrandGoalTest` (domínio, `internal`)
| Caso | Verifica | Regra |
|---|---|---|
| `definesARevenueGoalInBrl` | meta REVENUE em BRL | BR3 |
| `definesAVolumeGoal` | meta VOLUME por contagem | BR3 |
| `rejectsAMalformedPeriod` | período `2026/06` e `2026-13` → erro | BR3 |
| `rejectsARevenueGoalWithoutAPositiveBrlTarget` | REVENUE sem valor / zero / não-BRL → erro | BR3/DL-0062 |
| `rejectsAVolumeGoalWithoutAPositiveCount` | VOLUME sem contagem positiva → erro | BR3 |

### Integração (Testcontainers/Postgres) — `GoalRealizedProjectionIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `definingAGoalIsUniquePerBrandPeriodMetric` | meta única por (marca, período, métrica) → 2ª rejeitada | BR3 |
| `confirmedSalesOfTheBrandIncrementTheVolumeRealized` | `BookingConfirmed` de marca atribuída soma VOLUME; venda não atribuída **não conta**; attainment 20% | BR4/DL-0062 |
| `volumeProjectionIsIdempotentPerBooking` | re-entrega do mesmo `BookingConfirmed` não soma 2x | BR4 |
| `realizedSpreadOfTheBrandAccumulatesTheRevenueRealized` | `ReconciliationCaseOpened` liga caso→reserva; `SpreadRealized` soma REVENUE (BRL); attainment 40% (480k/1.2M) | BR4/DL-0062 |
| `revenueProjectionIsIdempotentPerCase` | re-entrega do mesmo `SpreadRealized` não soma 2x | BR4 |
| `aSaleWithNoAttributedBrandContributesNothing` | venda sem intake de marca não move meta nenhuma (sem atribuição forçada) | BR4/DL-0062 |
| `salesOutsideThePeriodDoNotCount` | venda em 2025 não conta na meta de 2026 | BR4 |
| `attributeSaleIsIdempotentPerBooking` | re-registrar a mesma reserva retorna o vínculo (1 linha, sem erro) | BR4/DL-0062 |

### Arquitetura (gates)
| Caso | Verifica |
|---|---|
| `ArchitectureTest.PORTFOLIO_REFERENCES_NEVER_COMMANDS_THE_SALE` (nova) | **BR6 com dentes:** `portfolio` não depende de `*Service` de outro módulo nem do `internal` alheio — referencia/projeta, **nunca** comanda/precifica a venda |
| `ModularityTests` | Modulith aceita `portfolio` consumindo só eventos de booking/reconciliation; **acíclico** |
| `HttpErrorMappingCompletenessTest` | `BrandGoalInvalidException` mapeada (400) |

## Resultado

- **`./mvnw -o test -Dtest=BrandGoalTest,GoalRealizedProjectionIntegrationTest,ArchitectureTest,
  ModularityTests,HttpErrorMappingCompletenessTest` → verde** (0 falhas).
- **Spotless** verde; **Checkstyle** 0 violações.
- A contagem total e a saída do `./mvnw verify` consolidado da fase estão no release note 0.15.0 e no
  relatório final da fase.

## Cobertura

- **Coberto:** definição de meta (VOLUME/REVENUE, unicidade), intake venda→marca idempotente,
  projeção de VOLUME (BookingConfirmed) e REVENUE (SpreadRealized via ReconciliationCaseOpened),
  idempotência por evento, filtro por período (ano), attainment, e a regra de fronteira BR6.
- **NÃO coberto (fora de escopo / Rule Zero):** preço/comissão (não moram aqui — BR6); FX da receita
  (só spread em BRL conta — DL-0062); tela Angular (módulo de referência/back-office, sem jornada de
  tela nesta fase); captura de marca nativa no fluxo de venda (seam DL-0062, decisão de negócio);
  parâmetro governado da antecedência de expiração (DL-0063).

## Como reproduzir

```bash
cd backend
./mvnw -o test -Dtest='BrandGoalTest,GoalRealizedProjectionIntegrationTest'   # unit + integração
./mvnw -o test -Dtest='ArchitectureTest,ModularityTests'                      # gates (inclui BR6)
./mvnw verify                                                                 # suíte completa + portões
```
