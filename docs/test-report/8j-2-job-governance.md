# Caderno de testes — Fatia 8j-2 (Governança de jobs)

## Escopo

SPEC-0023, BR2 (idempotência por janela + locking de uma instância) e BR3 (falha registrada, nunca
mascarada). Acceptance Criteria: "o crawler/feed/expirações rodam pelo scheduler com histórico e sem
duplicar execução"; Tests Required: "dois disparos concorrentes → um roda, outro vê locked; job falho
registra JobRun FAILED (não vira sucesso)". DLs: DL-0075 (registro + advisory lock), DL-0076 (catálogo +
ligação dos schedulers).

## Casos de teste

### Integração (Testcontainers/Postgres) — `JobGovernanceIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `aSuccessfulRunIsRecordedSucceededWithItsItemCount` | run com sucesso → SUCCEEDED com `items` | BR2 |
| `aSecondRunForTheSameWindowIsSkippedIdempotently` | 2ª run na mesma janela → SKIPPED; trabalho não roda 2× | BR2 |
| `aFailingJobIsRecordedFailedNotSuccess` | trabalho lança → JobRun FAILED (sobrevive ao rollback do trabalho), 0 SUCCEEDED, `failure_class=UNAVAILABLE` | BR3 |
| `twoConcurrentRunsOfTheSameJobOneRunsTheOtherSeesLocked` | um detém o advisory lock (bloqueado em latch); o concorrente recebe `JobLockedException` | BR2 |
| `theRunHistoryIsFilterableByJobAndStatus` | histórico filtra por job e por status | API |

### Integração de API (REST) — `PlatformJobApiIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `theSeededCatalogIsListed` | `GET /api/platform/jobs` lista os 6 jobs seedados (V28) | DL-0076 |
| `triggeringAKnownJobRunsItUnderGovernanceAndReturns202` | `POST /jobs/{name}/trigger` → 202 + run no histórico | API/BR2 |
| `triggeringAnUnknownJobIs404` | job inexistente → 404 `platform.job.not-found` | Error Behavior |

### Arquitetura / portões
- `ModularityTests` verde com o módulo `platform`; `HttpErrorMappingCompletenessTest` cobre
  `JobNotFound`(404)/`JobLocked`(409). `ArchitectureTest` (15 regras) verde — Platform sem regra de
  domínio (BR6).

## Resultado

`./mvnw verify` → **Tests run: 430, Failures: 0, Errors: 0 — BUILD SUCCESS**; Spotless 0, Checkstyle 0.
Os 5 schedulers existentes (SLA, licença, retenção, certificado, crawler) + o novo de representação
passam a rodar via `GovernedJobs.runWithGovernance` sem quebrar nenhum teste prévio.

## Cobertura / não coberto

- O advisory lock é **session-level** (`pg_try_advisory_lock`/`pg_advisory_unlock`) numa única conexão,
  cobrindo o caso multi-instância (deploy é single-instance hoje, ADR 0002). Métricas Prometheus
  (`job_runs_total`) ficam para a Fase 11 (SPEC-0027); aqui o histórico/contagem vive no `job_runs`.

## Como reproduzir

```
cd backend && ./mvnw -o test -Dtest=JobGovernanceIntegrationTest      # idempotência/lock/falha
cd backend && ./mvnw -o test -Dtest=PlatformJobApiIntegrationTest     # catálogo/trigger/404
cd backend && ./mvnw verify                                           # tudo + portões
```
