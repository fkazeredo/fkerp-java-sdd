# Plano da Fase 8g — Portfolio (SPEC-0020)

> Modo autônomo (RUN-PHASE). FASE-ALVO = 8, **escopo SPEC-0020 apenas**. Implementa o contexto de
> **Portfolio (representação)**: as **marcas/fornecedores** que a Acme representa, os **contratos de
> representação** (vigência + documento no Compliance, com alerta de expiração) e as **metas por
> marca** (VOLUME/REVENUE) com **realizado vs meta** projetado de eventos de venda. Versão alvo:
> **0.15.0** (próximo MINOR após 0.14.0, ADR 0015).

## Contexto e fronteiras

Novo módulo `com.fksoft.domain.portfolio` (**17º módulo Modulith**, DL-0060). Agregados:
`RepresentedBrand` (marca/fornecedor, status ACTIVE/INACTIVE), `RepresentationContract` (vigência,
documento no Compliance por id-valor, alerta de expiração idempotente), `BrandGoal` (meta por
marca/período/métrica). Mais o **intake** `BrandSaleAttribution` (`booking→brandRef`, UNIQUE) e a
**projeção** `BrandRealized` (realizado idempotente por evento de venda — DL-0062).

Portfolio **referencia** a marca/contrato; **não precifica nem calcula comissão** (BR6). É consumido
por Quoting/Commissioning/DSS como referência — **não comanda** a venda (DL-0061: alerta, não veto).

Direção de dependências (grafo **acíclico**, Spring Modulith verify):

- `portfolio → booking` **só por evento** (`BookingConfirmed`, projeta VOLUME, DL-0062).
- `portfolio → reconciliation` **só por evento** (`SpreadRealized`, projeta REVENUE em BRL, DL-0062).
- `portfolio → money`/`error` (kernels).
- Nenhum módulo de negócio depende de volta no Portfolio (folha do grafo, como Marketing).
- **Sem FK cross-contexto**: `brandRef` (string), `bookingId`/`caseId`/`documentId` (uuid) são
  **valores**, não FKs (modules-and-apis.md).

Observabilidade (observability.md): logar representação/contrato/expiração como evento de negócio
(`brandRef`, correlation id). Marca/fornecedor **não é PII** (sem mascaramento necessário). Métricas
de negócio: `represented_brands_active`, `representation_expiring_total`, atingimento de meta.

## Fatias (uma feature branch por fatia → merge --no-ff em feature/8g-integration)

### 8g-1 — RepresentedBrand + RepresentationContract + alerta de expiração · BR1/BR2/BR5
- Agregado `RepresentedBrand` (`internal`): id, brandRef (UNIQUE), displayName, status
  (ACTIVE|INACTIVE), audit, version. `BrandStatus`. Duplicidade de brandRef → erro de negócio
  traduzido (`portfolio.brand.duplicate`, 409), nunca constraint crua.
- Agregado `RepresentationContract` (`internal`): id, brandRef (valor), validFrom/validUntil,
  documentId (valor p/ Compliance), termsJson (jsonb), `expiringSignaledAt` (DL-0063), audit, version.
- `PortfolioService.registerBrand / getBrand / listBrands / deactivateBrand / registerContract /
  contractCoverage(brandRef, on) / flagExpiringContracts(now)`.
- Eventos: `BrandRepresented {brandRef, occurredAt}`, `RepresentationContractRegistered {brandRef,
  occurredAt}`, `RepresentationExpiring {brandRef, validUntil, occurredAt}`.
- Exceções: `BrandNotFoundException` (404), `BrandDuplicateException` (409),
  `RepresentationContractInvalidException` (400).
- Migração `V25` (parte): `represented_brands` (UNIQUE brand_ref), `representation_contracts`.
- Controller: `POST /api/portfolio/brands`, `GET /brands/{id}`, `GET /brands?status=`,
  `DELETE /brands/{id}` (desativa), `POST /brands/{brandRef}/contracts`,
  `GET /brands/{id}/contract-coverage?on=`, `POST /contracts/flag-expiring`.
- i18n (`portfolio.brand.*`), `HttpErrorMapping`, OpenAPI.
- Testes: unit (estados de marca; vigência coerente do contrato; expiração sinaliza uma vez —
  idempotente; cobertura de contrato vigente/expirado); integração (persistência, brandRef duplicado
  → 409, GET por id inexistente → 404, expiração publica `RepresentationExpiring`).

