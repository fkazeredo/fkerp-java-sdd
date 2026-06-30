# 0020 - Portfolio (Representação: Marcas, Contratos e Metas)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Distinto de `Assets` (patrimônio interno, SPEC-0021) — aqui é
> **o que a Acme Travel representa** comercialmente (redesenho linha 133/161). Os contratos de
> representação são documentos no **Compliance** (retenção enquanto vigentes + 5–10 anos).

## Goal

Modelar o **portfólio de representação** da Acme (uma GSA/representante): as **marcas/produtos** que ela
vende em nome de fornecedores, os **contratos de representação** que dão esse direito (com vigência e
condições) e as **metas por marca** — a base para escopo de comissão, alavancagem com fornecedor e
acompanhamento de meta (redesenho linha 161, 322).

## Scope

**Em escopo:** o agregado `RepresentedBrand` (marca/fornecedor representado, status ACTIVE/INACTIVE); o
`RepresentationContract` (marca, vigência, condições comerciais de referência, documento no Compliance);
`BrandGoal` (meta por marca/período: volume ou receita) e o acompanhamento **realizado vs meta** a partir
de eventos de venda; expõe a marca/produto representado como referência para Quoting/Commissioning/DSS.

**Fora de escopo:** **preços** (não moram aqui — Parte 6); o cálculo de comissão (Commissioning); o
patrimônio interno (Assets); a negociação contratual em si (workflow humano/Admin).

## Business Context

A Acme não é dona do catálogo, mas **representa marcas** e tem **contrato** que define o que pode vender
e em que condições. Saber a vigência do contrato e a meta por marca alimenta decisões: *fornecedor com
alto cancelamento → renegociar/substituir* (8.2-E) e *estamos perto da meta da marca?* (acompanhamento).
Sem este contexto, "qual marca" fica implícito e o DSS não consegue agrupar por representação.

## Business Rules

```txt
BR1  RepresentedBrand MUST ter brandRef (identidade da marca/fornecedor), displayName e status.
BR2  RepresentationContract MUST ter brandRef, validFrom/validUntil e referência ao documento de
     contrato no Compliance (REPRESENTATION_CONTRACT). Vender marca sem contrato vigente é uma
     **exceção sinalizável** (alerta — não bloqueia v1). ASSUMIDO (ver DL-0061): v1 **alerta**, não
     bloqueia; a cobertura de contrato vigente é exposta como leitura (contractCoverage) para quem
     compõe a venda sinalizar — o Portfolio não veta.
BR3  BrandGoal MUST ter brandRef, period (YYYY-MM ou YYYY), metric ∈ {VOLUME, REVENUE} e target.
BR4  O realizado por marca MUST ser projetado a partir de eventos de venda (BookingConfirmed/
     SpreadRealized) filtrados pela marca representada — read-model, sem alterar a venda. ASSUMIDO
     (ver DL-0062): o casamento venda→marca é um **intake próprio do Portfolio** (BrandSaleAttribution,
     `booking_id` UNIQUE) — o evento da venda NÃO é alterado; BookingConfirmed projeta VOLUME e
     SpreadRealized projeta REVENUE (BRL), idempotentes. Qual campo identifica a marca na venda fica
     como seam rastreável (confirmar com o dono).
BR5  Mudança de status/contrato MUST ser auditada; expiração de contrato MUST publicar
     RepresentationExpiring (alerta de governança). ASSUMIDO (ver DL-0063): a expiração é sinalizada
     por um **job de relógio controlado** (flagExpiringContracts(now)), antecedência 30 dias,
     idempotente (sinaliza uma vez por contrato) — alerta, não bloqueio.
BR6  Portfolio MUST NOT precificar nem calcular comissão — apenas referenciar a marca/contrato e
     **somar** o que os eventos de venda já trazem.
```

## Input/Output Examples

```http
POST /api/portfolio/brands
{ "brandRef":"ALAMO", "displayName":"Alamo Rent a Car" }
201 Created  { "id":"br1...", "status":"ACTIVE" }

POST /api/portfolio/brands/{id}/goals
{ "period":"2026", "metric":"REVENUE", "target":{"amount":"1200000.00","currency":"BRL"} }
201 Created

GET /api/portfolio/brands/{id}/goals/2026/progress
200 OK  { "metric":"REVENUE", "target":{"amount":"1200000.00","currency":"BRL"},
          "realized":{"amount":"480000.00","currency":"BRL"}, "attainmentPct":"40.0" }
```

## API Contracts

- `POST /api/portfolio/brands` / `GET .../brands/{id}` / `GET .../brands?status=` → CRUD + lista.
- `POST /api/portfolio/brands/{brandRef}/contracts` — registra contrato (vincula documento no Compliance) → 201.
- `POST /api/portfolio/brands/{brandRef}/goals` / `GET .../brands/{id}/goals/{period}/progress` → meta + realizado.
- `POST /api/portfolio/brands/{brandRef}/sales` — intake venda→marca (corpo `{bookingId}`), idempotente (DL-0062) → 201.
- `GET /api/portfolio/brands/{id}/contract-coverage?on=YYYY-MM-DD` — cobertura de contrato vigente (DL-0061, alerta).
- `POST /api/portfolio/contracts/flag-expiring` — dispara o job de alerta de expiração (DL-0063, relógio controlado).
- OpenAPI atualizada.

