# 0021 - Assets (Patrimônio Interno)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. **Supporting/Generic** (redesenho linha 134/162): entrega
> **enxuta e real** — registro de patrimônio interno. Distinto de `Portfolio` (representação comercial,
> SPEC-0020).

## Goal

Registrar o **patrimônio interno** da Acme — equipamentos, licenças de software, bens — com ciclo de
vida básico e os vínculos que o resto do sistema precisa (custo no Finance, contrato/nota no Compliance),
sem virar um sistema de gestão de ativos completo (redesenho linha 162).

## Scope

**Em escopo:** o agregado `Asset` (tipo EQUIPMENT | SOFTWARE_LICENSE | OTHER; identificação;
status ACTIVE/RETIRED; aquisição: data, custo, fornecedor; vencimento para licenças); referência ao
**documento** de aquisição/contrato no Compliance e ao **lançamento** de custo no Finance; alerta de
**licença a vencer**.

**Fora de escopo:** depreciação contábil, gestão de manutenção/chamados de TI, controle de estoque de
revenda (a Acme não revende patrimônio); se o cliente exigir gestão de ativos plena, **comprar**.

## Business Context

Bens e licenças têm **custo** (Finance) e **documento** (Compliance) — uma licença vencendo é risco
operacional/contratual. Um registro simples basta para amarrar custo↔documento e alertar vencimento;
não há regra de negócio rica aqui (por isso a entrega é enxuta e marca claramente o que é decisão do dono).

## Business Rules

```txt
BR1  Asset MUST ter type, identifier, status, acquisitionDate e acquisitionCost (Money); SOFTWARE_LICENSE
     MUST ter expiresAt.
BR2  Aquisição MUST poder referenciar o documento (nota/contrato) no Compliance e o lançamento de custo
     no Finance (ids como valor, sem FK).
BR3  Licença com expiresAt próximo MUST publicar AssetLicenseExpiring (alerta — não bloqueia).
BR4  Baixa (RETIRED) MUST ser auditada (quem, quando, motivo).
BR5  Assets MUST NOT precificar venda nem participar do fluxo comercial (é patrimônio, não produto).
```

### Decisões assumidas (modo autônomo — Fase 8h)

- **BR6 — ASSUMIDO (ver DL-0064):** Assets é um **contexto separado** de Portfolio (Q2: dois
  contextos, não um), entregue como **registro enxuto** de patrimônio (18º módulo Spring Modulith).
- **BR7 — ASSUMIDO (ver DL-0065):** **sem depreciação contábil nem gestão plena de ativos** no v1
  (Out of Scope); o módulo é o **registro + seam** ("comprar vs. construir") se o dono exigir gestão
  plena.
- **BR8 — ASSUMIDO (ver DL-0066):** o alerta de licença a vencer (BR3) roda por **job de relógio
  controlado** (`flagExpiringLicenses(now)`), antecedência **30 dias** (`assets.license.horizon-days`),
  **idempotente** por `expiry_signaled_at`. A listagem `?expiringWithinDays=N` é uma leitura ad-hoc
  com o `N` do request (default 30).
- **BR9 — ASSUMIDO (ver DL-0067):** Assets é **módulo-folha**: apenas **publica** `AssetRegistered` e
  `AssetLicenseExpiring` in-process; **não** fia consumidores em Finance/Intelligence nesta fatia
  (lançar custo automático de patrimônio é regra de negócio inexistente na spec). O vínculo ao Finance
  é por id (valor, `financeEntryId`), para um lançamento já existente.
- **BR10 — ASSUMIDO (ver DL-0068):** a baixa (BR4) é **auditada inline** no agregado
  (`retired_at`/`retired_by`/`retirement_reason`); a transição **ACTIVE→RETIRED é terminal** (sem
  reativação no v1); re-baixar lança `assets.asset.already-retired` (409).

## Input/Output Examples

```http
POST /api/assets
{ "type":"SOFTWARE_LICENSE", "identifier":"JetBrains All Products Pack",
  "acquisitionDate":"2026-01-10", "acquisitionCost":{"amount":"3200.00","currency":"BRL"},
  "expiresAt":"2027-01-10" }
201 Created  { "id":"as1...", "status":"ACTIVE" }

GET /api/assets?expiringWithinDays=30
200 OK  { "items":[ {"id":"as1...","identifier":"JetBrains...","expiresAt":"2027-01-10"} ] }
```

## API Contracts

- `POST /api/assets` / `GET .../assets/{id}` / `GET .../assets?type=&status=&expiringWithinDays=` → CRUD + lista.
- `POST /api/assets/{id}/retire` — baixa (motivo) → 200.
- OpenAPI atualizada.

## Events

- `AssetRegistered` — `{assetId, type, occurredAt}`. Produtor: `assets`. Consumidor: `finance` (custo),
  `intelligence` (custo fixo/infra).
- `AssetLicenseExpiring` — `{assetId, expiresAt, occurredAt}` (alerta). Consumidor: governança/TI.

## Persistence Changes

```txt
V21__create_assets.sql
  assets(
    id uuid PK, type varchar not null, identifier varchar not null, status varchar not null,
    acquisition_date date not null, acquisition_cost numeric(18,2) not null, currency varchar not null,
    expires_at date null, supplier_ref varchar null,
    document_id uuid null, finance_entry_id uuid null,        -- valores p/ Compliance/Finance
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null
  )
```

Alerta de vencimento por **job** (idempotência/locking). Documento/lançamento por id (valor).

## Validation Rules

- Application: `expiresAt` obrigatório para SOFTWARE_LICENSE; existência do ativo nas ações.
- Domain: estados ACTIVE/RETIRED; alerta de vencimento (BR3).

## Error Behavior

`assets.asset.not-found` → 404; `assets.license.expiry-required` → 400 (licença sem `expiresAt`). i18n em
`messages_pt_BR.properties`.

## Observability Requirements

- Logar registro/baixa/vencimento como evento de negócio (assetId, correlation id). Métricas:
  `assets_active_total{type}`, `assets_license_expiring_total`.

## Tests Required

- **Unit/domain:** obrigatoriedade de `expiresAt` em licença; transição para RETIRED.
- **Integração (Testcontainers):** registrar ativo vincula documento/lançamento (ids); job de vencimento
  publica `AssetLicenseExpiring`; listagem por `expiringWithinDays`.
- **Regressão:** Assets não entra no fluxo comercial (falha antes, passa depois).

## Acceptance Criteria

- Registrar uma licença com vencimento e custo, vinculada a documento e lançamento.
- Licença a vencer em 30 dias aparece na listagem e gera alerta.
- `./mvnw verify` verde.

## Open Questions

> **Resolvidas em modo autônomo na Fase 8h** (movidas para *Business Rules* acima):
> - ~~**Q2 — `Portfolio` + `Assets`: os dois ou um?**~~ → **dois contextos distintos** (ASSUMIDO, ver
>   DL-0064; recomendação do arquiteto no ROADMAP).
> - ~~Necessidade de **depreciação/gestão plena de ativos**~~ → **não** no v1: registro + seam
>   comprar-vs-construir (ASSUMIDO, ver DL-0065). Se o dono exigir gestão plena, **comprar**.

_Nenhuma Open Question de negócio permanece em aberto para esta fatia._

## Out of Scope

Depreciação contábil, manutenção/chamados de TI, estoque de revenda, gestão de ativos plena (comprar).
