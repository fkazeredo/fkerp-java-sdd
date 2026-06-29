# Plano da Fase 2 — Compliance mínimo (+ seam de Finance)

> Gerado pelo `docs/RUN-PHASE.md` (FASE-ALVO=2), modo autônomo. Convenção: prosa pt-BR, código em
> inglês. Segue o laço do `TUTORIAL.md` (RED → esqueleto → GREEN → refatora → portões → DoD), uma
> fatia por feature branch, `./mvnw verify` verde antes de cada merge `--no-ff` em `develop`.

## Objetivo da fase

Tornar o **documento comprobatório cidadão de primeira classe** e fazer o **lançamento sem documento
obrigatório não fechar o mês**. Entrega as specs:

- **SPEC-0015 (Finance)** — seam mínimo: razão AP/AR (`LedgerEntry`) + máquina de período
  (`AccountingPeriod`) com `closePeriod` que consulta o Compliance e **falha** se houver pendência.
- **SPEC-0008 (Compliance)** — cofre `Document` (hash, retenção), `DocumentRequirement` por tipo de
  lançamento, `DocumentAttachment` (vínculo por valor, sem FK cross-módulo), porta `FileStorage` +
  adaptador filesystem, `close-check` (o veto), bloqueio de expurgo antes da retenção, job de
  retenção vencendo.

O ROADMAP (linha 184-187) determina que o **seam de Finance co-entrega com o Compliance na Fatia 2**.

## Sequência de dependência

```
Slice 7a  Finance (AP/AR + período)           ← independente; closePeriod usa porta CloseGuard (seam)
   └── Slice 7b  Compliance (cofre + close-check) ← implementa CloseGuard; usa LedgerDirectory p/ ler lançamentos
          └── Slice 7c  Veto ponta a ponta + retenção job ← Compliance escuta LedgerEntryRegistered; fechamento realmente veta
```

Justificativa da ordem: Finance é dono da máquina de período e do calendário; ele **consulta** o
Compliance (BR6 da 0015, BR6 da 0008 — "a trava é do Finance; o veto é do Compliance"). Para manter
cada fatia verde e independente, o Finance nasce com a porta `CloseGuard` e um **default permissivo
rastreável** (`AlwaysAllowsCloseGuard`, aponta SPEC-0008) que o Compliance substitui na 7b.

## Fronteiras de módulo (Spring Modulith, detecção explicitly-annotated — DL-0006)

- `com.fksoft.domain.finance` (`@ApplicationModule "Finance"`), base pública + `internal`.
  - API pública: `FinanceService`, `LedgerDirectory` (porta de leitura p/ Compliance), `CloseGuard`
    (porta consumida — implementada pelo Compliance), views, eventos (`LedgerEntryRegistered`,
    `PeriodClosed`), exceções.
- `com.fksoft.domain.compliance` (`@ApplicationModule "Compliance"`), base pública + `internal`.
  - API pública: `ComplianceService`, `ConformanceDirectory`/`close-check`, value objects
    (`DocumentType`, `EntryType`, `RetentionPolicy`), eventos (`DocumentAttached`,
    `RequirementUnmet`, `RetentionExpiring`), exceções. Implementa `finance.CloseGuard` num bean do
    `internal`.
- Sem FK cross-contexto: o vínculo documento↔lançamento é `entry_id + entry_type` (valor); o
  `document_ref` no lançamento é valor. Eventos in-process (`ApplicationEventPublisher` + `@EventListener`).
- Camada `application` (controllers) e `infra` (FileStorage adapter, job, HttpErrorMapping) podem
  depender das APIs públicas dos módulos; nunca do `internal`.

## Decisões (decision-log) — registradas ANTES do código

| DL | Lacuna | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| DL-0012 | Catálogo `entryType × DocumentRequirement` (0008/0015 Open Q.) | Seed inicial cobre a tabela 7.7 (COMMISSION_*, UTILITY_EXPENSE, AUTONOMOUS_SERVICE, SUPPLIER_SETTLEMENT, PENALTY, REFUND); fases AT_REGISTRATION/AT_SETTLEMENT | Média | Barata |
| DL-0013 | Multimoeda no razão (0015 Open Q.) | Guardar em **moeda original** (Money), sem conversão; período agrega por moeda | Média | Moderada |
| DL-0014 | Comprar vs. construir Finance (0015 Open Q.) | **Construir o seam mínimo** agora (AP/AR+período); contabilidade plena = integrar/comprar depois (este módulo vira adaptador) | Alta | Moderada |
| DL-0015 | Abstração de storage do cofre (0008) | Porta `FileStorage` no módulo + adaptador **filesystem** em `infra.integration` (raiz configurável); hash SHA-256 do conteúdo | Alta | Barata |

