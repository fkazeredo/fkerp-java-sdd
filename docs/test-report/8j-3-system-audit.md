# Caderno de testes — Fatia 8j-3 (Auditoria de sistema)

## Escopo

SPEC-0023, BR4 (auditoria append-only consolidando segurança/integração/jobs com
timestamp/ator/correlation) e BR1 (sem material sigiloso). DL-0077 (listener in-process + fachada
`record`). Acceptance Criteria: "a auditoria de sistema consolida eventos de segurança/integração/jobs".

## Casos de teste

### Integração (Testcontainers/Postgres) — `SystemAuditIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `aGovernedJobRunIsConsolidatedIntoTheAudit` | run governado gera `JOB_RUN_STARTED` + `JOB_RUN_FINISHED` (detalhe com status) | BR4 |
| `custodyingACertificateIsAuditedWithoutLeakingTheMaterial` | import de certificado gera `CERTIFICATE_CUSTODIED` com ator + CNPJ mascarado; **segredo nunca na auditoria** (regressão) | BR4/BR1 |
| `theAuditEntryIsAppendOnly` | a entidade `SystemAuditEntry` **não** expõe mutador/setter/update — trilha imutável | BR4 |
| `theAuditIsFilterableByActorAndType` | filtro por ator + tipo retorna só o esperado | API |

### Portões
- `ModularityTests` verde (módulo `platform`, grafo acíclico — o listener consome só eventos
  expostos do próprio Platform); `ArchitectureTest` 15 regras verdes (Platform sem regra de domínio).
  `HttpErrorMappingCompletenessTest` verde (sem exceção nova nesta fatia).

## Resultado

`./mvnw verify` → **BUILD SUCCESS** (suíte completa verde; Spotless 0, Checkstyle 0). A query de
auditoria usa `JpaSpecificationExecutor` (critérios tipados) para evitar o problema de parâmetro NULL
sem tipo do Postgres em filtros opcionais.

## Cobertura / não coberto

- A fachada `SystemAuditService.record(type, actor, detail)` é o ponto de entrada para eventos de
  **segurança/integração** de outros módulos; hoje o Platform consolida os próprios eventos (jobs,
  certificado). Quando a auth real (SPEC-0024) emitir eventos de acesso, eles entram pela mesma fachada
  sem mudar o contrato.

## Como reproduzir

```
cd backend && ./mvnw -o test -Dtest=SystemAuditIntegrationTest   # auditoria/append-only/segurança
cd backend && ./mvnw verify                                      # tudo + portões
```
