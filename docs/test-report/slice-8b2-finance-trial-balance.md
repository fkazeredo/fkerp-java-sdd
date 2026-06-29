# Caderno de testes — Slice 8b-2 · Finance: balancete do período por moeda/status (SPEC-0015 full)

## Escopo

Relatório de período enriquecido (**BR10**, **DL-0043**): novo endpoint **`GET
/api/finance/periods/{yyyymm}/trial-balance`** que devolve o **balancete operacional** **por moeda**
(DL-0013 — nunca soma moedas) — `payable`, `receivable` e `net = receivable − payable` (saldo
**operacional**, não resultado contábil; DL-0042) — mais as **contagens por status**
(PROVISIONAL/CONFIRMED/SETTLED). Leitura pura sobre `ledger_entries` (sem migração; escala 2 HALF_UP).
**Aditivo:** não altera o `GET /periods/{yyyymm}` existente. Sem plano de contas / partidas dobradas
(fora de escopo, DL-0042).

## Casos de teste

### Integração (Testcontainers/Postgres) — `FinanceTrialBalanceIntegrationTest` (2 casos)
| Caso | Verifica | Regra |
|---|---|---|
| trialBalanceAggregatesPerCurrencyWithNetAndStatusCounts | BRL: 2700 AR + 1000 AP → net **1700,00**; USD: 500 AP → net **−500,00**; moedas **separadas** (nunca somadas); 3 PROVISIONAL / 0 CONFIRMED / 0 SETTLED | BR10, DL-0043, DL-0013 |
| trialBalanceOfAnUntouchedPeriodIsEmpty | período nunca referenciado → balancete **vazio**, contagens zero (nunca 404) | BR10 |

### Regressão — contratos do seam intactos
| Caso | Verifica | Regra |
|---|---|---|
| `FinanceIntegrationTest` (6) | `GET /periods/{yyyymm}` (totais AP/AR por moeda) **inalterado**; create/confirm/list/close/closed/malformed seguem | SPEC-0015 BR1-BR4 (aditividade do novo endpoint) |
| `FinanceEventPostingIntegrationTest` (4), `CloseVetoIntegrationTest` (1) | lançamento automático e a regra de ouro continuam verdes | SPEC-0015 BR3/BR5 |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 245, Failures: 0, Errors: 0, Skipped: 0`
(+2 da fatia, 243 → 245). Portões verdes: ArchUnit (10), Spring Modulith (acíclico), Spotless (407
arquivos limpos), Checkstyle (0 violações).

## Cobertura — o que NÃO está coberto e por quê

- **Balancete de período CLOSED com lançamentos confirmados/liquidados:** o agregador conta por status
  genericamente; o caso PROVISIONAL/contagens está coberto; CONFIRMED/SETTLED seguem o mesmo caminho
  (mesmo `switch`), sem ramo distinto a testar isoladamente (Rule Zero).
- **Plano de contas / partidas dobradas / DRE / SPED:** fora de escopo por decisão (DL-0042) — é
  livro-caixa, não Razão contábil.

## Como reproduzir

```bash
cd backend
./mvnw -q -Dtest=FinanceTrialBalanceIntegrationTest test     # os 2 casos da fatia
./mvnw verify                                                # tudo + portões (Docker no ar)
```
