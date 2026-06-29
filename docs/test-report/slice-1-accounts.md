# Caderno de testes — Slice 1: Accounts (SPEC-0002)

## Escopo

Cadastro/consulta/listagem da Conta Comercial (CNPJ/MEI/CPF) com validação de dígitos (BR2),
unicidade de documento (BR3), status `ACTIVE` ao nascer (BR4) e cadastros opcionais (BR5,
[DL-0007](../decision-log/DL-0007-accounts-cadastros-opcionais.md)). Cobre os Acceptance Criteria
da SPEC-0002 (exceto a tela Angular — ver Cobertura).

## Casos de teste

### Unitário — `Document` (`DocumentTest`)
| Caso | Verifica | Regra |
|---|---|---|
| `acceptsValidCnpjAndNormalizesPunctuation` | CNPJ válido com/sem pontuação → normaliza dígitos | BR2 |
| `acceptsValidCpf` | CPF válido (com pontuação) aceito e normalizado | BR2 |
| `acceptsMeiAsFourteenDigitCnpj` | MEI valida como CNPJ de 14 dígitos | BR1/BR2 |
| `rejectsInvalidCnpj` (4 valores) | DV inválido e dígitos repetidos rejeitados | BR2 |
| `rejectsInvalidCpf` (4 valores) | DV inválido e dígitos repetidos rejeitados | BR2 |
| `rejectsWrongLengthForLegalType` | tamanho incompatível com o tipo rejeitado | BR2 |
| `rejectsNullOrEmpty` | nulo/branco rejeitado | BR2 |

### Unitário/domínio — `Account` (`AccountTest`)
| Caso | Verifica | Regra |
|---|---|---|
| `registerStartsActiveWithGeneratedIdAndAudit` | conta nova nasce `ACTIVE`, com id e auditoria | BR4 |

### Integração (Testcontainers/Postgres) — `AccountIntegrationTest`
| Caso | Verifica | Acceptance Criteria |
|---|---|---|
| `createsValidCnpjAccountAsActive` | POST CNPJ válido → 201 `ACTIVE` | AC1 |
| `rejectsDuplicateDocumentWith409` | documento repetido → 409 `account.document.duplicate` | AC2 (regressão) |
| `rejectsInvalidCheckDigitWith400PointingToDocumentNumber` | DV inválido → 400 `account.document.invalid`, `fields=[documentNumber]` | AC3 |
| `fetchesByIdAndReturns404ForUnknownId` | GET id → 200; id inexistente → 404 `account.not-found` | AC4 |
| `listsFilteringByStatusWithPaginationEnvelope` | lista filtra por status e devolve `PageResponse` | AC5 |
| `rejectsInvalidStatusFilterWith400` | status inválido → 400 `request.parameter-invalid` | robustez |

### Arquitetura
ArchUnit (6 regras) + Spring Modulith `verify()` com o módulo `accounts` (`@ApplicationModule`):
a fronteira é imposta — `internal` (entity/repository) é privado ao módulo; só a fachada/serviço
e os tipos do pacote-base são públicos.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 32, Failures: 0, Errors: 0`
(Fase 0: 12 → +20 da fatia). Spotless **47 files clean**, Checkstyle **0 violations**.

## Cobertura — o que NÃO está coberto e por quê

- **Tela Angular** (formulário + lista com loading/empty/erro) — **pendente**; será entregue na
  leva de frontend da Fase 1. O backend expõe a API completa e está testado ponta a ponta.
- Transições de status (suspender/inativar) — fora de escopo (SPEC-0002 Open Questions).

## Como reproduzir

```bash
cd backend && ./mvnw verify                 # tudo (precisa Docker no ar)
./mvnw test -Dtest=DocumentTest             # só os unitários do value object
./mvnw test -Dtest=AccountIntegrationTest   # só o end-to-end (Testcontainers)
```

## Nota de infraestrutura de teste

`AbstractPostgresIntegrationTest` migrou para o **padrão singleton container** (start em bloco
estático, sem `@Testcontainers`/`@Container`): com duas classes de integração compartilhando o
container e o cache de contexto do Spring, o lifecycle por-classe parava o container e quebrava a
segunda classe ("connection has been closed"). O singleton sobe uma vez por JVM (reaped pelo Ryuk).
