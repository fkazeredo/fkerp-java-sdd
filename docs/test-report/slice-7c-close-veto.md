# Caderno de testes — Slice 7c · Veto de fechamento ponta a ponta + retenção (SPEC-0008/0015)

## Escopo

Fecha o ciclo da Fase 2: a **regra de ouro do compliance** (lançamento sem documento obrigatório
**não** fecha o mês, e fecha depois de anexado) provada ponta a ponta entre Finance e Compliance, e o
**job de retenção** (`RetentionExpiring`). É a regressão exigida por SPEC-0008 BR6 e SPEC-0015 BR3.

## Casos de teste

### Integração (Testcontainers/Postgres) — `CloseVetoIntegrationTest` (1 caso, regra de ouro)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| entryWithoutDocumentBlocksTheCloseThenClosesOnceAttached | (1) período com `COMMISSION_RECEIVABLE` sem nota → `POST /periods/{m}/close` → 409 `finance.period.cannot-close`, `fields` lista o entryId; (2) período volta a OPEN; (3) anexar `COMMISSION_INVOICE` → o mesmo período fecha (CLOSED) | "sem a nota o mês não fecha (409 com o que falta); com a nota anexada o período fecha" (0015) + "período sem documento → canClose=false" (0008) |

### Integração — `ComplianceIntegrationTest` (caso adicional)
| Caso | Verifica | Regra |
|---|---|---|
| flagsDocumentsApproachingRetention | `flagRetentionExpiring(horizonte)` flagra docs cujo `retentionUntil` está dentro do horizonte; horizonte curto não flagra | SPEC-0008 Events `RetentionExpiring` |

### Job
- `RetentionExpiryScheduler` (`@Scheduled`, idempotente) chama `flagRetentionExpiring`; a regra de
  negócio mora no domínio (`ComplianceService`). Intervalo/horizonte configuráveis.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 108, Failures: 0, Errors: 0,
Skipped: 0`. Log de negócio observado: `Domain error: code=finance.period.cannot-close status=409`.
Portões verdes: ArchUnit, Spring Modulith (9 módulos), Spotless, Checkstyle, completude do
`HttpErrorMapping`.

> Nota de regressão (falha-antes/passa-depois): antes da Slice 7b o `CloseGuard` era o default
> permissivo (`AlwaysAllowsCloseGuard`), então este cenário fechava o período indevidamente; com o
> `ComplianceCloseGuard @Primary` (7b) o veto passou a valer, e este teste tranca a regra.

## Cobertura — o que NÃO está coberto (e por quê)

- **Substituição/versão de documento** e **carimbo do tempo próprio**: Open Questions adiadas.
- **AT_SETTLEMENT** (comprovante de pagamento na liquidação): modelado no seed, exercido quando o
  Payout (SPEC-0017) chegar — o close-check só cobra AT_REGISTRATION (DL-0012).
- **Tela Angular** de Finance/Compliance: não entregue nesta fase (backend completo e testado).

## Como reproduzir

```bash
cd backend && ./mvnw verify -Dtest=CloseVetoIntegrationTest    # regra de ouro (Docker up)
cd backend && ./mvnw verify                                    # tudo + portões (108 testes)
```
