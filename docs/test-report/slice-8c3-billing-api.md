# Caderno de testes — Slice 8c-3 · Billing: API REST + cancelamento (SPEC-0016)

## Escopo

API REST do Billing (SPEC-0016 API Contracts / Error Behavior): `BillingController` com
**`POST /api/billing/invoices`** (cria rascunho → 201), **`POST /{id}/issue`** (emite via orquestrador
→ 200 EMITIDA com número/código/ISS/documentId), **`POST /{id}/cancel`** (cancela → 200 CANCELADA),
**`GET /{id}`** (→ 200 | 404). DTOs `CreateCommissionInvoiceRequest`/`CancelCommissionInvoiceRequest`
com Bean Validation. Erros mapeados no `HttpErrorMapping` (not-found 404, already-issued 409,
municipality.rejected 422, nfse.webservice-failure 502). OpenAPI atualizada (descrição da Fase 8c +
versão 0.11.0). i18n já adicionado nas fatias anteriores.

## Casos de teste

### Integração API (Testcontainers + TestRestTemplate) — `BillingApiIntegrationTest` (4 casos)
| Caso | Verifica | Regra |
|---|---|---|
| createIssueGetAndCancelJourney | jornada completa: criar (201 RASCUNHO, base=405) → emitir (200 EMITIDA, número, ISS 20,25, documentId) → consultar (200) → cancelar (200 CANCELADA) | API Contracts, BR3/BR6 |
| reissuingWithoutCancellingIs409 | reemitir nota já EMITIDA → no-op idempotente (200, mesma nota); **uma** EMITIDA por comissão (UNIQUE parcial) | BR4 |
| unknownInvoiceIs404 | `GET` de id inexistente → 404 `billing.invoice.not-found` | Error Behavior |
| municipalityRejectionIs422 | emitir com município `REJECT` → 422 `billing.municipality.rejected` | BR7, Error Behavior |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 265, Failures: 0, Errors: 0, Skipped: 0`
(+4 da fatia, 261 → 265). Portões verdes: ArchUnit (11), Spring Modulith acíclico, Spotless,
Checkstyle. `HttpErrorMappingCompletenessTest` verde (todas as exceções de Billing mapeadas). OpenAPI
gerada (code-first) inclui os endpoints `/api/billing/*`.

## Cobertura — o que NÃO está coberto e por quê

- **Listagem/paginação de notas:** a spec não pede um `GET /invoices` paginado (Rule Zero); só
  create/issue/cancel/get. Pode entrar quando uma tela de Billing existir (front fora de escopo desta
  fase).
- **Frontend Angular:** SPEC-0016 é backend; a tela de Billing é da iniciativa UX (SPEC-0026), fora do
  escopo desta sub-fase.
- **502 ponta a ponta pela API:** o mapeamento `BillingNfseWebserviceException → 502` é exercido na
  unidade do mapping + integração da fatia 8c-2 (TIMEOUT). A API cobre 201/200/404/409/422.

## Como reproduzir

```bash
cd backend && ./mvnw spotless:apply
cd backend && ./mvnw -Dtest='BillingApiIntegrationTest' test
cd backend && ./mvnw verify   # build completo + portões (Docker no ar)
```
