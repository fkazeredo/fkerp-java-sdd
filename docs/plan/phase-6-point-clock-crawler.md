# Plano — Fase 6: Crawler de ponto (SPEC-0012)

> Modo autônomo (RUN-PHASE, FASE-ALVO=6). Integra o **ponto físico (REP)** separando **dado operacional**
> (espelho → snapshot para o RH/`People`) de **artefato legal** (AFD/AEJ assinado → cofre da `Compliance`
> com retenção de 5 anos). Primeira **resiliência de saída** do projeto: fila + disjuntor + idempotência.
> O crawler **nunca escreve no miolo** — só publica evento/snapshot (redesenho 7.8).

## Objetivo

- **Operacional:** um job agendado raspa o portal do fornecedor de ponto, coleta o espelho/marcações do
  período corrente e publica um **snapshot operacional** (`operationalOnly=true`) para o `People`, com
  **histórico de execução** e **idempotência** por `(sourceRef, periodRef)`.
- **Legal:** o **AFD/AEJ assinado** (CAdES `.p7s`) entra pela **exportação oficial** (upload), tem
  **assinatura/integridade verificadas** e é guardado no **cofre da Compliance** (reuso de SPEC-0008:
  `FileStorage` + `RetentionPolicy` + `Document`, `signedFormat=CAdES_P7S`, retenção 5 anos).
- **Resiliência real e testada:** disjuntor que abre após N falhas, fila/retry com **dead-letter**,
  classificação de falha, tudo com **relógio controlado** e **mock injetor de falhas** da fonte do REP.
- **Fronteiras:** o formato externo do portal **não cruza** para o domínio (regra ArchUnit, como a do
  `quotationsite`); o crawler **não escreve** em módulos do miolo (teste de fronteira).

## Decisões registradas antes do código (decision-log)

| DL | Lacuna (Open Question) | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| DL-0029 | **Q6 — tipo de REP (C/A/P)** | Mirar **REP-P**; AFD/AEJ via **upload da exportação oficial** (serve REP-C via USB). Operacional × legal independe do tipo. | **Baixa** | **Cara** |
| DL-0030 | Quem é dono do snapshot/histórico; qual módulo nasce | Novo módulo **`people`** (11º Modulith) dono do snapshot/idempotência/histórico; crawler técnico em `infra/integration`; **sem** módulo `platform` vazio (agendador em `infra/jobs`). | Alta | Moderada |
| DL-0031 | Mecanismo de fila/disjuntor/retry | **In-process determinístico** (sem resilience4j/broker): `PointClockCircuitBreaker` (CLOSED/OPEN/HALF_OPEN sobre `Clock`), fila/retry com **dead-letter**, classes de falha; fonte do REP atrás de porta + mock injetor. | Alta | Moderada |
| DL-0032 | Profundidade da verificação de assinatura do AFD | Verificar **envelope CAdES/PKCS#7 + hash do conteúdo** na ingestão; `point.afd.invalid` → 400 no adulterado. ICP-Brasil completo fica p/ Platform (SPEC-0023). | Média | Moderada |
| DL-0033 | Periodicidade do crawl e lista de REPs | **Diário**, período corrente `YYYY-MM`; lista de `sourceRef` **configurável** (default um). | **Baixa** | Barata |

## Fatias (ordem de dependência)

### Slice 11a — Módulo `people` + snapshot operacional + idempotência  ·  `feature/slice-11a-people-snapshot`
- **Entrega:** módulo `com.fksoft.domain.people` (`@ApplicationModule`); agregado `PointSnapshot
  {sourceRef, periodRef, operationalOnly=true, payloadRef, marks, collectedAt}`; `PointSnapshotService`:
  - `collect(command)` **idempotente** por `(sourceRef, periodRef)` (UNIQUE + pré-checagem → re-run
    atualiza, não duplica; BR5); marca sempre `operationalOnly=true` (BR3); publica `PointSnapshotCollected`.
  - `getById(id)` → `PointSnapshotView`.
  - `recordRunStarted/Succeeded/Failed` → histórico `PointCrawlRun` (BR7), com `attempts`/`status`/
    `failureClass`/`correlationId`; `runs(status,page,size)` paginado.
  - `recordFailure(sourceRef, failureClass)` → publica `PointCrawlingFailed` (BR2).
- **API:** `GET /api/integration/point/snapshots/{id}` (200/404 `point.snapshot.not-found`);
  `GET /api/integration/point/runs?status=&page=&size=` (histórico).
- **Migração:** `V16__create_point_integration.sql` (`point_snapshots`, `point_crawl_runs`).
- **Erros/i18n:** `point.snapshot.not-found` (404) em pt-BR + fallback EN; registrar exceção no `HttpErrorMapping`.
- **Testes:** unit (idempotência por chave; `operationalOnly` sempre true; máquina de status do run);
  integração (coleta publica `PointSnapshotCollected`; re-coleta não duplica; GET snapshot 200/404; runs).
- **Regressão (Tests Required):** snapshot raspado **nunca** é documento legal — `operationalOnly` impede;
  teste falha antes (sem o flag), passa depois.

