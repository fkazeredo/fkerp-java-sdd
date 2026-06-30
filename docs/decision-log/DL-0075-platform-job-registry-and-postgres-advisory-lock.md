# DL-0075 — Governança de jobs: registro `ScheduledJob`/`JobRun` + idempotência por janela + advisory lock no Postgres

- **Fase:** 8j
- **Spec(s):** SPEC-0023 (BR2, BR3, Persistence; Open Question: ferramenta de locking/scheduler),
  `architecture/messaging-and-integrations.md` (background jobs), `architecture/persistence.md` (locking)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0023 exige idempotência (não rodar duplicado para a mesma janela), **locking** (uma instância por
vez) e `JobRun` com início/fim/status/itens/correlation (BR2); falha registra `FAILED`, nunca vira
sucesso (BR3). A **ferramenta** de locking/scheduler é Open Question (ShedLock, Quartz, lock no Postgres).

## Decisão

1. **Registro próprio, sem framework de scheduler novo.** `ScheduledJob` (catálogo: nome, cron, enabled,
   owner_module, last_run_at) e `JobRun` (id, job_name, started_at, finished_at, status, items,
   failure_class, correlation_id, idempotency_key). O agendamento continua com o `@Scheduled` do Spring
   já em uso (`infra.jobs`) — Regra Zero: **não** adicionar Quartz.
2. **Idempotência por `(job_name, window)`.** A janela é uma string determinística do job (ex.: `YYYY-MM`
   do período, ou a data do dia para varreduras). `job_runs` tem **UNIQUE parcial** sobre
   `(job_name, idempotency_key)` quando a chave não é nula: um segundo start na mesma janela detecta a
   linha existente e é `SKIPPED` (não roda duplicado).
3. **Locking distribuído = advisory lock no Postgres.** Porta `JobLock`; adaptador
   `PostgresAdvisoryJobLock` usa `pg_try_advisory_xact_lock(hashtext(jobName))`: um disparo concorrente
   do mesmo job que não obtém o lock recebe `JobLockedException` (409). Transacional (liberado no fim da
   transação), sem linha extra nem limpeza. **Sem ShedLock** (uma dependência a menos; já temos Postgres).
4. **Template de execução governada.** `runWithGovernance(jobName, window, work)`: abre `JobRun RUNNING`
   (com correlation id), tenta o lock, roda `work`, fecha `SUCCEEDED` com `items`, ou `FAILED` com
   `failure_class` em qualquer exceção (BR3 — o scheduler **não** mascara falha). Disparo manual
   `POST /jobs/{name}/trigger` usa o mesmo template (202).

## Justificativa

- **`persistence.md`:** "pessimistic locking for job claiming"; advisory lock é o mecanismo nativo do
  Postgres para isso, sem tabela de lock nem TTL a gerenciar.
- **`messaging-and-integrations.md`:** important jobs MUST ter idempotência, locking, retry, histórico,
  correlation id — tudo coberto pelo registro + template, reusando o que o projeto já faz (varreduras de
  relógio controlado idempotentes em 8e/8g/8h).
- **Regra Zero:** deploy é **single-instance** (ADR 0002); o advisory lock já cobre o futuro
  multi-instância sem custo. Quartz/ShedLock seriam peso sem problema concreto.

## Alternativas descartadas

- **ShedLock:** resolve o lock distribuído, mas é dependência extra para um caso que `pg_advisory_lock`
  já cobre; o histórico/idempotência o ShedLock **não** dá (precisaríamos do `JobRun` de qualquer forma).
- **Quartz:** scheduler completo com store próprio — sobra de engenharia para `@Scheduled` + alguns jobs.
- **Lock por linha (`SELECT ... FOR UPDATE` numa tabela de lock):** funciona, mas exige a tabela e o
  cuidado de liberar; o advisory transacional é mais simples.

## Impacto

- **Specs:** SPEC-0023 — BR2/BR3 e a Open Question de locking viram "ASSUMIDO (ver DL-0075)".
- **Arquivos:** `domain.platform`: `ScheduledJob`, `JobRun`, `JobStatus`, `JobRunView`,
  `ScheduledJobView`, `PlatformJobService`, `JobLock` (porta), `JobNotFoundException`,
  `JobLockedException`, eventos `JobRunStarted`/`JobRunFinished`. `infra.platform`:
  `PostgresAdvisoryJobLock`.
- **Migração:** `scheduled_jobs`, `job_runs` (índice `ix_job_runs_job_status`; UNIQUE parcial de
  idempotência).
- **Contratos:** `GET /api/platform/jobs`, `GET /jobs/runs`, `POST /jobs/{name}/trigger`.

## Como reverter

Trocar o lock (para ShedLock/Redis) muda só o adaptador `JobLock`; trocar o scheduler (para Quartz) muda
`infra.jobs`. O registro `JobRun`/`ScheduledJob` permanece. Reversão **Moderada** e localizada.
