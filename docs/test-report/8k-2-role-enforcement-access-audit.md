# Caderno de testes — Slice 8k-2 (Identity: enforcement por papel + auditoria de acesso)

## Escopo

- **Spec:** SPEC-0024 (BR2 — autorização por papel; ações sensíveis exigem o papel; negação → 403 +
  auditoria; BR3 — login/negação auditados; BR4 — sem segredo no audit; BR10/BR11).
- **DLs:** DL-0082 (modelo papel→permissão + mapa das ações sensíveis; enforcement HTTP + reafirma a
  checagem de domínio DL-0038), DL-0083 (auditoria de acesso reusa o `system_audit` do Platform).
- **Acceptance Criteria cobertos:** "ações sensíveis exigem o papel correspondente e ficam auditadas".
- **Tests Required (regressão de fronteira):** "emitir NF passa a exigir o papel certo — falha antes
  (stub deixava passar), passa depois".

## Casos de teste

### Integração (Testcontainers + Postgres real) — `AccessControlIntegrationTest`
| Caso | O que verifica | Regra |
|---|---|---|
| `issuingAnInvoiceWithoutTheFinanceRoleIsForbiddenAndAudited` | `POST /api/billing/invoices/{id}/issue` com token `director` (sem ROLE_FINANCE) → **403 `access.denied`** + 1 linha `ACCESS_DENIED` no `system_audit` (actor=director, detalhe com `DENY` e o path) | **regressão de fronteira** (BR2/BR3) |
| `issuingAnInvoiceWithTheFinanceRolePassesTheSecurityGate` | mesmo endpoint com token `finance` → passa o portão (404 `billing.invoice.not-found` no id falso, provando que chegou ao controller) | BR2 (positivo) |
| `triggeringAJobRequiresTheItRole` | `POST /api/platform/jobs/*/trigger`: `director`→403, `it`→202 | BR2 (crawler/job → TI) |
| `issuingADirectiveRequiresTheDirectorRole` | `POST /api/commercial-policy/directives` com `finance` → 403 `access.denied` | BR2 (DIRECTIVE → diretor) |
| `accessAuditAndRolesEndpointsAreThemselvesProtected` | `GET /access-audit` com `viewer` → 403; `GET /roles` com `it` → 200 (catálogo com papéis + permissões) | BR2 (audit/roles protegidos) |
| `loginIsRecordedInTheAccessAudit` | login → 1 linha `AUTH_LOGIN` (actor=finance); detalhe **não contém a senha** | BR3/BR4 |

> O **enforcement HTTP** (Spring Security `hasRole(...)`) e os handlers 401/403 auditados foram
> entregues na 8k-1 (mesmo `SecurityConfig`); a 8k-2 **prova** a fronteira por testes e a auditoria.
> A checagem de **domínio** da diretiva (DL-0038) permanece e agora lê os papéis do **token real**.

## Resultado

```
./mvnw -o -Dtest=AccessControlIntegrationTest test → Tests run: 6, Failures: 0, Errors: 0
./mvnw verify (suíte completa) → BUILD SUCCESS — 444 tests
ArchitectureTest: 15 regras · Spring Modulith: 21 módulos acíclicos
Checkstyle: 0 violations · Spotless: OK
```

## Cobertura

- **Coberto:** 403 + auditoria das ações sensíveis citadas na spec (NF, job, diretiva), positivo com o
  papel, proteção dos endpoints de identidade, login auditado sem segredo.
- **Não coberto (intencional):** mapeamento de **toda** ação sensível futura (a spec manda consolidar à
  medida que as fatias expõem ações — `role_permissions` é dado governável, DL-0082); tela Angular →
  8k-3.

## Como reproduzir

```bash
cd backend && ./mvnw -o -Dtest=AccessControlIntegrationTest test   # Docker no ar
cd backend && ./mvnw verify
```
