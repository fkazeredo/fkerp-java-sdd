# Relatório — Fase 1: Núcleo comercial manual (SPEC-0002…0007)

- **Data:** 2026-06-29 · **Release:** `0.2.0` · **Branch base:** `develop` → `main`
- **Resultado:** `cd backend && ./mvnw verify` → **BUILD SUCCESS**, `Tests run: 82, Failures: 0`.

## Fatias entregues (6/6, cada uma verde e mergeada em `develop`)

| Slice | Spec | Módulo | Entregável |
|---|---|---|---|
| 1 | SPEC-0002 | `accounts` | Conta comercial CNPJ/MEI/CPF (validação/unicidade/status) |
| 2 | SPEC-0003 | `exchange` | Taxa congelada append-only (Open-Host) + histórico |
| 3 | SPEC-0004 | `commissioning` | Comissão de duas pontas + spread (puro) + kernel `Money` |
| 4 | SPEC-0005 | `quoting` (+`commercialpolicy` stub) | Composição + override com proveniência (keystone) |
| 5 | SPEC-0006 | `booking` | Ciclo de vida + localizador + timeout 72h + eventos |
| 6 | SPEC-0007 | `reconciliation` | Caso por venda: esperado × realizado + ganho/perda cambial |

## Arquivos (resumo)

- **Domínio:** 7 módulos novos sob `com.fksoft.domain.*` (cada um com base pública + `internal`
  privado), + kernel `domain.money`. ~70 arquivos Java de produção.
- **Delivery:** 6 controllers + DTOs em `application.api`.
- **Infra:** `infra.jobs` (scheduler de timeout + `@EnableScheduling`); `HttpErrorMapping` e
  `GlobalExceptionHandler` estendidos (handlers de 400 para corpo/param malformado).
- **Migrações:** `V2`…`V6` (Flyway).
- **Testes:** 12 classes novas (unit + Testcontainers).

## Specs / ADRs atualizados

- Specs 0002–0007: *Open Questions* resolvidas movidas para ASSUMIDO (com link ao DL).
- **ADR 0015** (SemVer) criado a pedido do dono; **ADR 0014** já existente.

## Testes por tipo

| Tipo | Ferramenta | Resultado |
|---|---|---|
| Unit/domínio | JUnit 5 + AssertJ | ✅ (Document, CurrencyPair, Commission, Quote, BookingStatus, ReconciliationCase) |
| Integração | Testcontainers + Postgres real | ✅ (6 contextos, fachadas reais cruzando módulos) |
| Arquitetura | ArchUnit (6 regras) + Spring Modulith `verify()` | ✅ 7 módulos com fronteiras impostas |
| Portões | Spotless + Checkstyle | ✅ 0 violações |

Saída: `Tests run: 82, Failures: 0, Errors: 0` (Fase 0: 12 → 82).

## OpenAPI

Novos recursos documentados via springdoc: accounts, exchange/pinned-rates, commissioning/preview,
quotes (+override), bookings (+ações), reconciliation (+settlement).

## Decisões (decision-log)

- [DL-0007](decision-log/DL-0007-accounts-cadastros-opcionais.md) — CADASTUR/IATA opcionais.
- [DL-0008](decision-log/DL-0008-exchange-nome-do-modulo.md) — manter `Exchange`.
- [DL-0009](decision-log/DL-0009-quoting-formula-de-preco.md) — **preço = base BRL + markup** (Rev. **Cara**).
- [DL-0010](decision-log/DL-0010-booking-quote-multiplicidade.md) — Quote→Booking não 1:1.
- [DL-0011](decision-log/DL-0011-reconciliation-tolerancia-discrepancia.md) — tolerância `max(R$1; 0,5%)`.

## Riscos / pendências (para a próxima fase)

- **Telas Angular** dos 5 contextos da Fase 1 — **não entregues** nesta release; backend completo e
  testado. É o principal follow-up (0.2.x) antes de declarar a Fase 1 "feita ponta a ponta".
- `CommercialPolicy` markup é stub `SYSTEM_DEFAULT` (gradua na SPEC-0014).
- Eventos in-process (sem outbox) — adequado a monólito; revisitar ao extrair serviço.
- DL-0009 tem Reversibilidade **Cara** — confirmar a fórmula de preço com o dono antes de escalar.

## Próxima fase

Fase 2 — Compliance mínimo (SPEC-0008 + seam Finance/0015).
