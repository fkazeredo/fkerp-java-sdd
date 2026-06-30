# DL-0077 — Auditoria de sistema append-only, consolidada por listener de eventos in-process

- **Fase:** 8j
- **Spec(s):** SPEC-0023 (BR4, Events, Observability), `architecture/observability.md` (audit/security logs)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

BR4 manda consolidar eventos de **segurança/integração/jobs** com timestamp/ator/correlation id,
append-only. Faltava decidir **como** consolidar sem acoplar o Platform aos módulos produtores e **sem**
vazar segredo.

## Decisão

1. **Tabela `system_audit` append-only** (`id, occurred_at, actor, type, detail_json jsonb,
   correlation_id`). `SystemAuditEntry` é entidade **write-once**: sem método mutador, sem update/delete
   (ArchUnit/teste garantem); a única operação é inserir.
2. **Consolidação por listener in-process.** Um listener no Platform consome os eventos já existentes do
   próprio Platform (`JobRunStarted`/`JobRunFinished`, `CertificateExpiring`) e grava uma linha de
   auditoria. Para eventos de **segurança/integração** de fora, o Platform expõe a fachada
   `SystemAuditService.record(type, actor, detail, correlationId)` — o ponto de entrada único para
   qualquer produtor (ex.: futuras falhas de assinatura, acessos à custódia) registrar sem acoplar
   tabela. O Platform **não** importa fachadas de comando de outros módulos (BR6) — só reage a eventos
   expostos.
3. **Correlation id** vem do MDC (`CorrelationIdFilter` já existente) quando presente.
4. **Sem material sigiloso (BR1/security.md):** `detail_json` carrega só metadados (job, status,
   failure_class, runId, fingerprint, daysToExpiry); CNPJ/ator são mascarados em log; **nunca** material
   de certificado/senha. Teste de regressão de segurança garante que segredo não aparece na auditoria.

## Justificativa

- **`observability.md`:** distingue logs de **audit** e **security**; a auditoria de sistema é o registro
  durável desses fatos (o log estruturado é o volátil). Append-only é a propriedade que dá fé ao registro.
- **Listener in-process** segue o padrão já validado (Finance/Intelligence/Marketing consomem eventos
  expostos sem chamar fachadas) — mantém o grafo Modulith acíclico (o Platform é consumidor-folha).
- **Fachada `record(...)`** dá o ponto de entrada para segurança/integração sem o Platform conhecer cada
  produtor — extensível quando a auth real (SPEC-0024) emitir eventos de acesso.

## Alternativas descartadas

- **Platform chamar cada módulo para puxar auditoria:** acoplaria e violaria BR6/Modulith.
- **Auditar só via log estruturado (sem tabela):** perde a propriedade append-only consultável que a
  spec exige (`GET /api/platform/audit`).
- **`detail_json` com payload bruto do evento:** risco de carregar PII/segredo; consolidamos só
  metadados mascarados.

## Impacto

- **Specs:** SPEC-0023 — BR4 vira "ASSUMIDO (ver DL-0077)".
- **Arquivos:** `domain.platform`: `SystemAuditEntry`, `SystemAuditService`, `SystemAuditView`,
  `AuditType`, listener de eventos. Controller `GET /api/platform/audit`.
- **Migração:** `system_audit` (V28) com índice por `(type, occurred_at)` e por `actor`.
- **Contratos:** `GET /api/platform/audit?actor=&type=&from=&to=&page=&size=`.

## Como reverter

Mudar o destino da auditoria (para um sink externo/SIEM) é trocar a implementação do
`SystemAuditService`/listener; o contrato de leitura permanece. Reversão **Moderada**.