## Fatias

### Slice 7a — Finance (SPEC-0015)
- **Migração** `V7__create_finance.sql`: `ledger_entries`, `accounting_periods`.
- **Domínio:** `LedgerEntry` (aggregate, máquina PROVISIONAL→CONFIRMED→SETTLED), `AccountingPeriod`
  (OPEN→CLOSING→CLOSED), enums `LedgerDirection`, `EntryStatus`, `PeriodStatus`, `EntryType`. Porta
  `CloseGuard` (consumida) + `AlwaysAllowsCloseGuard` default rastreável. Porta `LedgerDirectory`.
- **App/API:** `POST /api/finance/entries`, `.../{id}/confirm`, `GET /api/finance/entries`,
  `POST /api/finance/periods/{yyyymm}/close`, `GET /api/finance/periods/{yyyymm}`.
- **Eventos:** `LedgerEntryRegistered`, `PeriodClosed`.
- **Erros:** `finance.period.cannot-close` (409), `finance.period.closed` (409),
  `finance.entry.not-found` (404), `finance.period.invalid` (400).
- **Testes:** unit (máquina de período + estados do lançamento), integração (criar lançamento, fechar
  com guard permissivo → CLOSED; lançar em CLOSED → 409).

### Slice 7b — Compliance (SPEC-0008)
- **Migração** `V8__create_compliance.sql`: `documents`, `document_attachments`,
  `document_requirements` (+ seed de requirements e retenção via SQL).
- **Domínio:** `Document` (aggregate, `retentionUntil` calculado por `RetentionPolicy`), value objects
  `DocumentType`, `EntryType`, `SignedFormat`, `RetentionPolicy`; `DocumentAttachment`;
  `DocumentRequirement` (repo); `ComplianceService` (upload, attach, getById, closeCheck, purge).
  Bean `internal` implementa `finance.CloseGuard` consultando `LedgerDirectory`.
- **Porta** `FileStorage` (store/read/delete) + adaptador `FilesystemFileStorage` em
  `infra.integration` (valida tamanho/tipo/extensão/content-type/nome).
- **App/API:** `POST /api/compliance/documents` (multipart), `.../{id}/attach`, `GET .../{id}`,
  `GET .../{id}/content`, `GET /api/compliance/close-check?period=`, `DELETE .../{id}`.
- **Eventos:** `DocumentAttached`, `RequirementUnmet`, `RetentionExpiring`.
- **Erros:** `compliance.document.not-found` (404), `compliance.retention.not-expired` (409),
  `compliance.upload.invalid` (400).
- **Testes:** unit (retentionUntil por type; veto de expurgo; conformidade), integração (upload 201;
  upload inválido 400; attach idempotente; close-check canClose=false com pendência; expurgo no prazo 409).

### Slice 7c — Veto ponta a ponta + retenção
- Compliance escuta `LedgerEntryRegistered` (rastreia o lançamento para conformidade) — o `close-check`
  passa a considerar lançamentos reais do período.
- `closePeriod` do Finance agora **realmente veta** via `CloseGuard` (impl. do Compliance): período
  com lançamento não-conforme → 409 com a lista; com documento anexado → CLOSED + `PeriodClosed`.
- Job `RetentionExpiryScheduler` (`@Scheduled`, idempotente) publica `RetentionExpiring`.
- **Teste de regressão (a regra de ouro):** lançamento sem documento exigido **bloqueia** o
  fechamento (falha antes, passa depois).

## Definition of Done por fatia
ArchUnit + Spring Modulith + Spotless + Checkstyle verdes; `HttpErrorMapping` completo (teste de
completude); migração idempotente; `DomainException.code == chave i18n` (pt-BR + fallback); OpenAPI
atualizada; observabilidade (evento logado, sem vazar conteúdo/caminho, correlation id); caderno de
testes atualizado antes do merge.

## Entrega final
Release `0.3.0` (ADR 0015: Fase 2 = próxima MINOR). Release branch a partir de `develop` → merge em
`main` e `develop` → tag `0.3.0` → push. `docs/ROADMAP-STATUS.md` é do supervisor (não tocar).
