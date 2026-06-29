# 0012 - Crawler de Ponto Eletrônico (Snapshot Operacional + AFD/AEJ Legal)

Status: Approved
Related ADRs: 0010, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Fila/disjuntor/idempotência seguem
> `messaging-and-integrations.md`; o artefato legal vai para o cofre da **SPEC-0008**; credenciais e
> orquestração do job são **Platform (SPEC-0023)**. Base regulatória: Portaria MTP 671/2021 (redesenho 7.8).

## Goal

Integrar o **ponto físico (REP)** por **web crawling**, separando com clareza **dado operacional** de
**artefato legal**: o crawler raspa o portal do fornecedor de ponto e publica um **snapshot
operacional** para o RH (`People`); o **AFD/AEJ assinado** (CAdES `.p7s`), que é o que vale na
fiscalização, é capturado pela **exportação oficial** e guardado no `Compliance` com retenção de 5
anos. O crawler **nunca escreve no miolo** — só publica evento/snapshot (redesenho 7.8).

## Scope

**Em escopo:** `PointClockCrawler` (em `infra/integration`, orquestrado por job do `Platform`) que loga
no portal, captura marcações/espelho e publica `PointSnapshotCollected` / `PointCrawlingFailed` com
**fila + circuit breaker + idempotência**; a ingestão do **AFD/AEJ assinado** (exportação oficial) como
`Document` no `Compliance` (tipos `TIME_RECORD_AFD` / `PROCESSED_JOURNAL_AEJ`, `signedFormat=CAdES_P7S`,
retenção 5 anos); o seam de consumo do snapshot pelo `People`.

**Fora de escopo:** o **tratamento de jornada** (banco de horas, divergências, fechamento de folha) é
**People (SPEC-0022)** — aqui só o snapshot chega; a custódia do certificado/credenciais e o agendador
são **Platform (SPEC-0023)**.

## Business Context

**Só o REP gera o AFD com validade legal**; o programa de tratamento gera apenas espelho e AEJ. Logo,
**raspar a tela** entrega dado operacional (espelho/jornada) — ótimo para o RH ver jornada e faltas no
dia a dia — **mas não o artefato legal**. O AFD/AEJ assinado deve vir da **exportação oficial**. Tratar
o snapshot raspado como se fosse o documento legal seria um erro de compliance. Por isso os **dois
caminhos coexistem** e têm destinos diferentes (operacional → People; legal → Compliance).

## Business Rules

```txt
BR1  O crawler MUST autenticar no portal com credenciais custodiadas pelo Platform (nunca em código/
     log) e capturar marcações/espelho como dado OPERACIONAL.
BR2  Cada coleta bem-sucedida MUST publicar PointSnapshotCollected {snapshotId, sourceRef, periodRef,
     collectedAt}; falha MUST publicar PointCrawlingFailed {sourceRef, failureClass, occurredAt} e
     acionar retry/circuit breaker conforme a classe (TIMEOUT, UNAVAILABLE, AUTHENTICATION_FAILED,
     INVALID_RESPONSE, …).
BR3  O snapshot operacional MUST ser marcado como NÃO-legal (operationalOnly = true): nenhum consumidor
     pode tratá-lo como documento de retenção.
BR4  O AFD/AEJ assinado MUST ser ingerido pela exportação OFICIAL (não pela raspagem) e guardado no
     Compliance preservando o arquivo assinado original (não regerar; signedFormat=CAdES_P7S; retenção
     5 anos). A integridade (hash/assinatura) MUST ser verificada na ingestão.
BR5  A coleta MUST ser idempotente por (sourceRef, periodRef): reexecução não duplica snapshot nem
     documento.
BR6  O crawler MUST NOT escrever em agregados de outros módulos: comunica-se só por evento/snapshot.
BR7  Cada execução do job MUST ter histórico (início, fim, itens, falhas, correlation id) — job
     importante (`messaging-and-integrations.md`).
BR8  ASSUMIDO (ver DL-0029) — Tipo de REP (Q6): mira-se **REP-P (software/nuvem)** e a captura do AFD/AEJ
     assinado é modelada como **upload da exportação OFICIAL** (`POST /api/integration/point/afd`), que
     **também serve REP-C via USB** (o operador extrai e faz upload). O modelo operacional × legal independe
     do tipo; só a origem física do `.p7s` (USB × portal) é passo operacional, fora do código.
BR9  ASSUMIDO (ver DL-0033) — Periodicidade: o job roda **diariamente** e coleta o **período corrente**
     (`periodRef = YYYY-MM`); a **lista de `sourceRef`** (REPs/filiais) é **configurável** (default um). A
     idempotência por `(sourceRef, periodRef)` torna recoletar seguro.
```

## Input/Output Examples

```txt
Job "point-clock-crawl" (agendado pelo Platform)
  -> autentica no portal, coleta período corrente
  -> grava point_snapshots(...) ; publica PointSnapshotCollected
  -> em falha: publica PointCrawlingFailed{class=UNAVAILABLE}; circuit breaker abre após N falhas

Ingestão oficial do AFD (REP-C via USB / REP-P via exportação — depende de Q6)
  POST /api/integration/point/afd        (multipart: arquivo .p7s + metadados)
  201 -> Document{type:TIME_RECORD_AFD, signedFormat:CAdES_P7S, retentionUntil:+5anos} no Compliance
```

```http
GET /api/integration/point/snapshots/{id}
200 OK
{ "id":"s55...", "sourceRef":"REP-FILIAL-SP", "periodRef":"2026-06", "operationalOnly": true,
  "collectedAt":"2026-06-26T03:10:00Z", "marks": 482 }
```

## API Contracts

