# 0002 - Accounts (Conta Comercial)

Status: Approved
Related ADRs: 0012, 0014

## Goal

Cadastrar e consultar a **Conta Comercial** (agência ou agente — o cliente direto da Acme Travel),
que é a porta de entrada de toda operação comercial. Toda cotação, reserva e repasse pendura numa
Account.

## Scope

**Em escopo:** criar, consultar por id e listar/filtrar Accounts; validar tipo legal e dígitos do
documento; impor unicidade de documento; status com valor padrão `ACTIVE`. Tela Angular mínima:
formulário de criação + lista com filtro por status.

**Fora de escopo:** carteira/limite de crédito; validação externa de CADASTUR/IATA junto a
registros oficiais; transições de status (suspensão/reativação); merge/deduplicação.

## Business Context

Cadeia `Fornecedor → Acme Travel → Agência/Agente → Viajante`. O cliente direto é a agência
(raramente CPF). A Account guarda a **identidade comercial e legal** do parceiro — **não calcula
dinheiro** (redesenho, Parte 6).

## Business Rules

```txt
BR1  Uma Account MUST ter legalType ∈ {CNPJ, MEI, CPF}.
BR2  documentNumber MUST ser estruturalmente válido p/ o legalType (CNPJ e MEI: 14 dígitos + DV;
     CPF: 11 dígitos + DV). Inválido => 400 account.document.invalid.
BR3  (legalType, documentNumber) MUST ser único. Duplicado => 409 account.document.duplicate.
BR4  Uma Account MUST ter displayName não-vazio e status ∈ {ACTIVE, SUSPENDED, INACTIVE};
     conta nova nasce ACTIVE.
BR5  cadastur e iata são opcionais e guardados como vieram (NÃO validados contra registro externo).
BR6  Account MUST NOT conter cálculo monetário.
BR7  Consulta por id inexistente => 404 account.not-found.
```

## Input/Output Examples

```http
POST /api/accounts
{ "legalType": "CNPJ", "documentNumber": "12345678000195",
  "displayName": "Agência Sol e Mar", "cadastur": "26.123456.10.0001-9" }
201 Created
{ "id": "8f1c...", "legalType": "CNPJ", "documentNumber": "12345678000195",
  "displayName": "Agência Sol e Mar", "status": "ACTIVE", "cadastur": "26.123456.10.0001-9",
  "iata": null, "createdAt": "2026-06-26T12:00:00Z" }
```

```http
POST /api/accounts  (documento já existente)
409 Conflict
{ "code": "account.document.duplicate", "message": "Documento já cadastrado.", "fields": [] }

POST /api/accounts  (CNPJ com DV inválido)
400 Bad Request
{ "code": "account.document.invalid", "message": "Documento inválido para o tipo informado.", "fields": ["documentNumber"] }
```

## API Contracts

- `POST /api/accounts` — body `{legalType, documentNumber, displayName, cadastur?, iata?}` →
  201 com o recurso. Autorização: usuário autenticado (regras finas adiadas com Identity).
- `GET /api/accounts/{id}` → 200 | 404 `account.not-found`.
- `GET /api/accounts?status=&document=&page=&size=` → 200 `PageResponse`. Sort default por
  `createdAt desc`; max page size definido (ex. 100); resultado vazio retorna página vazia (não 404).
- OpenAPI atualizada. Enums com valores externos explícitos.

## Events

- `AccountRegistered` — fato: uma conta foi cadastrada. Payload `{accountId, legalType, occurredAt}`.
  Produtor: `accounts`. Consumidores: nenhum ainda (futuro: Marketing, Intelligence). Evento
  **interno in-process** (Spring event); vira contrato estável/outbox quando outro módulo/serviço
  consumir (`messaging-and-integrations.md`).

## Persistence Changes

```txt
V2__create_accounts.sql
  accounts(
    id uuid PK,
    legal_type varchar not null,
    document_number varchar not null,
    display_name varchar not null,
    status varchar not null,
    cadastur varchar null,
    iata varchar null,
    created_at, updated_at timestamptz not null,
    created_by, updated_by varchar null,
    version bigint not null
  )
  UNIQUE INDEX ux_accounts_document (legal_type, document_number)
```

`@Version` para optimistic locking. `Document` (legalType + number) modelado como **value object**
que protege a invariante de dígitos (BR2).

## Validation Rules

- Delivery: Bean Validation no request (campos obrigatórios, tamanho).
- Domain: o value object `Document` valida os dígitos (BR2) — invariante, não depende do controller.
- Persistence: índice único (BR3) traduzido para `account.document.duplicate` (nunca exceção crua
  de banco vaza — `persistence.md`).

## Error Behavior

- `account.document.invalid` → 400; `account.document.duplicate` → 409; `account.not-found` → 404.
- Chaves i18n em `messages_pt_BR.properties` (e fallback `messages.properties`).

## Observability Requirements

- Logar `AccountRegistered` como evento de negócio — **sem o número completo do documento**
  (mascarar; CPF é dado pessoal, LGPD — `security.md`).
- Correlation id propagado. Métrica opcional `accounts_registered_total`.

## Tests Required

- **Unit:** `Document` valida DV de CNPJ e CPF (tabela de válidos/ inválidos, incluindo dígitos
  repetidos como inválidos).
- **Unit/domain:** invariantes de criação da Account.
- **Integração (Testcontainers):** POST 201; documento inválido 400; duplicado 409; GET 200/404;
  listagem com paginação e filtro por status.
- **Regressão:** detecção de duplicado (falha antes, passa depois) — ver TUTORIAL.

## Acceptance Criteria

- Criar uma agência CNPJ válida retorna 201 com status `ACTIVE`.
- Repetir o mesmo documento retorna 409 `account.document.duplicate`.
- CNPJ/CPF com DV inválido retorna 400 `account.document.invalid` apontando `documentNumber`.
- `GET /api/accounts/{id}` inexistente retorna 404 `account.not-found`.
- A tela cria e lista contas, com estados loading/empty/erro.
- `mvnw verify` verde (ArchUnit/Modulith inclusos).

## Open Questions

- ~~Quais cadastros (CADASTUR, IATA) são **obrigatórios** por tipo de conta?~~ → **ASSUMIDO**
  (2026-06-29): nenhum cadastro externo é obrigatório no v1; `cadastur`/`iata` são opcionais e
  guardados como vieram (BR5). Ver [DL-0007](../decision-log/DL-0007-accounts-cadastros-opcionais.md).
- Modelagem de **carteira** (saldo/limite) — adiada para spec própria.
- **Transições de status** (suspender/reativar/inativar) — adiadas até existir um workflow real que
  as exija; não inventar máquina de estados agora (`CLAUDE.md`, invariante 3).

## Out of Scope

Carteira, crédito, validação externa de CADASTUR/IATA, transições de status, deduplicação/merge.
