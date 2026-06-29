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
| Slice 11c — Ingestão AFD/AEJ assinado → cofre | SPEC-0012 | [slice-11c-afd-legal-ingestion.md](slice-11c-afd-legal-ingestion.md) | ✅ verde (backend 206 testes; verificação CAdES/PKCS#7 + cofre com retenção 5 anos) |
| Slice 12a — Intelligence framework + PromoFxAdvisor | SPEC-0013 | [slice-12a-intelligence-promofx.md](slice-12a-intelligence-promofx.md) | ✅ verde (backend 216 testes; 12º módulo + insight read-model + advisor determinístico + V17 + ArchUnit "aconselha, nunca comanda" com dentes) |
| Slice 12b — OverrideNudge (gated) + decisão + observabilidade | SPEC-0013 | [slice-12b-nudge-decision.md](slice-12b-nudge-decision.md) | ✅ verde (backend 219 testes; nudge off por flag sem dado falso + decisão humana sem ação + InsightDecided) |
| Slice 8a — CommercialPolicy (parâmetros governados + precedência) | SPEC-0014 | [slice-8a-commercial-policy.md](slice-8a-commercial-policy.md) | ✅ verde (backend 239 testes; motor de precedência Diretiva>Promoção>Contrato>Política>Padrão com proveniência + V18 seed; gradua o stub de markup sem quebrar o Quoting; diretiva auditada vence imediatamente) |
| Slice 8b-1 — Finance: lançamento automático de AP/AR por evento | SPEC-0015 (full) | [slice-8b1-finance-event-posting.md](slice-8b1-finance-event-posting.md) | ✅ verde (backend 243 testes; consome eventos do Booking → AP/AR idempotente UNIQUE(source_ref,charge_kind) + V19; merchant trap preservado; regra de ouro intacta; Modulith acíclico finance→booking) |
| Slice 8b-2 — Finance: balancete do período por moeda/status | SPEC-0015 (full) | [slice-8b2-finance-trial-balance.md](slice-8b2-finance-trial-balance.md) | ✅ verde (backend 245 testes; `GET /periods/{yyyymm}/trial-balance` por moeda com net=AR−AP + contagens por status; aditivo, sem migração; sem plano de contas — DL-0043) |

## Resumo por nível (Fase 0)

| Nível | Ferramenta | Resultado |
|---|---|---|
| Unitário / Arquitetura (back) | JUnit 5 + ArchUnit + Spring Modulith | ✅ 11 casos |
| Integração (back) | Testcontainers + Postgres | ✅ 1 caso |
| Unitário (front) | Vitest + jsdom | ✅ 4 casos |
| Smoke / E2E | docker-compose + curl | ✅ health 200 UP |
| Portões | Spotless, Checkstyle, ESLint | ✅ 0 violações |
