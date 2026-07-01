# Caderno de testes — Fase 16c (Telas de operação: Inteligência/DSS, Política comercial, Marketing, Portfólio)

- **Spec:** SPEC-0029 (estendida) · **Decisão de origem:** DL-0109 · **Release:** 0.26.0 · **Data:** 2026-07-01
- **Escopo:** frontend-only — 4 telas de operação (Intelligence/CommercialPolicy/Marketing/Portfolio) sobre
  APIs que já existiam (`/api/intelligence`, `/api/commercial-policy`, `/api/marketing`, `/api/portfolio`).
  Nenhum endpoint novo, nenhum contrato/schema/migração. Bump de versão do backend/OpenAPI para 0.26.0.

## Resultado global

✅ **Verde** nos portões executáveis no sandbox. A jornada Playwright de 16c foi **autorada e compila**,
mas **não foi executada** aqui por limitação de infra (ver "E2E").

## Casos por nível

### Unitário / componente (frontend, Vitest + jsdom)

Um spec por tela, cobrindo os estados exigidos (loading→success, empty onde há lista, error/permissão) e
os caminhos de ação, mais um spec de serviço (`HttpTestingController`) por feature para os wrappers HTTP:

- `intelligence-page.spec.ts` — lista (loading→success), empty, error, **permissão (403 `access.denied`)**,
  registrar decisão na seleção, **erro de decisão por código (`intelligence.decision.invalid`)**,
  severidades de status/veredito, selecionar limpa erro, guarda de decidir sem seleção.
- `commercial-policy-page.spec.ts` — lista de regras (loading→success), empty, error, resolver com
  proveniência, **permissão (403) ao emitir diretiva**, definir regra + reload + form sujo, severidades de
  camada, **erro de resolução por código (`policy.parameter.unknown`)**, emitir diretiva + reload.
- `marketing-page.spec.ts` — histórico idle/empty, lookup de consentimento (loading→success), **permissão
  (403)**, definir segmento + preview, disparar campanha (suprimidos por consentimento), apagamento LGPD,
  severidades de consentimento/campanha, revogar/conceder + reload, registrar+listar atribuição, erro de
  apagamento por código.
- `portfolio-page.spec.ts` — lista de marcas (loading→success), empty, error, **permissão (403)**,
  registrar marca + reload + form sujo, contratos + cobertura, definir meta + progresso (meta × realizado
  × atingimento), severidades de status, desativar marca + reload, registrar contrato, erro de meta por código.
- Serviços: `intelligence.service.spec.ts`, `commercial-policy.service.spec.ts`, `marketing.service.spec.ts`,
  `portfolio.service.spec.ts` — cada método HTTP (GET/POST/DELETE, query params) via `HttpTestingController`.

**Total frontend:** 196 testes Vitest, 37 arquivos, **0 falhas**.

**Cobertura (v8) — acima dos pisos da Fase 12** (stmts/lines ≥ 65, funcs ≥ 48, branches ≥ 55):

| Métrica | Medido | Piso |
|---|---|---|
| Statements | 70,8 % | 65 % |
| Branches | 56,6 % | 55 % |
| Functions | 50,1 % | 48 % |
| Lines | 76,1 % | 65 % |

### Lint + build (frontend)

- `npx ng lint` → **All files pass linting.**
- `npx ng build` → **sucesso**; chunks lazy emitidos: `intelligence-page`, `commercial-policy-page`,
  `marketing-page`, `portfolio-page`.

### Backend (regressão — inalterado)

- `cd backend && ./mvnw verify` → **BUILD SUCCESS** (exit 0); **Tests run: 476, Failures: 0, Errors: 0**
  no surefire; **0 violações de Checkstyle**; ArchUnit/Modulith/JaCoCo verdes. Única mudança de backend:
  string de versão `0.25.0 → 0.26.0` em `pom.xml` e o texto de descrição do `OpenApiConfig` — sem impacto
  de comportamento, contrato ou schema.

### E2E (Playwright)

- Jornada nova `e2e/intelligence-policy.spec.ts`: (1) usuário **DIRECTOR** (`director`) entra por OIDC →
  abre **Inteligência** por URL (a leitura é `authenticated()`) → vê o **estado vazio** da lista no DB
  efêmero → abre **Política comercial** pela nav (item gated em `ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN`) → vê o
  **explicador de precedência** e resolve um parâmetro (valor+proveniência ou estado de erro); (2) via API,
  token **sem ROLE_DIRECTOR** (`ops`) recebe **403** no endpoint de diretiva (autoridade no backend — BR5) e
  o **diretor é autorizado** (não 401/403).
- **Descoberta pelo Playwright:** ✅ `npx playwright test --list` → **17 testes em 9 arquivos** (15
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

- E2E não executada no sandbox (infra, acima). A fatia **16d** entrega o restante das telas da Fase 16
  (People/RH, Ponto, Assets, Admin, Platform/TI, Identity → release `0.27.0`).
