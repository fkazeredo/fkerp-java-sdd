# DL-0083 — Identity: auditoria de acesso reusa o `system_audit` do Platform (sem tabela nova); `GET /api/identity/access-audit` é uma leitura focada

- **Fase:** 8k (Identity)
- **Spec(s):** SPEC-0024 (BR3 — login/ação sensível/negação auditados; Events `UserAuthenticated`/
  `AccessDenied`; API `GET /api/identity/access-audit`; Persistence previa `access_audit`)
- **ADR relacionado:** DL-0077 (auditoria de sistema append-only do Platform); `architecture/security.md`
  (não logar segredos; mascarar PII); `architecture/observability.md`
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0024 previa uma tabela `access_audit` própria e o endpoint `GET /api/identity/access-audit`. Mas
a Fase 8j (Platform) já entregou um **`system_audit` append-only** com `AuditType.SECURITY_EVENT`,
fachada `SystemAuditService.record(...)`, correlation id automático e leitura filtrável. Decidir:
**criar `access_audit`** (como a spec rascunhou) **ou reusar** o seam do Platform?

## Decisão

1. **Reusar o `system_audit` do Platform** (Regra Zero — não duplicar um seam append-only que já
   existe). Os fatos de acesso são gravados via `SystemAuditService.record(type, actor, detailJson)`:
   - **login bem-sucedido** → `AuditType.AUTH_LOGIN` (novo valor do enum), `actor=username`;
   - **negação de acesso** (403) → `AuditType.ACCESS_DENIED`, com `action`/`resource` no `detailJson`;
   - **ação sensível permitida** (ex.: emitir NF, diretiva) → já coberta pelos eventos/log de negócio
     do módulo dono; o registro de **acesso** foca em login e negação (o que a spec chama de
     auditoria de acesso), evitando duplicar a auditoria de negócio.
   - Dois novos valores entram no enum `AuditType`: `AUTH_LOGIN`, `ACCESS_DENIED` (e reusa-se
     `SECURITY_EVENT` para o genérico). **Nunca** o token/senha/hash entra no `detailJson` (BR4,
     security.md); o `username` é o ator; dados pessoais são mascarados.
2. **`GET /api/identity/access-audit?actor=&action=&from=&to=&page=&size=`** é uma **leitura focada**
   que consulta o mesmo `SystemAuditService.search(...)` filtrando pelos tipos de acesso
   (`AUTH_LOGIN`/`ACCESS_DENIED`/`SECURITY_EVENT`). O contrato do endpoint da spec é honrado; a fonte é
   o seam consolidado. Autorização: papel TI/diretor (`identity:audit:read`, DL-0082).
3. **Eventos de domínio** `UserAuthenticated`/`AccessDenied` (in-process) são publicados pelo módulo
   `identity` para quem quiser consumir; a **gravação** no audit é feita pela fachada (não cria FK
   cross-contexto — o `identity` chama a fachada pública do `platform`, dependência de comando
   permitida pois `platform` é consumer-leaf e `identity` não é importado por ele → grafo acíclico).

## Justificativa

- **Regra Zero:** `system_audit` já é o "registro append-only de eventos de segurança/integração/job,
  metadados only, com correlation id" — exatamente o que a auditoria de acesso precisa. Criar
  `access_audit` seria uma segunda tabela com a mesma função.
- **BR3/observability:** ator, ação, recurso, quando e correlation id já são campos do `system_audit`
  (`actor`, `detail_json`, `occurred_at`, `correlation_id`); o tipo discrimina o fato.
- **BR4/security.md:** a fachada já garante "metadados only, sem segredo"; herdamos a regressão de
  segredo-nunca-no-audit do 8j.
- **Confiança=Alta:** o seam é estável e já testado (8j).

## Alternativas descartadas

- **Tabela `access_audit` dedicada (rascunho da spec).** Descartada: duplica o `system_audit`; mais
  schema, mais código, mesma função — Regra Zero.
- **Logar acesso só em arquivo de log (sem persistir).** Descartada: BR3 pede auditoria consultável
  (`GET /access-audit`); log volátil não atende.
- **`identity` grava direto na tabela do Platform (FK/JPA cross-contexto).** Descartada: violaria a
  fronteira de módulo; a gravação passa pela **fachada** `SystemAuditService` (comando público), id por
  valor, sem FK.

## Impacto

- **Enum `AuditType`** ganha `AUTH_LOGIN`, `ACCESS_DENIED` (aditivo; `SECURITY_EVENT` já existe).
- **Sem migração nova de tabela de auditoria** (V29 cria só identity; o `system_audit` é o do V28).
- **Arquivos:** `IdentityController.accessAudit(...)` delega ao `SystemAuditService.search`; um
  `AccessAuditRecorder` (em `identity` ou `infra.security`) chama `record(...)` no login e na negação;
  `AccessDeniedHandler`/`AuthenticationEntryPoint` do Spring Security disparam o registro do 403/401.
- **Spec SPEC-0024:** a Persistence muda de "`access_audit` nova" para "reusa `system_audit`
  (V28/Platform)"; documentado como ASSUMIDO.

## Como reverter

Reversão **moderada**: se o dono exigir uma trilha de acesso fisicamente separada (ex.: retenção/ípsilon
distintos por compliance), cria-se `access_audit` e troca-se a fonte do `record`/`search` de acesso —
sem mexer nos chamadores (passam pela fachada). Custo: uma migração + um repositório.
