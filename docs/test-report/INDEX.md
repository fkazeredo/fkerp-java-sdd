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

## Resumo por nível (Fase 0)

| Nível | Ferramenta | Resultado |
|---|---|---|
| Unitário / Arquitetura (back) | JUnit 5 + ArchUnit + Spring Modulith | ✅ 11 casos |
| Integração (back) | Testcontainers + Postgres | ✅ 1 caso |
| Unitário (front) | Vitest + jsdom | ✅ 4 casos |
| Smoke / E2E | docker-compose + curl | ✅ health 200 UP |
| Portões | Spotless, Checkstyle, ESLint | ✅ 0 violações |