## Events

- `BrandRepresented` / `RepresentationContractRegistered` — `{brandRef, occurredAt}`. Produtor:
  `portfolio`. Consumidor: `intelligence` (alavancagem com fornecedor), `commercial-policy` (escopo).
- `RepresentationExpiring` — `{brandRef, validUntil, occurredAt}` (alerta). Consumidor: governança/DSS.

## Persistence Changes

```txt
V25__create_portfolio.sql   -- (a spec citava V20; a migração real é V25, próxima livre — ver DL-0060)
  represented_brands( id uuid PK, brand_ref varchar not null UNIQUE, display_name varchar not null,
                      status varchar not null, created_at, updated_at timestamptz not null, version bigint not null )
  representation_contracts( id uuid PK, brand_ref varchar not null, valid_from date not null,
                            valid_until date null, document_id uuid null,           -- valor p/ Compliance
                            terms_json jsonb null, expiring_signaled_at timestamptz null,  -- DL-0063 (idempotência do alerta)
                            created_at, updated_at timestamptz not null, version bigint not null )
  brand_goals( id uuid PK, brand_ref varchar not null, period varchar not null,
               metric varchar not null, target_amount numeric(18,2) null, target_count int null,
               UNIQUE (brand_ref, period, metric) )
  brand_sale_attributions( id uuid PK, booking_id uuid not null UNIQUE, brand_ref varchar not null,
                           attributed_at timestamptz not null )   -- intake venda→marca (DL-0062), valor
  brand_realized( id uuid PK, brand_ref varchar not null, metric varchar not null,   -- projeção idempotente
                  source_ref varchar not null, amount numeric(18,2) null, count_inc int null,
                  occurred_at timestamptz not null,
                  UNIQUE (metric, source_ref) )                   -- idempotência do evento de venda
```

O **realizado vs meta** é **read-model/projeção** sobre eventos de venda (sem FK para Booking; filtra
pela marca representada como valor, via o intake DL-0062). Contrato referencia o documento no
Compliance por id (valor). A projeção `brand_realized` é idempotente por `(metric, source_ref)` —
re-entrega do mesmo `BookingConfirmed`/`SpreadRealized` não soma duas vezes.

## Validation Rules

- Application: unicidade de `brandRef`; vigências coerentes; meta por (marca, período, métrica) única.
- Domain: estados de marca/contrato; projeção de realizado (BR4).
- Princípio: nada de preço/comissão aqui (BR6).

## Error Behavior

`portfolio.brand.not-found` → 404; `portfolio.brand.duplicate` → 409; `portfolio.goal.invalid` → 400.
i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar representação/contrato/expiração como evento de negócio (brandRef, correlation id). Métricas:
  `represented_brands_active`, `representation_expiring_total`, atingimento de meta por marca.

## Tests Required

- **Unit/domain:** estados de marca/contrato; projeção de realizado vs meta (VOLUME e REVENUE).
- **Integração (Testcontainers):** registrar marca/contrato (vincula documento); `BookingConfirmed` de
  uma marca incrementa o realizado da meta; expiração de contrato publica `RepresentationExpiring`.
- **Regressão:** Portfolio não altera nem precifica venda (falha antes, passa depois).

## Acceptance Criteria

- Registrar a marca e o contrato (com documento no cofre) e definir meta anual de receita.
- O progresso da meta reflete as vendas confirmadas da marca.
- Contrato a vencer gera alerta de governança.
- `./mvnw verify` verde.

## Open Questions

> Todas as Open Questions que afetam o código desta fatia foram resolvidas em modo autônomo e movidas
> para *Business Rules* marcadas "ASSUMIDO (ver DL-NNNN)". Permanecem como **confirmações de negócio**
> (o valor adotado é defensável e reversível conforme cada DL):

- **Q2 — `Portfolio` + `Assets`: os dois ou um?** RESOLVIDA — ASSUMIDO (ver **DL-0060**): **dois
  contextos distintos** (recomendação do ROADMAP). Se o dono unificar "inventário", esta spec e a
  SPEC-0021 se fundem (reversível na fatia da SPEC-0021).
- Vender marca **sem contrato vigente**: alertar apenas (v1) ou **bloquear**? RESOLVIDA — ASSUMIDO
  (ver **DL-0061**): **alerta** no v1 (BR2). Confirmar se o dono quer veto.
- Como a marca/produto se liga à reserva (qual campo identifica a marca na venda)? RESOLVIDA no v1 —
  ASSUMIDO (ver **DL-0062**, Confiança=Baixa): **intake próprio** `(booking→brandRef)` + projeção;
  **confirmar com o dono** qual campo nativo da venda deveria alimentar o intake automaticamente.

## Out of Scope

Preços (não moram aqui), cálculo de comissão (SPEC-0004), patrimônio interno (SPEC-0021), negociação
contratual (Admin/humano).