### Slice 11b — Crawler ACL + fila + disjuntor + histórico + agendador  ·  `feature/slice-11b-crawler-resilience`
- **Entrega:** `com.fksoft.infra.integration.pointclock`:
  - porta de saída `PointClockSource.fetchMirror(sourceRef, periodRef)` (timeout) devolvendo a forma
    **externa** `PortalMirror` (DTO do portal, **fica em infra**); mock `FaultInjectingPointClockSource`
    (teste) injeta falhas por classe.
  - `PointFailureClass` (TIMEOUT, UNAVAILABLE, AUTHENTICATION_FAILED, INVALID_RESPONSE, UNKNOWN_ERROR).
  - `PointClockCircuitBreaker` (CLOSED → OPEN após N falhas; cooldown sobre `Clock`; HALF_OPEN).
  - `PointMirrorTranslator` (ACL): traduz `PortalMirror` → comando de domínio (`CollectSnapshotCommand`);
    o DTO externo **não cruza** para o domínio.
  - `PointClockCrawler.crawl(sourceRef)`: registra run, chama a fonte via breaker, em sucesso chama
    `PointSnapshotService.collect` (publica snapshot) e marca run SUCCEEDED; em falha **classifica**,
    agenda retry (classes retetáveis) ou vai a **DEAD_LETTER** após `maxAttempts`, publica
    `PointCrawlingFailed` — **nunca** snapshot falso.
  - `PointClockCrawlScheduler` (`infra/jobs`): agenda diário; itera `point-clock.sources`; disparo manual
    `POST /api/integration/point/crawl` (papel operacional/TI).
- **Config:** `point-clock.sources`, `point-clock.crawl.*`, breaker (`failure-threshold`, `cooldown-ms`,
  `max-attempts`).
- **Observabilidade:** log por execução (sourceRef, classe, latência, itens, correlation id) **sem
  credenciais**; transições do breaker logadas.
- **Fronteira (ArchUnit):** o DTO `infra.integration.pointclock` (`PortalMirror`) **não** é referenciado
  pelo domínio (regra nova, espelha a do `quotationsite`). Teste de fronteira: o crawler **não escreve** em
  agregados do miolo (só chama a fachada do `people` e publica eventos).
- **Testes (Testcontainers + fonte fake):** coleta publica `PointSnapshotCollected` (idempotente);
  N falhas **abrem o breaker** (próxima chamada recusada sem bater na fonte); item esgota retries →
  **DEAD_LETTER** + `PointCrawlingFailed`, sem snapshot; `AUTHENTICATION_FAILED` não retenta; histórico de
  execução registrado; relógio controlado.

### Slice 11c — Ingestão do AFD/AEJ assinado → cofre da Compliance  ·  `feature/slice-11c-afd-legal-ingestion`
- **Entrega:** porta `AfdSignatureVerifier` + adaptador `Pkcs7AfdSignatureVerifier` (verifica envelope
  CAdES/PKCS#7 + presença de assinatura + hash do conteúdo == `expectedContentHash`); `PointAfdInvalidException`
  (`point.afd.invalid` → 400). Caso de uso de ingestão chama `ComplianceService.upload(TIME_RECORD_AFD |
  PROCESSED_JOURNAL_AEJ, bytes, filename, contentType, issuedAt, CAdES_P7S, hasPersonalData=true, ...)` →
  `Document` no cofre com `retentionUntil = +5 anos`; publica `LegalTimeRecordArchived {documentId, type,
  periodRef, occurredAt}`.
- **API:** `POST /api/integration/point/afd` (multipart: `file` `.p7s`, `type`, `issuedAt`, `periodRef`,
  `expectedContentHash`) → 201 `Document` | 400 `point.afd.invalid`.
- **Migração:** nenhuma (AFD é `Document` no cofre; sem tabela própria — SPEC-0012 Persistence).
- **Testes:** unit (verificador: válido aceita; envelope quebrado / hash divergente rejeita);
  integração (AFD válido → `Document` com `signedFormat=CAdES_P7S` e retenção 5 anos no cofre; AFD inválido
  → 400, nada guardado); evento `LegalTimeRecordArchived` publicado.
- **Compliance/LGPD:** AFD carrega CPF/PIS (dado pessoal) → `hasPersonalData=true` (acesso auditado pelo
  cofre); nunca logar credenciais nem conteúdo (security.md).

## Definition of Done (por fatia e da fase)

- `cd backend && ./mvnw spotless:apply && ./mvnw verify` **verde** (ArchUnit + Spring Modulith[11 módulos]
  + Spotless + Checkstyle) — **nenhum gate afrouxado**; Docker no ar (Testcontainers).
- Migração `V16` aplicada e validada (Postgres real). i18n pt-BR + fallback. OpenAPI atualizada (endpoints
  novos). `HttpErrorMapping` cobre as exceções novas (teste de completude verde).
- Spring Modulith `verify()` verde — **sem ciclo** (people é leaf; crawler em infra → domain é acíclico).
- Caderno de testes em `docs/test-report/` por fatia + INDEX; cada Acceptance Criteria vira teste.
- Merge `--no-ff` em `develop` por fatia verde; push. Ao fim: release `0.7.0` (ADR 0015 — próximo MINOR).

## Riscos / pontos de atenção

- **Q6 (DL-0029) é Confiança=Baixa/Reversibilidade=Cara:** o formato real do `.p7s` do REP do cliente pode
  diferir; o verificador é uma porta para fortalecer depois. Destacado no INDEX do decision-log.
- **Ciclo Modulith:** garantir que `people` não dependa de `compliance`/`integration` (é leaf); a ingestão
  do AFD orquestra-se em `infra` (chama `compliance` + publica evento), não no `people`.
- **Sem snapshot falso:** fallback nunca produz resultado de negócio enganoso (falha → DEAD_LETTER + evento,
  não snapshot).
