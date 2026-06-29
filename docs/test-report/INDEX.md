# Caderno de testes — Índice

Um arquivo por fatia, com escopo, casos por tipo (unitário/arquitetura/integração/e2e/smoke),
resultado, cobertura e como reproduzir.

| Fatia | Spec | Arquivo | Resultado |
|---|---|---|---|
| Slice 0 — Walking Skeleton | SPEC-0001 | [slice-0-walking-skeleton.md](slice-0-walking-skeleton.md) | ✅ verde (backend 12 testes, frontend 4 testes, smoke OK) |

## Resumo por nível (Fase 0)

| Nível | Ferramenta | Resultado |
|---|---|---|
| Unitário / Arquitetura (back) | JUnit 5 + ArchUnit + Spring Modulith | ✅ 11 casos |
| Integração (back) | Testcontainers + Postgres | ✅ 1 caso |
| Unitário (front) | Vitest + jsdom | ✅ 4 casos |
| Smoke / E2E | docker-compose + curl | ✅ health 200 UP |
| Portões | Spotless, Checkstyle, ESLint | ✅ 0 violações |
