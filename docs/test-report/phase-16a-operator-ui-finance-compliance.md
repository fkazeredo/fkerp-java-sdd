# Caderno de testes — Fase 16a (Telas de operação: Financeiro, Faturamento, Repasses, Conformidade)

- **Spec:** SPEC-0029 (nova) · **Decisão de origem:** DL-0109 · **Release:** 0.24.0 · **Data:** 2026-06-30
- **Escopo:** frontend-only — 4 telas de operação (Finance/Billing/Payout/Compliance) sobre APIs que já
  existiam (`/api/finance`, `/api/billing`, `/api/payouts`, `/api/compliance`). Nenhum endpoint novo,
  nenhum contrato/schema/migração. Bump de versão do backend/OpenAPI para 0.24.0.

## Resultado global

✅ **Verde** nos portões executáveis no sandbox. A jornada Playwright de 16a foi **autorada e compila**,
mas **não foi executada** aqui por limitação de infra (ver "E2E").

## Casos por nível

### Unitário / componente (frontend, Vitest + jsdom)

Um spec por tela, cobrindo os estados exigidos (loading→success, empty, error/permissão) e os caminhos
de ação:

- `finance-page.spec.ts` — lista (loading→success), empty, error, **permissão (403 `access.denied`)**,
  criar lançamento + reload + limpa campo, erro de criação por código, consultar período + balancete +
  fechar, severidades de status/período, **fechamento vetado** (`finance.period.cannot-close`).
- `billing-page.spec.ts` — idle inicial, lookup (loading→success), error, **permissão ao emitir (403)**,
  criar rascunho, emitir (render da NF emitida + severidades), cancelar com motivo.
- `payout-page.spec.ts` — lista (loading→success), empty, error, criar + selecionar, erro de criação,
  selecionar+detalhe+executar (sucesso), **FAILED explícito sem falso positivo**, severidades.
- `compliance-page.spec.ts` — idle inicial, close-check (pendências), pode-fechar, ler documento,
  error de lookup, **permissão (403 close-check)**, capturar arquivo + upload, limpar arquivo, erro de
  upload por código.

Também ajustado `shell.spec.ts`: o teste "navegação completa" agora usa um usuário com **todos os
papéis** (a nav ganhou itens gated em `ROLE_FINANCE`), e um novo caso prova que a nav **esconde** os
itens gated de quem não tem o papel.

**Total frontend:** 89 testes Vitest, 21 arquivos, **0 falhas**.

**Cobertura (v8) — acima dos pisos da Fase 12** (stmts/lines ≥ 65, funcs ≥ 48, branches ≥ 55):

| Métrica | Medido | Piso |
|---|---|---|
| Statements | 76,9 % | 65 % |
| Branches | 59,4 % | 55 % |
| Functions | 50,1 % | 48 % |
| Lines | 78,7 % | 65 % |

### Lint + build (frontend)

- `npx ng lint` → **All files pass linting.**
- `npx ng build` → **sucesso**; chunks lazy emitidos: `finance-page`, `billing-page`, `payout-page`,
  `compliance-page`.

### Backend (regressão — inalterado)

- `cd backend && ./mvnw verify` → **BUILD SUCCESS**; linha agregada **Tests run: 476, Failures: 0,
  Errors: 0, Skipped: 0**; **0 violações de Checkstyle**; ArchUnit 17 regras verdes. Única mudança de
  backend: string de versão `0.23.1 → 0.24.0` em `pom.xml` e `OpenApiConfig` — sem impacto de
  comportamento.

### E2E (Playwright)

- Jornada nova `e2e/finance.spec.ts`: (1) usuário **FINANCE** entra por OIDC → abre **Financeiro** pela
  nav (item gated em `ROLE_FINANCE`) → vê o **estado vazio** do razão no DB efêmero → abre
  **Conformidade** → roda o **close-check** e vê "pode fechar"; (2) via API, token **não-FINANCE**
  (`ops`) recebe **403** no fechamento de período e um token **FINANCE** **não** é bloqueado (portão por
  papel, não bloqueio geral).
- **Descoberta pelo Playwright:** ✅ `npx playwright test --list` → **13 testes em 7 arquivos** (11
  anteriores + os 2 novos casos), confirmando que o spec **compila**.
- **Execução no sandbox:** ❌ **não executada**. O `npm run e2e:up` falhou ao **construir a imagem Docker
  do backend** do `compose.e2e.yaml` com `DependencyResolutionException` do Maven **dentro do contêiner**
  (build sem cache `.m2`/rede de artefatos no sandbox). Não é defeito de código: o `./mvnw verify` **no
  host** resolve as dependências e passa. Rodar em ambiente com rede/cache Maven para o build da imagem
  executa a jornada.

## Como reproduzir

```bash
cd frontend && npm ci && npx ng lint && CI=true npx ng test --watch=false && npx ng build
cd backend && ./mvnw verify
# E2E (requer build da imagem do backend com acesso a artefatos Maven):
cd frontend && npm run e2e:up && E2E_BASE_URL=http://localhost:4201 npm run e2e && npm run e2e:down
```

## Riscos / pendências

- E2E não executada no sandbox (infra, acima). Fatias **16b–16d** entregam o restante das telas da
  Fase 16 (releases `0.25.0`…`0.27.0`).
