# Caderno de testes — Slice 7b · Compliance (SPEC-0008)

## Escopo

Cofre de documentos: `Document` (hash SHA-256 + `retentionUntil` calculado na ingestão),
`DocumentAttachment` (vínculo por valor), `DocumentRequirement` (seed DL-0012), porta `FileStorage` +
adaptador filesystem (DL-0015), `close-check` (o veto consumido pelo Finance via `CloseGuard
@Primary`), e bloqueio de expurgo antes da retenção. Cobre os Acceptance Criteria da SPEC-0008.

Decisões aplicadas: DL-0012 (catálogo de requisitos), DL-0015 (FileStorage + SHA-256).

## Casos de teste

### Unitário / domínio — `RetentionPolicyTest` (5 casos)
| Caso | Verifica | Regra |
|---|---|---|
| fiscalDocumentRetainsFiveYears | NFSE/COMMISSION_INVOICE → +5 anos | BR2 |
| contractRetainsTenYears | REPRESENTATION_CONTRACT → +10 anos | BR2 |
| documentComputesRetentionUntilOnIngestion | `retentionUntil` fixado na ingestão | BR2 |
| purgeIsRejectedWithinRetention | expurgo antes do prazo → exceção | BR7 |
| purgeIsAllowedAfterRetention | expurgo no/após o prazo permitido | BR7 |

### Integração (Testcontainers/Postgres) — `ComplianceIntegrationTest` (6 casos)
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| uploadsADocumentAndComputesRetention | `POST /documents` (multipart) → 201, hash `sha256:`, retenção +5 anos | "anexar NFS-e define retentionUntil = +5 anos" |
| rejectsAnInvalidUpload | upload vazio → 400 `compliance.upload.invalid` | validação de upload |
| attachIsIdempotent | dois attach do mesmo doc→entry → 1 vínculo | BR5 |
| closeCheckReturnsCannotCloseWithPendingEntries | período com lançamento sem doc → `canClose=false` + lista | "canClose=false e a lista do que falta" |
| attachingTheRequiredDocumentClearsThePending | anexar COMMISSION_INVOICE → `canClose=true` | BR5/BR6 (fluxo completo do cofre) |
| purgeIsRejectedWithinRetention | `DELETE /documents/{id}` no prazo → 409 `compliance.retention.not-expired` | "expurgar fiscal nos 5 anos → 409" |

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 106, Failures: 0, Errors: 0,
Skipped: 0` (+11 do Compliance). Portões verdes: ArchUnit, Spring Modulith (9 módulos), Spotless,
Checkstyle, completude do `HttpErrorMapping`. O `CloseGuard` real (`@Primary`) já é o consultado pelo
Finance — o veto funciona ponta a ponta (provado por `attachingTheRequiredDocumentClearsThePending`).

## Cobertura — o que NÃO está coberto (e por quê)

- **Regressão Finance-level do veto** (`closePeriod` → 409 com lista, falha-antes/passa-depois) e o
  **job de retenção** + listener de `LedgerEntryRegistered`: na Slice 7c.
- **Versão/substituição de documento** e **carimbo do tempo próprio**: Open Questions adiadas (spec).
- **Adaptador S3/Azure**: a porta existe; só o filesystem está implementado (DL-0015).

## Como reproduzir

```bash
cd backend && ./mvnw -q spotless:apply
cd backend && ./mvnw test -Dtest=RetentionPolicyTest               # unit
cd backend && ./mvnw verify -Dtest=ComplianceIntegrationTest        # integração (Docker up)
cd backend && ./mvnw verify                                         # tudo + portões
```
