# Caderno de testes — Fase 16d (Telas de operação: People/RH, Ponto, Assets, Admin, Platform/TI, Identity/acesso)

- **Spec:** SPEC-0029 (estendida — **Fase 16 concluída**) · **Decisão de origem:** DL-0109 · **Release:** 0.27.0 · **Data:** 2026-07-01
- **Escopo:** frontend-only — 6 telas de operação (People/Ponto/Assets/Admin/Platform/Identity) sobre APIs
  que já existiam (`/api/people`, `/api/integration/point`, `/api/assets`, `/api/admin`, `/api/platform`,
  `/api/identity`). Nenhum endpoint novo, nenhum contrato/schema/migração. Bump de versão do backend/OpenAPI
  para 0.27.0.

## Resultado global

✅ **Verde** nos portões executáveis no sandbox. A jornada Playwright de 16d foi **autorada e compila**,
mas **não foi executada** aqui por limitação de infra (ver "E2E").

## Casos por nível

### Unitário / componente (frontend, Vitest + jsdom)

Um spec por tela, cobrindo os estados exigidos (loading→success, empty onde há lista, error/permissão) e
os caminhos de ação, mais um spec de serviço (`HttpTestingController`) por feature para os wrappers HTTP:

- `people-page.spec.ts` — lista (loading→success), empty, error, **permissão (403 `access.denied`)**,
  registrar colaborador + reload + form sujo, ler jornada + banco de horas, **erro de jornada por código
  (`people.journey.invalid`)**, fila de discrepâncias (empty), severidades de status/discrepância.
- `point-page.spec.ts` — histórico de coletas (loading→success), empty, error, **permissão (403)**, ler
  espelho por id, **erro de espelho por código**, severidades de status de coleta.
- `assets-page.spec.ts` — lista (loading→success), empty, error, **permissão (403)**, registrar item +
  reload + form sujo, baixar item + reload, varredura de licenças (total sinalizado), severidades de status.
- `admin-page.spec.ts` — lista de fornecedores (loading→success), empty, **permissão (403)**, registrar
  fornecedor + reload + form sujo, **erro de escrita por código (403 sem `ROLE_FINANCE`)**, contratos
  (empty), registrar despesa (mostra lançamento financeiro), varredura de contratos, severidades de status.
- `platform-page.spec.ts` — catálogo de jobs + certificado (loading→success), **certificado vazio em 404**,
  **permissão (403) no catálogo**, disparar job + reload de execuções, **trigger 403 por código**, erro na
  auditoria, severidades de job/certificado.
- `identity-page.spec.ts` — catálogo de papéis (loading→success), auditoria (empty), **permissão (403) no
  catálogo**, error na auditoria, severidades de tipo de auditoria de acesso.
- Serviços: `people.service.spec.ts`, `point.service.spec.ts`, `assets.service.spec.ts`,
  `admin.service.spec.ts`, `platform.service.spec.ts`, `identity.service.spec.ts` — cada método HTTP
  (GET/POST, query params, path encoding do trigger) via `HttpTestingController`.

**Total frontend:** 264 testes Vitest, 49 arquivos, **0 falhas**.

**Cobertura (v8) — acima dos pisos da Fase 12** (stmts/lines ≥ 65, funcs ≥ 48, branches ≥ 55):

| Métrica | Medido | Piso |
|---|---|---|
| Statements | 73,4 % | 65 % |
| Branches | 56,5 % | 55 % |
| Functions | 51,1 % | 48 % |
| Lines | 78,8 % | 65 % |

### Lint + build (frontend)

- `npx ng lint` → **All files pass linting.**
- `npx ng build` → **sucesso**; chunks lazy emitidos: `people-page`, `point-page`, `assets-page`,
  `admin-page`, `platform-page`, `identity-page`.

### Backend (regressão — inalterado)

- `cd backend && ./mvnw verify` → **BUILD SUCCESS** (exit 0); **Tests run: 476, Failures: 0, Errors: 0**
  no surefire (ArchUnit 17 incluído); ArchUnit/Modulith/JaCoCo verdes. Única mudança de backend: string de
  versão `0.26.0 → 0.27.0` em `pom.xml` e o texto de descrição do `OpenApiConfig` (registra 16c/16d como
  frontend-only e a Fase 16 concluída) — sem impacto de comportamento, contrato ou schema.

### E2E (Playwright)

- Jornada nova `e2e/platform-people.spec.ts`: (1) usuário **IT** (`it`) entra por OIDC → abre **Plataforma/
  TI** pela nav (item gated em `ROLE_IT`) → o card do **certificado** mostra **só metadados** (ou o estado
  vazio quando não há custódia) — nunca a chave/senha → o **catálogo de jobs** renderiza (tabela ou estado
  vazio) → abre **People** por URL (leitura `authenticated()`) → vê o **estado vazio** no DB efêmero; (2) via
  API, token **sem ROLE_IT** (`ops`) recebe **403** no trigger de job governado (autoridade no backend —
  DL-0082) e o **IT é autorizado** (não 401/403).
- **Descoberta pelo Playwright:** ✅ `npx playwright test --list` → **19 testes em 10 arquivos** (17
  anteriores + os 2 novos casos), confirmando que o spec **compila**.
- **Execução no sandbox:** ❌ **não executada**. A build da imagem Docker do backend do `compose.e2e.yaml`
  exige rede/cache Maven **dentro do contêiner**, indisponível no sandbox. Não é defeito de código: o
  `./mvnw verify` **no host** passa. Rodar em ambiente com rede/cache Maven para o build da imagem executa
  a jornada.

## Como reproduzir

```bash
cd frontend && npm ci && npx ng lint && CI=true npx ng test --watch=false && npx ng build
cd backend && ./mvnw verify
# E2E (requer build da imagem do backend com acesso a artefatos Maven):
cd frontend && npm run e2e:up && E2E_BASE_URL=http://localhost:4201 npm run e2e && npm run e2e:down
```

## Riscos / pendências

- E2E não executada no sandbox (infra, acima). **Fase 16 concluída** — não há mais fatias de UI de operação
  pendentes; toda a dívida de UI (DL-0109) está quitada. Toda tela usa o padrão SPEC-0026 (service +
  `<app-screen-state>` + rota lazy + nav por papel + i18n bilíngue), sem mudança de contrato/schema.
