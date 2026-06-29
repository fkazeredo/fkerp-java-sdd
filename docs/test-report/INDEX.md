# Caderno de testes — Índice

Um arquivo por fatia, com escopo, casos por tipo (unitário/arquitetura/integração/e2e/smoke),
resultado, cobertura e como reproduzir.

| Fatia | Spec | Arquivo | Resultado |
|---|---|---|---|
| Slice 0 — Walking Skeleton | SPEC-0001 | [slice-0-walking-skeleton.md](slice-0-walking-skeleton.md) | ✅ verde (backend 12 testes, frontend 4 testes, smoke OK) |
| Slice 1 — Accounts | SPEC-0002 | [slice-1-accounts.md](slice-1-accounts.md) | ✅ verde (backend 32 testes; tela Angular pendente) |
| Slice 2 — Exchange | SPEC-0003 | [slice-2-exchange.md](slice-2-exchange.md) | ✅ verde (backend 46 testes; tela Angular pendente) |
| Slice 3 — Commissioning | SPEC-0004 | [slice-3-commissioning.md](slice-3-commissioning.md) | ✅ verde (backend 54 testes; tela Angular pendente) |
| Slice 4 — Quoting (keystone) | SPEC-0005 | [slice-4-quoting.md](slice-4-quoting.md) | ✅ verde (backend 62 testes; tela Angular pendente) |
| Slice 5 — Booking | SPEC-0006 | [slice-5-booking.md](slice-5-booking.md) | ✅ verde (backend 73 testes; tela Angular pendente) |
| Slice 6 — Reconciliation | SPEC-0007 | [slice-6-reconciliation.md](slice-6-reconciliation.md) | ✅ verde (backend 82 testes) |
| Fase 1 — Telas Angular | SPEC-0002…0007 | [release-notes/0.2.1.md](../release-notes/0.2.1.md) | ✅ verde (frontend: lint + 14 testes + build; 5 telas + nav) |
| Slice 7a — Finance | SPEC-0015 | [slice-7a-finance.md](slice-7a-finance.md) | ✅ verde (backend 95 testes; veto real na 7c) |
| Slice 7b — Compliance | SPEC-0008 | [slice-7b-compliance.md](slice-7b-compliance.md) | ✅ verde (backend 106 testes; cofre + close-check + retenção) |
| Slice 7c — Veto de fechamento | SPEC-0008/0015 | [slice-7c-close-veto.md](slice-7c-close-veto.md) | ✅ verde (backend 108 testes; regra de ouro + job de retenção) |
| Slice 8a — Sourcing | SPEC-0009 | [slice-8a-sourcing.md](slice-8a-sourcing.md) | ✅ verde (backend 114 testes; SourcedOffer + offers API) |
| Slice 8b — Quoting ramo INTEGRATED | SPEC-0009/0005 | [slice-8b-integrated-quote.md](slice-8b-integrated-quote.md) | ✅ verde (backend 118 testes; composeIntegrated + porta + V10) |
| Slice 8c — ACL de entrada (webhook) | SPEC-0009 | [slice-8c-inbound-acl.md](slice-8c-inbound-acl.md) | ✅ verde (backend 135 testes; HMAC + tradução ACL + idempotência + INTEGRATED ponta a ponta) |
| Slice 9a — CancellationPolicy como objeto | SPEC-0010 | [slice-9a-cancellation-policy-object.md](slice-9a-cancellation-policy-object.md) | ✅ verde (backend 145 testes; política como objeto + fonte administrável V12) |
| Slice 9b — Cancelamento rico + armadilha do merchant | SPEC-0010 | [slice-9b-cancel-charges-merchant-trap.md](slice-9b-cancel-charges-merchant-trap.md) | ✅ verde (backend 151 testes; congelamento + 2 obrigações que não se anulam, V13) |
| Slice 9c — NoShowPolicy | SPEC-0010 | [slice-9c-no-show-policy.md](slice-9c-no-show-policy.md) | ✅ verde (backend 157 testes; fee + dispensa por prova de voo) |
| Slice 10a — Market Rate | SPEC-0011 | [slice-10a-market-rate.md](slice-10a-market-rate.md) | ✅ verde (backend 162 testes; taxa de mercado + porta + V14) |
| Slice 10b — FxPosition (subsídio × drift) | SPEC-0011 | [slice-10b-fx-position.md](slice-10b-fx-position.md) | ✅ verde (backend 174 testes; subsídio/drift/gap + V15; exemplo 7.2 provado) |
| Slice 10c — Relatórios de câmbio (LiveExposure/PromoFx) | SPEC-0011 | [slice-10c-fx-reports.md](slice-10c-fx-reports.md) | ✅ verde (backend 179 testes; agregado do livro + alerta 2% + promo-fx) |
| Slice 11a — People + snapshot operacional | SPEC-0012 | [slice-11a-people-snapshot.md](slice-11a-people-snapshot.md) | ✅ verde (backend 187 testes; módulo people 11º + idempotência + histórico + V16) |
| Slice 11b — Crawler ACL + fila + disjuntor | SPEC-0012 | [slice-11b-crawler-resilience.md](slice-11b-crawler-resilience.md) | ✅ verde (backend 197 testes; circuit breaker + retry/dead-letter + ACL + fronteira ArchUnit) |

## Resumo por nível (Fase 0)

| Nível | Ferramenta | Resultado |
|---|---|---|
| Unitário / Arquitetura (back) | JUnit 5 + ArchUnit + Spring Modulith | ✅ 11 casos |
| Integração (back) | Testcontainers + Postgres | ✅ 1 caso |
| Unitário (front) | Vitest + jsdom | ✅ 4 casos |
| Smoke / E2E | docker-compose + curl | ✅ health 200 UP |
| Portões | Spotless, Checkstyle, ESLint | ✅ 0 violações |
