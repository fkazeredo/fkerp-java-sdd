# DL-0056 — Marketing: Consent é histórico append-only; estado atual derivado da última linha por (titular, finalidade)

- **Fase:** 8f (Marketing — SPEC-0019)
- **Spec(s):** SPEC-0019 (BR1 "Revogação é **append** (history preservado)"; API
  `GET /api/marketing/consents?subject=&purpose=` → "estado atual + histórico"; Persistence:
  `consents(... created_at ...)` com comentário "revogação = nova linha (append/history)").
- **ADR relacionado:** persistence.md (sem editar histórico; índices), security.md (LGPD: trilha,
  controle de acesso), DL-0024 (fatos distintos não se compensam — mesma filosofia event-sourcing-like).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A spec manda "revogação é append; history preservado", mas não fixa **como** modelar o estado atual:
(a) uma tabela mutável `status` que é sobrescrita, ou (b) **um log append-only** de eventos de
consentimento (`GRANTED`/`REVOKED`) onde o estado vigente é a **última linha** por `(subject, purpose)`.

## Decisão

1. **Tabela `consents` append-only** (sem `UPDATE` de status): cada decisão do titular é **uma nova
   linha** imutável com `id`, `subject_id`, `subject_type`, `purpose`, `legal_basis`, `status`
   (`GRANTED`/`REVOKED`), `source`, `created_at`, `created_by`. **Não** há coluna mutável de status
   "atual"; conceder de novo após revogar é **outra** linha `GRANTED`.
2. **Estado atual = a linha mais recente** por `(subject_id, subject_type, purpose)` ordenada por
   `created_at` (desempate por `id`). Há um value/view `ConsentState` que projeta
   `{subject, purpose, currentStatus, lastChangedAt}`; o `GET ...?subject=&purpose=` devolve **o
   estado atual + a lista histórica** (todas as linhas).
3. **O filtro de envio (BR2)** pergunta ao agregado/serviço "este titular tem `GRANTED` vigente para
   esta finalidade?" — resolvido pela última linha, **não** por varredura de strings.
4. **Revogação** (`DELETE /consents/{id}` semântica de revogar a finalidade do titular, ou
   `POST` de revogação) **insere** uma linha `REVOKED` referenciando o mesmo `(subject, purpose)`;
   publica `ConsentRevoked {subjectRef, purpose, occurredAt}`. Nunca apaga linhas (a não ser pelo
   expurgo LGPD — DL-0058).
5. **Índice** `ix_consents_subject_purpose (subject_type, subject_id, purpose, created_at DESC)`
   para resolver o estado atual em uma leitura barata.

## Justificativa

- "Append; history preservado" é literalmente um **log imutável**; sobrescrever status perderia a
  trilha que a LGPD valoriza (quem consentiu, quando, por qual origem, quando revogou) — security.md
  pede trilha para dado pessoal.
- Derivar o estado da última linha é o padrão **event-log + projection** já usado no projeto
  (Insight/posição cambial/conciliação leem o estado de fatos acumulados); evita a clássica
  corrida "li status, sobrescrevi" e dá auditabilidade grátis.
- É barato: uma tabela, um índice, uma query "última por chave" — sem jsonb, sem tabela de history
  separada (Rule Zero).

## Alternativas descartadas

- **Tabela mutável com `UPDATE status`.** Descartada: viola "append/history preservado" e perde a
  trilha de revogações/reconsentimentos.
- **Duas tabelas (`consents` corrente + `consents_history`).** Descartada: duplicação sem ganho — o
  log único já é a fonte e a projeção; manter duas sincronizadas é mais código e mais risco.
- **Event sourcing pleno (event store + snapshots).** Descartada: overengineering para um domínio
  de poucas transições por titular (Rule Zero).

## Impacto

- **Specs:** SPEC-0019 BR1 concretizada como "ASSUMIDO (ver DL-0056): append-only + estado por última
  linha".
- **Arquivos:** agregado/entidade `Consent` (linha imutável), `ConsentRepository` (última por chave),
  `ConsentState`/`ConsentView`, `ConsentService.grant/revoke/currentState/history`. Evento
  `ConsentRevoked`.
- **Migração:** `consents` (V24) sem coluna de status mutável; índice por `(subject, purpose,
  created_at DESC)`.
- **Contratos:** `GET /api/marketing/consents` devolve `{ current: [...], history: [...] }`.

## Como reverter

Moderada: passar a um modelo mutável exigiria colapsar o log na linha corrente (com uma migração de
projeção) — possível, mas perderia a trilha; não há motivo previsto para reverter. Adicionar
`PENDING` (double opt-in, DL-0055) é aditivo sobre este log.
