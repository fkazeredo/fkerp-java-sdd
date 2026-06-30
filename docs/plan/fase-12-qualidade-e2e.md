# Plano — Fase 12 (Qualidade & E2E) · SPEC-0028

> Tooling de qualidade verificável (cobertura como portão + Playwright E2E em stack isolado +
> caminhos tristes + job de E2E no CI), espelhando o fkerp-poc. **PATCH** 0.22.1 (ADR 0015) — sem
> mudar contrato, schema ou feature de produto.

## Ordem das fatias (incremental, portão verde a cada passo)

| Fatia | Entrega | Portão a rodar verde |
|---|---|---|
| 12-1 | JaCoCo no backend: `prepare-agent` + `report` + **`check`** (INSTRUCTION ≥ limiar) na fase `verify` | `cd backend && ./mvnw verify` (477 testes + JaCoCo check) |
| 12-2 | `@vitest/coverage-v8` + `vitest.config.ts` com `thresholds`; `ng test` via `--runner-config` | `cd frontend && npm run test:coverage` (57 testes + limiar) |
| 12-3 | `compose.e2e.yaml` (Postgres efêmero, 4201/8081) + Dockerfile/nginx do frontend + Playwright config; **prova de isolamento** | `e2e:up` sobe; `GET /api/system/health` UP; dev DB intacto |
| 12-4 | Specs Playwright: jornadas críticas + caminhos tristes (AC3–AC7) | `npm run e2e` headless verde na 4201 |
| 12-5 | Job de E2E no CI (`.github/workflows/e2e.yml`): up → wait → e2e headless → down (`if: always()`) | workflow válido (yamllint/estrutura) |
| 12-6 | Docs: release note pt-BR 0.22.1 + CHANGELOG en-US; test-reports + INDEX; bump pom/OpenAPI 0.22.1 | `./mvnw verify` final + `ng test` final |

Cada fatia = `feature/12-<slice>` a partir de `feature/12-integration`; commits pequenos; merge
`--no-ff` de volta só com o portão verde.

## Decisões autônomas (DL antes do código dependente)

- **DL-0099** — limiar JaCoCo backend (medir cobertura atual; fixar piso defensável abaixo do medido).
- **DL-0100** — limiar Vitest frontend (idem, statements/lines/functions).
- **DL-0101** — isolamento do stack E2E (Postgres efêmero/tmpfs, portas 4201/8081, perfil dev p/ seed).
- **DL-0102** — Playwright + escopo das jornadas/caminhos tristes + job de E2E no CI.

## Limiares — método

1. Medir a cobertura **atual** (rodar JaCoCo report e Vitest coverage uma vez).
2. Fixar o limiar **abaixo** do medido, numa margem que absorva flutuação normal (alvo: piso redondo,
   ~5–10 pp abaixo do medido), de modo que o código atual **passe** e uma queda real **falhe**.
3. Registrar o número medido e o limiar escolhido no DL (Confiança/Reversibilidade).

## Isolamento (requisito inegociável)

- `compose.e2e.yaml`: `name: acme-e2e`; serviço `postgres` com `tmpfs: /var/lib/postgresql/data`
  (sem volume nomeado); `backend` perfil `dev` (seed de usuários), porta host **8081**; `frontend`
  Nginx porta host **4201**, proxy `/api` → backend. Rede `acme-e2e`.
- Distinções vs `docker-compose.yml` (dev): nome do projeto (`acme-e2e` × default), rede, portas
  (4201/8081 × 4200/8080/5432), DB efêmero (tmpfs × volume `db-data`).
- Prova: a corrida de E2E **não inicia** o stack de dev; `e2e:down` remove o container e o tmpfs.

## Não-negociáveis

- Nenhum portão enfraquecido; 477 backend + 57 frontend verdes.
- Cobertura é **gate** (queda abaixo do limiar quebra o build).
- Sem migração (mesma schema; o DB efêmero roda as mesmas migrações).
- Sem `node_modules`/browsers do Playwright no git.
- Manual **inalterado** (nada visível ao usuário muda — Regra Zero).
