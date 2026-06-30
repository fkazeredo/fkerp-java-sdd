# DL-0076 — Catálogo inicial de jobs e ligação dos schedulers existentes à governança

- **Fase:** 8j
- **Spec(s):** SPEC-0023 (Scope/BR2; Open Question: quais jobs no catálogo inicial)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0023 deixa em aberto "quais jobs entram no catálogo inicial (crawler, feed de câmbio, expiração de
PENDING, retenção, SLA) — confirmar conforme as fatias forem ativadas". Precisamos definir o **seed** do
`scheduled_jobs` e **como** os schedulers já existentes passam a registrar `JobRun` sem mudar a lógica de
cada job (que mora no módulo dono — BR6).

## Decisão

1. **Catálogo inicial = os jobs que já existem hoje** (Regra Zero — só o que está ativado):
   | nome | owner_module | o que faz (mora no dono) |
   |---|---|---|
   | `point-clock-crawl` | people | crawler de ponto (SPEC-0012) |
   | `aftersales-sla-sweep` | aftersales | varredura de breach de SLA (SPEC-0018) |
   | `asset-license-expiry` | assets | alerta de licença a vencer (SPEC-0021) |
   | `representation-expiry` | portfolio | alerta de representação a vencer (SPEC-0020) |
   | `retention-expiry` | compliance | retenção a vencer no cofre (SPEC-0008) |
   | `certificate-expiry` | platform | alerta de e-CNPJ a vencer (SPEC-0023, BR5) |
   Seedados via Flyway na V28 (dados de sistema essenciais — `persistence.md` permite seed por Flyway).
   "Feed de câmbio" e "expiração de PENDING" **não** entram porque ainda não há job dedicado ativado
   (booking timeout existe como `@Scheduled` interno; entra quando for governado, sem dívida).
2. **Ligação não-invasiva:** cada `@Scheduled` em `infra.jobs` passa a chamar
   `platformJobService.runWithGovernance(jobName, window, work)` (DL-0075), onde `work` é a chamada que
   ele já fazia (ex.: `afterSalesService.markBreaches(now)`). A **lógica do job continua no módulo dono**;
   o Platform só registra início/fim/status/itens/correlation e aplica idempotência+lock.
3. **`items`** = o retorno contável do job (nº de breaches/flags/snapshots), quando houver, para o
   histórico e a métrica `job_runs_total`.

## Justificativa

- **SPEC-0023 Scope:** "a lógica de cada job mora no módulo dono"; a ligação por template mantém isso e
  só acrescenta governança transversal.
- **Regra Zero:** seedar só os jobs reais evita catálogo especulativo; novos jobs entram quando ativados.
- **Reuso:** os schedulers e as varreduras de relógio controlado já são idempotentes — encaixam no
  template sem reescrever.

## Alternativas descartadas

- **Catálogo especulativo (feed de câmbio/PENDING agora):** criaria entradas sem job real — viola Regra
  Zero e a própria spec ("confirmar conforme ativadas").
- **Mover a lógica dos jobs para o Platform:** violaria BR6 (Platform sem regra de domínio) e o
  ownership de cada módulo.

## Impacto

- **Specs:** SPEC-0023 — Open Question de catálogo vira "ASSUMIDO (ver DL-0076)".
- **Arquivos:** schedulers em `infra.jobs` passam a injetar `PlatformJobService`; seed na V28.
- **Migração:** `INSERT` dos 6 jobs em `scheduled_jobs` (V28).
- **Contratos:** `GET /api/platform/jobs` lista o catálogo seedado.

## Como reverter

Remover a ligação de um scheduler é tirar a chamada ao template (volta a rodar "solto"); remover um job
do catálogo é um `DELETE` de seed. Reversão **Moderada** (toca cada scheduler) e sem perda de lógica de
negócio.