- `POST /api/integration/point/afd` — ingestão do **AFD/AEJ assinado** (exportação oficial) → cria
  `Document` no Compliance → 201 | 400 `point.afd.invalid` (assinatura/integridade).
- `GET /api/integration/point/snapshots/{id}` → snapshot operacional → 200 | 404.
- `GET /api/integration/point/runs?status=&page=&size=` → histórico de execuções do crawler.
- `POST /api/integration/point/crawl` — disparo manual da coleta (além do agendado). Autorização:
  papel operacional/TI.
- OpenAPI atualizada. (O crawler em si não é um endpoint público — é job + adaptador.)

## Events

- `PointSnapshotCollected` — `{snapshotId, sourceRef, periodRef, collectedAt}`. Produtor:
  `integration`. Consumidor: `people` (jornada — SPEC-0022).
- `PointCrawlingFailed` — `{sourceRef, failureClass, occurredAt}`. Consumidor: `platform`
  (observabilidade/alerta).
- `LegalTimeRecordArchived` — `{documentId, type, periodRef, occurredAt}`. Produtor: `compliance`
  (após ingestão do AFD/AEJ).

## Persistence Changes

> Nota de implementação: a Fase 4/5 já consumiram V12–V15, então esta migração é **`V16`** (não V12).

```txt
V16__create_point_integration.sql
  point_snapshots(
    id uuid PK, source_ref varchar not null, period_ref varchar not null,
    operational_only boolean not null default true,
    payload_ref varchar not null,                 -- espelho/marcações capturados (via FileStorage)
    marks integer not null default 0,             -- nº de marcações no espelho (operacional)
    collected_at timestamptz not null, created_at timestamptz not null,
    UNIQUE (source_ref, period_ref)               -- idempotência (BR5)
  )
  point_crawl_runs(
    id uuid PK, source_ref varchar not null, period_ref varchar null,
    started_at timestamptz not null, finished_at timestamptz null,
    status varchar not null,                       -- RUNNING | SUCCEEDED | RETRY_SCHEDULED | DEAD_LETTER
    attempts integer not null default 1,
    items integer null, failures integer null,
    failure_class varchar null, correlation_id varchar null
  )
-- AFD/AEJ NÃO tem tabela própria: é Document no Compliance (SPEC-0008)
```

O cliente do portal e o parser do AFD vivem em `infra/integration` (ACL); o domínio vê só
eventos/snapshots. O agendamento e as credenciais são do `Platform`.

## Validation Rules

- Integração: timeout/retry/circuit breaker por classe de falha; verificação de assinatura/integridade
  do AFD/AEJ na ingestão (BR4).
- Application: idempotência por `(sourceRef, periodRef)`; histórico de execução.
- Domain/regra: snapshot sempre `operationalOnly=true` (BR3); AFD/AEJ sempre via cofre com retenção.

## Error Behavior

`point.afd.invalid` → 400 (assinatura/integridade); `point.snapshot.not-found` → 404; falha de portal →
classe registrada + alerta (não 200 enganoso). i18n em `messages_pt_BR.properties`. **Nunca** logar
credenciais (`security.md`).

## Observability Requirements

- Logs de integração por execução (sourceRef, classe de falha, latência, itens, correlation id), **sem
  credenciais**. Métricas: `point_crawl_runs_total{status}`, `point_crawl_failures_total{class}`, estado
  do circuit breaker, `legal_time_records_total`.

## Tests Required

- **Unit:** parser/ACL do espelho e do AFD (sem vazar formato externo); verificação de assinatura;
  classificação de falha.
- **Integração (Testcontainers + portal fake):** coleta publica `PointSnapshotCollected` (idempotente);
  falha publica `PointCrawlingFailed` e abre o breaker; ingestão de AFD válido cria Document com retenção
  5 anos; AFD inválido → 400.
- **Regressão:** snapshot raspado **nunca** é tratado como documento legal (`operationalOnly` impede)
  — falha antes, passa depois.

## Acceptance Criteria

- O job coleta o espelho e publica o snapshot operacional para o People, com histórico de execução.
- Falhas de portal abrem o circuit breaker e alertam, sem produzir snapshot falso.
- O AFD/AEJ assinado é guardado no cofre com `signedFormat=CAdES_P7S` e retenção de 5 anos.
- `./mvnw verify` verde (ArchUnit confirma que o crawler não escreve em outro módulo).

## Open Questions

> Resolvidas em modo autônomo na Fase 6 (movidas para *Business Rules* — BR8/BR9). Reabrir só com decisão do
> negócio (RH/cliente). As de **Confiança=Baixa** estão destacadas no `docs/decision-log/INDEX.md`.

- ~~**Q6 — tipo de REP (C/A/P)**~~ → **ASSUMIDO (ver [DL-0029](../decision-log/DL-0029-rep-type-target-rep-p-export-upload.md))**,
  BR8. Mira-se REP-P; AFD/AEJ via upload da exportação oficial (serve REP-C via USB). **Confiança=Baixa,
  Reversibilidade=Cara** — qual REP o cliente usa é incógnita de negócio; **confirmar com o cliente**.
- ~~Periodicidade do crawl e janela de coleta~~ → **ASSUMIDO (ver [DL-0033](../decision-log/DL-0033-crawl-period-and-multi-source.md))**,
  BR9. Diário, período corrente; **confirmar com o RH**.
- ~~Múltiplos REPs/filiais (vários `sourceRef`)~~ → **ASSUMIDO (ver [DL-0033](../decision-log/DL-0033-crawl-period-and-multi-source.md))**,
  BR9. Lista de `sourceRef` configurável (default um); **confirmar a lista real**.

## Out of Scope

Tratamento de jornada/banco de horas/folha (SPEC-0022), custódia de certificado/credenciais e
agendador (SPEC-0023).
