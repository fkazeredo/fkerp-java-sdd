# Caderno de testes — Slice 7a · Finance (SPEC-0015)

## Escopo

Seam mínimo do Finance: razão AP/AR (`LedgerEntry`) + máquina de período (`AccountingPeriod`) com
fechamento mensal. Cobre os Acceptance Criteria da SPEC-0015 que **não dependem** do veto real do
Compliance (que chega na Slice 7c): criar lançamento PROVISIONAL, confirmar, fechar período sem veto,
e rejeitar lançamento em período fechado. Nesta fatia o `CloseGuard` é o default permissivo rastreável
`AlwaysAllowsCloseGuard` (seam para o Compliance, DL-0014).

Decisões aplicadas: DL-0013 (moeda original, total por moeda), DL-0014 (construir o seam mínimo).

## Casos de teste

### Unitário / domínio — `PeriodAndEntryStateMachineTest` (7 casos)
| Caso | Verifica | Regra |
|---|---|---|
| entryIsBornProvisional | lançamento nasce PROVISIONAL | BR2 |
| entryMovesProvisionalToConfirmedToSettled | PROVISIONAL→CONFIRMED→SETTLED | BR2 |
| entryRejectsSkippingConfirmation | PROVISIONAL→SETTLED proibido | BR2 |
| settledEntryIsTerminal | SETTLED é terminal | BR2 |
| periodIsBornOpenAndSeals | OPEN→CLOSING→CLOSED | BR3 |
| abortedClosingReturnsToOpen | veto reabre o período (CLOSING→OPEN) | BR3 |
| periodIdRejectsMalformedValue | `AccountingPeriodId` valida YYYY-MM | — |

### Integração (Testcontainers/Postgres) — `FinanceIntegrationTest` (6 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| createsPayableEntryAsProvisional | `POST /entries` → 201, status PROVISIONAL, moeda original | "lançar devo ao fornecedor cria PROVISIONAL" |
| confirmsAProvisionalEntry | `POST /entries/{id}/confirm` → CONFIRMED | BR2 |
| closesAnOpenPeriodWhenNothingVetoes | `POST /periods/{m}/close` → CLOSED + closedAt (guard permissivo) | BR3 |
| rejectsAnEntryAgainstAClosedPeriod | lançar em período CLOSED → 409 `finance.period.closed` | BR4 |
| rejectsAMalformedPeriod | período `2026/06` → 400 `finance.period.invalid` | validação |
| totalsPeriodPerCurrency | `GET /periods/{m}` agrega AP/AR por moeda | DL-0013 |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 95, Failures: 0, Errors: 0, Skipped: 0`
(82 herdados da Fase 1 + 13 do Finance). Portões verdes: ArchUnit, Spring Modulith (8 módulos),
Spotless, Checkstyle, completude do `HttpErrorMapping`.

## Cobertura — o que NÃO está coberto (e por quê)

- **Veto real do Compliance** no fechamento: adiado para a Slice 7c (aqui o `CloseGuard` é o default
  permissivo — seam rastreável). O caso "período com pendência → 409 com lista" entra na 7c (regressão).
- **SETTLED via Payout**: o estado existe mas a liquidação é do Payout (SPEC-0017), fora de escopo.
- **Multimoeda/conversão**: por decisão (DL-0013) o razão guarda moeda original; relatórios de câmbio
  são da Fase 5 (SPEC-0011).

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=PeriodAndEntryStateMachineTest   # unit
cd backend && ./mvnw verify -Dtest=FinanceIntegrationTest          # integração (Docker up)
cd backend && ./mvnw verify                                        # tudo + portões
```
