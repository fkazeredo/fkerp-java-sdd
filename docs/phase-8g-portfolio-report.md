# Relatório da Fase 8g — Portfolio (SPEC-0020)

- **Fase:** 8g (Portfolio — subdomínio Supporting). **Release:** 0.15.0 (próximo MINOR após 0.14.0).
- **Base:** `origin/develop` @ 3296d67. **Branch de integração:** `feature/8g-integration`.
- **Resultado:** `./mvnw verify` **BUILD SUCCESS** — **374 testes**, 0 falhas; **ArchUnit 14 regras**
  (inclui a nova BR6 "Portfolio referencia, nunca comanda a venda"); Spring Modulith acíclico (**17º
  módulo** `portfolio`); Spotless 0 alterações; Checkstyle 0 violações; artefato
  `acme-travel-erp-0.15.0.jar`.

## Fatias entregues

| Fatia | Entrega | BRs |
|---|---|---|
| **8g-1** Marcas + contratos + alerta de expiração | `RepresentedBrand` (brandRef único → 409 traduzido; ACTIVE/INACTIVE); `RepresentationContract` (vigência + documento Compliance por valor + termos jsonb); cobertura de contrato como **leitura/alerta** (não bloqueia, DL-0061); `RepresentationExpiring` por **job de relógio controlado** 30d idempotente (DL-0063); V25 | BR1, BR2, BR5, BR6 |
| **8g-2** Metas + realizado vs meta | `BrandGoal` (VOLUME/REVENUE) única por (marca, período, métrica); **realizado projetado de eventos de venda** — `BookingConfirmed`→VOLUME, `SpreadRealized`→REVENUE (BRL) casados à marca por **intake próprio** `booking→brand`, idempotente por evento; `ReconciliationCaseOpened` liga caso→reserva; progresso (target × realizado × attainmentPct); regra ArchUnit BR6 | BR3, BR4, BR6 |

## Arquivos (alto nível)

- **Domínio (novo módulo `com.fksoft.domain.portfolio`):** `PortfolioService` (fachada); agregados
  `internal/{RepresentedBrand, RepresentationContract, BrandGoal, BrandSaleAttribution, BrandRealized}`
  + repositórios + `TermsCodec`; enums `BrandStatus`/`GoalMetric`; views/commands/eventos (`BrandView`,
  `ContractView`, `ContractCoverage`, `GoalView`, `GoalProgress`, `Register*Command`,
  `DefineGoalCommand`, `BrandRepresented`, `RepresentationContractRegistered`, `RepresentationExpiring`);
  exceções (`Brand*`, `RepresentationContractInvalid`, `BrandGoalInvalid`); listener
  `internal/PortfolioSalesEventsListener` (consome `BookingConfirmed`, `ReconciliationCaseOpened`,
  `SpreadRealized`); `package-info` com `@ApplicationModule`.
- **Delivery:** `PortfolioController` (10 endpoints) + DTOs `RegisterBrandRequest`,
  `RegisterContractRequest`, `DefineGoalRequest`, `AttributeSaleRequest`.
- **Transversais:** `HttpErrorMapping` (+5 exceções de Portfolio), i18n pt-BR + fallback en
  (`portfolio.*`), `OpenApiConfig` (descrição Portfolio + versão 0.15.0), `ArchitectureTest` (+1 regra
  BR6 com dentes), `pom.xml` (0.15.0).
- **Migração:** `V25__create_portfolio.sql` (5 tabelas; sem FK cross-contexto).
- **Testes:** `domain/portfolio/internal/{RepresentationContractTest, BrandGoalTest}` (unit);
  `portfolio/{BrandAndContractIntegrationTest, GoalRealizedProjectionIntegrationTest}` (Testcontainers).

## Specs / ADRs / decisões atualizados

- **SPEC-0020:** Open Questions resolvidas e movidas para Business Rules com "ASSUMIDO (ver DL-NNNN)";
  Persistence atualizado para V25 (+ `brand_sale_attributions`/`brand_realized`); API Contracts
  ampliados (sales/coverage/flag-expiring).
- **Decision-log (novas):** [DL-0060](decision-log/DL-0060-portfolio-separate-context-from-assets.md)
  (dois contextos — Alta/Moderada);
  [DL-0061](decision-log/DL-0061-portfolio-sell-without-active-contract-alerts-not-blocks.md) (alerta,
  não bloqueia — Média/Barata);
  **[DL-0062](decision-log/DL-0062-portfolio-brand-sale-attribution-intake-and-realized-projection.md)**
  (intake próprio + projeção, sem alterar o evento da venda — **Confiança=Baixa** / Reversibilidade
  Moderada);
  [DL-0063](decision-log/DL-0063-portfolio-representation-expiring-controlled-clock-alert.md) (alerta de
  expiração por relógio controlado — Média/Barata). INDEX do decision-log atualizado (destaque para
  DL-0062 em Confiança=Baixa).
- **ADRs:** nenhum novo (reusa ADR 0011/0012/0013 e os padrões de modules-and-apis/messaging).

## Migrações

- **V25__create_portfolio.sql** (idempotente, próxima após V24): `represented_brands`,
  `representation_contracts`, `brand_goals`, `brand_sale_attributions`, `brand_realized`. Nenhuma FK
  cross-contexto (ids de outros contextos são valor).

## Testes por tipo

- **Unitário (domínio):** `RepresentationContractTest` (vigência, em vigor, expiração idempotente),
  `BrandGoalTest` (período, alvo coerente com a métrica) — **verde**.
- **Integração (Testcontainers/Postgres):** `BrandAndContractIntegrationTest` (CRUD, brandRef duplicado
  409, 404, cobertura, expiração publica evento), `GoalRealizedProjectionIntegrationTest` (metas,
  projeção VOLUME/REVENUE por eventos, idempotência, sem-marca-não-conta, fora-de-período) — **verde**.
- **Arquitetura:** `ArchitectureTest` (14 regras, inclui a nova BR6), `ModularityTests` (17º módulo
  acíclico), `HttpErrorMappingCompletenessTest` — **verde**.
- **Saída consolidada:** `Tests run: 374, Failures: 0, Errors: 0, Skipped: 0` → **BUILD SUCCESS**.

## Impacto em OpenAPI

- Novos endpoints sob `/api/portfolio/*` (brands, contracts, contract-coverage, flag-expiring, goals,
  goals/{period}/progress, sales). Descrição e versão da OpenAPI atualizadas para **0.15.0**.

## Riscos / o que ficou para a próxima fase

- **DL-0062 (Confiança=Baixa):** qual campo identifica a marca na venda é incógnita de negócio. O intake
  próprio + seam rastreável protege o contrato; quando a marca for nativa na venda (Sourcing/Quoting),
  troca-se a fonte do casamento no listener sem mexer nas tabelas. **Confirmar com o dono.**
- **Bloqueio de venda sem contrato (DL-0061)** e **antecedência governada de expiração (DL-0063)** são
  evoluções aditivas adiadas.
- **Assets (SPEC-0021)** fica como módulo próprio futuro (DL-0060). **Tela Angular** do Portfolio fica
  para a Fase 10 (UX) — coerente com 8c–8f.