### 8g-2 — BrandGoal + realizado vs meta (projeção de eventos de venda) · BR3/BR4/BR6
- Agregado `BrandGoal` (`internal`): id, brandRef (valor), period (YYYY ou YYYY-MM), metric
  (VOLUME|REVENUE), targetAmount (REVENUE, BRL) | targetCount (VOLUME); `UNIQUE (brand_ref, period,
  metric)`. `GoalMetric`. `BrandGoalInvalidException` (400, `portfolio.goal.invalid`).
- Intake `BrandSaleAttribution` (`internal`): id, bookingId (UNIQUE), brandRef, attributedAt
  (DL-0062). `PortfolioService.attributeSale(brandRef, bookingId)` — idempotente.
- Projeção `BrandRealized` (`internal`): id, brandRef, metric, sourceRef, amount|countInc,
  occurredAt; `UNIQUE (metric, source_ref)` (idempotência do evento). Repos de projeção.
- Listeners internos: `PortfolioBookingEventsListener` (consome `BookingConfirmed` → VOLUME +1 para a
  marca do intake), `PortfolioReconciliationEventsListener` (consome `SpreadRealized` → REVENUE +
  spread BRL para a marca do intake, resolvendo case→booking pelo intake). Sem código pré-registrado
  → não conta (sem atribuição forçada).
- `PortfolioService.defineGoal / goalProgress(brandId, period)` → cruza realizado × target,
  `attainmentPct` (escala 1, HALF_UP).
- Migração `V25` (parte): `brand_goals`, `brand_sale_attributions`, `brand_realized`.
- Controller: `POST /brands/{brandRef}/goals`, `GET /brands/{id}/goals/{period}/progress`,
  `POST /brands/{brandRef}/sales` (intake).
- i18n (`portfolio.goal.invalid`), OpenAPI. Métrica: atingimento de meta por marca.
- Testes: unit (projeção VOLUME e REVENUE; attainmentPct; idempotência da projeção); integração
  (`BookingConfirmed` de uma marca incrementa o realizado VOLUME; `SpreadRealized` incrementa REVENUE;
  re-entrega não soma duas vezes; meta única por (marca, período, métrica)); **regressão BR6**:
  Portfolio não altera nem precifica a venda (a projeção é read-model — falha antes, passa depois).

## Definition of Done por fatia (TUTORIAL §3)
Critérios de aceite da spec → testes verdes; `./mvnw verify` verde (ArchUnit + Modulith);
migração Flyway idempotente (V25); `DomainException` code==chave i18n (pt-BR + fallback); sem
exceção crua de banco; OpenAPI atualizada; observabilidade (evento de negócio logado, correlation
id; marca não é PII); costura adiada = mock/seam rastreável (intake venda→marca, seam de marca nativa
na venda); Spotless aplicado; Conventional Commits; caderno de testes atualizado.

## Migração
**Única migração `V25__create_portfolio.sql`** (próxima após V24), idempotente, cobrindo as cinco
tabelas (`represented_brands`, `representation_contracts`, `brand_goals`, `brand_sale_attributions`,
`brand_realized`). Nunca editar V1–V24 já aplicadas.

## Riscos / pontos de atenção
- **DL-0062 (Confiança=Baixa):** qual campo identifica a marca na venda é incógnita de negócio. O
  intake próprio + seam rastreável (espelha DL-0057) protege o contrato de metas; quando a marca for
  nativa na venda, troca-se a fonte no listener sem mexer nas tabelas. **Confirmar com o dono.**
- **`SpreadRealized` carrega `caseId`, não `bookingId`:** o casamento case→booking→marca usa o
  intake; no v1 o intake pode registrar a marca pelo booking (e a projeção de REVENUE resolve via o
  caso quando o produtor só expõe o caseId). Seam documentado.
- **Realizado é projeção, não venda:** Portfolio nunca escreve no Booking/Reconciliation (BR6).

## O que NÃO entra (Out of Scope da spec / Rule Zero)
Preços (não moram aqui); cálculo de comissão (Commissioning); patrimônio interno (Assets/SPEC-0021);
negociação contratual (Admin/humano); bloqueio de venda sem contrato (v1 alerta, DL-0061); parâmetro
governado da antecedência de expiração (value default 30d, DL-0063); captura de marca nativa no fluxo
de venda (seam, DL-0062).
