# Caderno de testes — Fase 16b (Telas de operação: Pós-venda, Origem de ofertas, Mesa de câmbio, Cancelamento)

- **Spec:** SPEC-0029 (estendida) · **Decisão de origem:** DL-0109 · **Release:** 0.25.0 · **Data:** 2026-07-01
- **Escopo:** frontend-only — 4 telas de operação (AfterSales/Sourcing/Exchange-mesa/Cancelamento) sobre
  APIs que já existiam (`/api/aftersales`, `/api/sourcing`, `/api/exchange` — exposure/positions/
  market-rates/reports — e `/api/products/*/cancellation-policy`). Nenhum endpoint novo, nenhum
  contrato/schema/migração. Bump de versão do backend/OpenAPI para 0.25.0.

## Resultado global

✅ **Verde** nos portões executáveis no sandbox. A jornada Playwright de 16b foi **autorada e compila**,
mas **não foi executada** aqui por limitação de infra (ver "E2E"). A tela de câmbio existente (taxa
congelada) e seus testes permanecem **intactos**.

## Casos por nível

### Unitário / componente (frontend, Vitest + jsdom)

Um spec por tela, cobrindo os estados exigidos (loading→success, empty onde há lista, error/permissão) e
os caminhos de ação, mais um spec de serviço (`HttpTestingController`) por feature para os wrappers HTTP:

- `aftersales-page.spec.ts` — lista (loading→success), empty, error, **permissão (403 `access.denied`)**,
  abrir chamado + selecionar + reload, conduzir transição (assign), **resolver com erro por código**,
  severidades de status.
- `sourcing-page.spec.ts` — idle inicial, lookup por id (loading→success), error, **permissão (403)**,
  registrar oferta + render + reset do form sujo, erro de registro por código, severidades de integração.
- `exchange-desk-page.spec.ts` — carrega exposição + histórico de taxa no init (success), empty do
  histórico, **error de exposição**, **permissão (403)**, registrar taxa + reload, lookup de posição
  (idle→success), promo-fx (error por código e success).
- `cancellation-page.spec.ts` — idle inicial, lookup + seed do form (loading→success), error, **permissão
  (403)**, adicionar/remover janelas + form sujo, salvar + limpa sujo, **erro `cancellation.policy.invalid`**,
  severidades de tipo.
- Serviços: `aftersales.service.spec.ts`, `sourcing.service.spec.ts`, `cancellation.service.spec.ts`,
  `exchange-desk.service.spec.ts` — cada método HTTP (GET/POST/PUT, query params, encode de ref) via
  `HttpTestingController`.

Também ajustado `shortcut.service.ts` (não um teste): o mapa `g`+tecla passa a **manter a primeira** tela
quando duas rotas dividem a inicial (accounts/aftersales, exchange/exchange-desk, compliance/cancellation),
preservando o atalho existente `g a → /accounts` (o `shortcut.service.spec.ts` de base segue verde sem
alteração). O `shell.spec.ts` de base já computa a contagem de nav dinamicamente de `NAV_ITEMS`, então
continua verde com os itens novos gated em `ROLE_OPERATIONS`.

**Total frontend:** 135 testes Vitest, 29 arquivos, **0 falhas**.

**Cobertura (v8) — acima dos pisos da Fase 12** (stmts/lines ≥ 65, funcs ≥ 48, branches ≥ 55):

| Métrica | Medido | Piso |
|---|---|---|
| Statements | 70,8 % | 65 % |
| Branches | 58,1 % | 55 % |
| Functions | 49,5 % | 48 % |
| Lines | 74,7 % | 65 % |

### Lint + build (frontend)

- `npx ng lint` → **All files pass linting.** (dois `== null` de template corrigidos para `=== null`).
- `npx ng build` → **sucesso**; chunks lazy emitidos: `aftersales-page`, `sourcing-page`,
  `exchange-desk-page`, `cancellation-page`.

### Backend (regressão — inalterado)

- `cd backend && ./mvnw verify` → **BUILD SUCCESS** (exit 0); **Tests run: 476, Failures: 0, Errors: 0**
  no surefire; **0 violações de Checkstyle**; ArchUnit/Modulith/JaCoCo verdes. Única mudança de backend:
  string de versão `0.24.0 → 0.25.0` em `pom.xml` e o texto de descrição do `OpenApiConfig` — sem impacto
  de comportamento, contrato ou schema.

### E2E (Playwright)

- Jornada nova `e2e/aftersales-cancellation.spec.ts`: (1) usuário **OPERATIONS** (`ops`) entra por OIDC →
  abre **Pós-venda** pela nav (item gated em `ROLE_OPERATIONS`) → vê o **estado vazio** da lista no DB
  efêmero → abre **Cancelamento** → consulta uma política inexistente e vê o **estado de erro/permissão**
  do `<app-screen-state>`; (2) via API, token **sem ROLE_FINANCE** (`ops`) recebe **403** no fechamento de
  período (autoridade no backend) e a leitura de `/api/aftersales/cases` **não** é bloqueada.
- **Descoberta pelo Playwright:** ✅ `npx playwright test --list` → **15 testes em 8 arquivos** (13
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

- E2E não executada no sandbox (infra, acima). Fatias **16c–16d** entregam o restante das telas da
  Fase 16 (releases `0.26.0`…`0.27.0`).
