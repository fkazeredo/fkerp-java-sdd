# 0028 - Qualidade & E2E (Playwright em stack isolada + cobertura JaCoCo/Vitest + caminhos tristes + job de E2E no CI)

Status: Approved
Fase: 12 · **estendida na Fase 19i (QA hardening — BR8–BR11)**
Related ADRs: 0015 (SemVer — esta fase é PATCH: tooling de teste/CI/cobertura, sem mudar contrato
nem feature de produto), 0012 (camadas — testes e tooling vivem fora do domínio), 0014 (módulos)
Related DLs: DL-0099 (limiar de cobertura backend JaCoCo), DL-0100 (limiar de cobertura frontend
Vitest), DL-0101 (isolamento do stack E2E — Postgres efêmero, porta 4201, teardown limpa o volume),
DL-0102 (Playwright + jornadas/caminhos tristes + job de E2E no CI), **DL-0131 (concorrência:
lock do período no register/post + conflito otimista→409), DL-0132 (PIT + jqwik + pisos BRANCH)**

## Goal

Elevar a **qualidade verificável** do ERP ao padrão do projeto irmão **fkerp-poc**, **sem inventar
nenhum comportamento de negócio novo** (Regra Zero): só medir e proteger o que já existe.

1. **Cobertura como portão real** — relatório + **limiar que quebra o build** quando a cobertura cai
   abaixo da barra: **JaCoCo** no backend (no `./mvnw verify`) e **`@vitest/coverage-v8`** no frontend
   (no `ng test`). O limiar é fixado num nível **defensável que o código atual já passa** (não uma
   barra impossível), e a decisão fica registrada num DL.
2. **Playwright E2E nas jornadas críticas**, rodando contra um **stack isolado e descartável**
   (`compose.e2e.yaml`): app + **Postgres efêmero** (`tmpfs`, apagado quando o container para), em
   **portas próprias** (frontend **4201**, backend **8081**), distintas do `docker-compose.yml` de dev.
   A suíte de E2E **NUNCA** toca o banco de desenvolvimento — requisito **inegociável**, provado pelo
   desenho (serviço/porta/nome de banco distintos; o teardown remove o volume efêmero).
3. **Caminhos tristes sistemáticos** — não só o caminho feliz: cenários de **erro/permissão/vazio**
   (401 não autenticado, 403 sem papel, estados de lista vazia, validação de formulário, aviso de
   alterações não salvas).
4. **Job de E2E no CI** — em `.github/workflows/`, ao lado do `ci.yml` existente: sobe o stack isolado,
   roda o Playwright **headless**, derruba tudo — **sem nunca tocar dados de dev**.

## Scope

**Em escopo (tooling de teste/CI/cobertura — sem mudar regra de negócio, schema ou contrato):**

- **JaCoCo (backend)**: plugin `jacoco-maven-plugin` no `backend/pom.xml` — `prepare-agent`
  + `report` (HTML/CSV/XML em `target/site/jacoco`) + **`check`** com regra de cobertura **INSTRUCTION
  ≥ limiar** ligado na fase `verify`. O `check` **quebra o build** se a cobertura cair abaixo do limiar
  (DL-0099). Exclusões mínimas e justificadas (a classe `main` do Spring Boot, DTOs/records de request/
  response sem lógica, `package-info`, código gerado).
- **Vitest coverage (frontend)**: dependência `@vitest/coverage-v8`; **`vitest.config.ts`** (carregado
  pelo builder `@angular/build:unit-test` via `--runner-config`) com `coverage.enabled`,
  `provider: 'v8'`, reporters (`text-summary`, `html`, `lcov`) e **`coverage.thresholds`** (DL-0100). O
  builder respeita o limiar do arquivo de config → cobertura abaixo da barra **falha** `ng test`. Script
  `npm run test:coverage` e o `ng test` padrão do CI passam a rodar com cobertura.
- **`compose.e2e.yaml`** (raiz, espelha a POC): serviços `postgres` (efêmero, `tmpfs:
  /var/lib/postgresql/data`, **sem volume nomeado**), `backend` (perfil `dev` para o seed de usuários,
  porta host **8081**), `frontend` (Nginx servindo o build, porta host **4201**, proxy `/api` →
  backend). Rede própria `acme-e2e`. **Self-contained** (não precisa de `.env`). Roda lado a lado com o
  stack de dev sem colisão (portas/rede/nome distintos). (DL-0101)
- **Dockerfile do frontend** (novo) + **nginx.conf**: build multi-stage (Node → Nginx), proxy
  same-origin `/api/` → `backend:8080`, fallback SPA para `index.html`. Necessário para o stack E2E
  servir o frontend repaginado da Fase 10.
- **Playwright**: dependência `@playwright/test`; `playwright.config.ts` (`testDir: ./e2e`,
  `baseURL` via `E2E_BASE_URL` default `http://localhost:4201`, projeto `chromium`, headless por
  padrão, `forbidOnly` no CI, 1 retry para absorver flutuação de timing de infra — **sem** enfraquecer
  asserção). Scripts `e2e`, `e2e:up`, `e2e:down`. (DL-0102)
- **Specs de E2E** (jornadas críticas + caminhos tristes — ver *Acceptance Criteria*): login (feliz +
  inválido), versão na tela de login, navegação/shell + proteção de não-salvos, um fluxo central de
  negócio (cadastro de conta comercial), e um caminho de permissão (403 ao tentar ação sensível sem
  papel) / estado vazio.
- **Job de E2E no CI** (`.github/workflows/e2e.yml`): instala o Playwright, sobe o `compose.e2e.yaml`,
  espera a saúde, roda `npm run e2e` headless, e **sempre** derruba o stack (`if: always()`), anexando o
  relatório/trace como artefato.
- **`.gitignore`**: ignorar artefatos de cobertura e do Playwright (`coverage/`, `playwright-report/`,
  `test-results/`, binários de browser). **Nunca** versionar `node_modules` nem os browsers do
  Playwright.
- **Versão**: bump **PATCH** `0.22.0 → 0.22.1` no `backend/pom.xml` e na OpenAPI (ADR 0015 — tooling de
  teste/CI/cobertura, sem contrato/feature nova). Release note pt-BR + CHANGELOG en-US.

**Fora de escopo (Regra Zero — não antecipar):**

- **Novos comportamentos/telas/endpoints de negócio.** Esta fase **só testa o que já existe**; nenhuma
  rota, regra, migração ou contrato muda. Se uma jornada esbarrar em algo inexistente, ela cobre o que
  há (não se inventa feature para "ter teste").
- **Cobertura 100% / barra inflada.** O limiar é o **piso defensável** que o código atual já cumpre
  (gate de não-regressão), não uma meta cosmética (Filosofia de testes: cobertura é sinal, não meta).
  Subir a barra é trabalho incremental de fatias futuras, não desta.
- **E2E multi-browser** (Firefox/WebKit/mobile): só `chromium` no v1 (a POC faz o mesmo). Adicionar
  browsers é aditivo e fica para quando houver necessidade real.
- **Testes de carga/performance/segurança automatizados** (k6, ZAP): outra disciplina; não confundir com
  E2E funcional.
- **Mutation testing** (PIT) e **cobertura por branch agressiva**: melhoria futura; o gate v1 é
  INSTRUCTION (back) + statements/lines (front), o sinal mais estável para começar.
- **Migração Flyway**: nenhuma — o banco efêmero do E2E roda **as mesmas migrações** do dev.
- **Mudança no manual do usuário**: esta fase é **tooling interno de qualidade**; nada visível ao
  usuário muda (Regra Zero — o `MANUAL.md`/`MANUAL.en-US.md` ficam **inalterados** de propósito).

## Business Context

A qualidade de um ERP é parte do produto: regressões silenciosas custam confiança comercial e dinheiro.
Duas salvaguardas faltavam ao projeto, ambas presentes no fkerp-poc:

- **Cobertura medida e protegida**: sem um piso de cobertura no CI, nada impede um PR de baixar a
  qualidade do conjunto (remover testes, adicionar código sem teste). O JaCoCo/Vitest com `check`
  transformam "cobertura é boa prática" em "cobertura é portão".
- **E2E em stack isolado**: validar as jornadas críticas no navegador real, contra um app real, **sem
  arriscar o banco de desenvolvimento**. Misturar E2E com o banco de dev é um risco operacional clássico
  (dados de teste poluindo o dev, ou pior, um teardown apagando dados reais). O `compose.e2e.yaml` com
  Postgres efêmero remove esse risco por construção.

A separação espelha a `testing.md`: **comandos separados** para unit/integration/E2E; **realismo no
nível certo** (Testcontainers já cobre integração no backend; Playwright cobre a jornada de ponta a
ponta); **E2E só para fluxos críticos**.

## Business Rules

- **BR1 — Isolamento do banco (inegociável).** O stack de E2E usa um Postgres **efêmero próprio**
  (container/`tmpfs`/porta/nome de banco distintos do `docker-compose.yml`). Rodar o E2E **não pode**
  ler, escrever ou apagar o banco de dev. Provado por: serviço `postgres` em rede `acme-e2e`, **sem
  volume nomeado** (`tmpfs`), portas host **4201/8081** (dev usa 4200/8080/5432); `e2e:down` remove o
  container e o `tmpfs` some. ASSUMIDO (ver DL-0101).
- **BR2 — Cobertura é portão real.** O `./mvnw verify` falha se a cobertura de instruções do backend
  cair abaixo do limiar JaCoCo; o `ng test` (com a config de runner) falha se a cobertura de statements/
  lines/functions do frontend cair abaixo do limiar Vitest. O limiar é o **piso defensável** que o
  código **atual já passa** (não uma barra impossível). ASSUMIDO (ver DL-0099, DL-0100).
- **BR3 — Nenhum portão existente é enfraquecido.** Os 477 testes do backend e os 57 do frontend
  permanecem verdes; ArchUnit/Modulith/Spotless/Checkstyle seguem ligados e capazes de quebrar o build.
  Adicionar o gate de cobertura **não** afrouxa nenhum outro (CLAUDE.md invariante 5).
- **BR4 — Caminhos tristes obrigatórios.** A suíte de E2E cobre, além do caminho feliz, ao menos:
  **autenticação inválida** (login errado → erro genérico, sem revelar se o usuário existe), **acesso
  sem sessão** (rota protegida → redireciona ao login), **estado vazio** (lista sem dados → empty
  state), e **proteção de não-salvos** (cancelar formulário alterado → avisa). (ver SPEC-0024/0026)
- **BR5 — E2E headless e descartável no CI.** O job de CI roda o Playwright **headless**, e **sempre**
  derruba o stack ao final (`if: always()`), mesmo em falha. O `forbidOnly` impede um `.only` esquecido
  de mascarar a suíte. ASSUMIDO (ver DL-0102).
- **BR6 — Seed de usuários no E2E.** O backend do stack E2E roda com o perfil `dev` para que o
  `DevUserSeeder` crie os usuários de teste (um por papel, senha `dev12345`) — os mesmos do dev local.
  Sem isso o login do E2E não teria credencial. É perfil **dev/test apenas**, nunca produção (SPEC-0024
  BR6). ASSUMIDO (ver DL-0101).
- **BR7 — Versão (PATCH).** A entrega é tooling de teste/CI/cobertura, sem mudar contrato nem feature de
  produto → **PATCH** `0.22.1` (ADR 0015). A OpenAPI acompanha a versão do pom.

### Business Rules — Fase 19i (QA hardening)

- **BR8 — Mutation testing como portão (gradua o "Future" desta spec).** Profile Maven `mutation`
  roda o **PIT** sobre as classes de matemática de dinheiro (`domain.money`, `InstallmentPlan`/
  `Payout`/`PayoutInstallment`, `domain.commissioning`, `FxPosition`/`ForwardContract`/
  `CurrencyPair`, `LedgerEntry`/`AccountingPeriod`) usando **só os testes unitários/propriedade
  rápidos** (sem Spring/Testcontainers por mutante). `mutationThreshold=60` **quebra a execução**
  abaixo do piso; **job próprio no CI** (`ci.yml` → `mutation`) com relatório como artefato.
  Cobertura diz que a linha rodou; mutação diz que as asserções percebem quando ela erra.
  ASSUMIDO (ver DL-0132; primeira medição: 185 mutantes, 126 mortos = 68%, test strength 89%).
- **BR9 — Conflito otimista é 409, não 500.** Uma escrita que perde a corrida do `@Version`
  (`OptimisticLockingFailureException`) responde **409 `error.conflict`** (i18n pt/en) pelo
  handler global — o cliente recarrega e tenta de novo. Coberto por teste do handler.
- **BR10 — Invariantes de concorrência testados de verdade.** (a) N threads disputando
  `beginInstallmentExecution` do payout começam **exatamente uma** execução (lock pessimista;
  perdedores recebem a rejeição de domínio); (b) um `register`/`postFromCharge` correndo contra o
  fechamento do período **serializa no lock da linha do período** — ou entra antes do selo, ou
  relê CLOSED e é rejeitado (SPEC-0015 BR4); a regressão provou o furo antes do lock (entrada
  escorregava para o período selado) e o fix fecha (teste vermelho→verde, DL-0131).
- **BR11 — Blindagem de timezone.** O período contábil deriva de `occurredAt` **em UTC** (nunca do
  fuso default da JVM — provado com default São Paulo e Moscou nos limites de mês) e a janela de
  SLA de 72h é aritmética exata de instantes (vira no segundo seguinte ao deadline, sob qualquer
  fuso default). Propriedades de `Money` (normalização escala 2 HALF_UP, add/subtract inversos,
  identidade do zero, moedas não misturam) e da distribuição de centavos do `InstallmentPlan`
  (soma exata ao centavo para qualquer total×parcelas) verificadas por **jqwik** (1000 casos por
  propriedade).
- **BR12 — Pisos elevados.** JaCoCo ganha piso de **BRANCH ≥ 0,65** (medido 0,689) ao lado do de
  INSTRUCTION; Vitest sobe para statements 70 / lines 75 / functions 49 (branches permanece 55 —
  folga real de 0,67pp; subir seria barra cosmética). Pisos continuam sendo o não-regresso
  defensável que o código atual passa (BR2).

## Acceptance Criteria (jornadas E2E + portões de cobertura)

> Cada AC de E2E é um spec do Playwright; cada AC de cobertura é uma regra de `check` que quebra o build.

**Cobertura (portões):**

- **AC1 — JaCoCo backend gate.** `cd backend && ./mvnw verify` gera o relatório JaCoCo e **falha** se a
  cobertura de instruções ficar abaixo do limiar (DL-0099). Com o código atual, **passa**. Plantar uma
  regra com limiar acima da cobertura atual faz o build falhar (prova de que é gate de verdade).
- **AC2 — Vitest frontend gate.** `cd frontend && npm run test:coverage` (e o `ng test` do CI com a
  config de runner) gera o relatório de cobertura e **falha** se statements/lines/functions ficarem
  abaixo do limiar (DL-0100). Com o código atual, **passa**.

**E2E — jornadas críticas (feliz + triste), na 4201 com o banco de dev intacto:**

- **AC3 — Login feliz.** Em `/login`, com `director`/`dev12345`, o usuário entra e chega ao dashboard
  (jornada autenticada). A versão do app aparece na tela de login (`/api/version`).
- **AC4 — Login inválido (caminho triste).** Com credencial errada, a tela mostra um **erro genérico**
  e o usuário **permanece** em `/login` (não revela se o usuário existe — SPEC-0024 BR4).
- **AC5 — Rota protegida sem sessão (caminho triste).** Acessar uma rota autenticada sem login
  **redireciona** para `/login` (authGuard).
- **AC6 — Fluxo central de negócio.** Logado, o usuário navega ao módulo de **Contas** (Accounts), vê a
  lista (ou seu **empty state** quando vazia — caminho de borda), e a navegação do shell funciona
  (sidebar/rotas).
- **AC7 — Proteção de não-salvos (caminho triste).** Ao alterar um formulário e tentar sair/cancelar, o
  app **avisa** sobre alterações não salvas (`canDeactivate`), e "continuar editando" mantém o usuário na
  tela. (SPEC-0026)
- **AC8 — Isolamento provado.** Após uma corrida completa de E2E (`e2e:up` → `e2e` → `e2e:down`), o banco
  de dev (`docker-compose.yml`) está **intacto** (não foi sequer iniciado pela corrida de E2E; o E2E usa
  seu próprio Postgres efêmero). Documentado no test-report da fatia 12-3.

**CI:**

- **AC9 — Job de E2E no CI.** Existe um workflow em `.github/workflows/` que sobe o `compose.e2e.yaml`,
  roda o Playwright headless e **sempre** derruba o stack — sem tocar dados de dev. O `ci.yml` existente
  (backend/flyway/frontend) continua válido e agora roda também com os gates de cobertura.

## Tests Required

- **Backend**: os 477 testes existentes seguem verdes sob `./mvnw verify`; o JaCoCo `check` roda na fase
  `verify` e é capaz de quebrar o build (gate). Não há novo teste de domínio (Regra Zero — nada de
  negócio muda); a prova do gate é o próprio `check` + o relatório.
- **Frontend (Vitest)**: os 57 testes existentes seguem verdes; a cobertura é coletada e o limiar é
  capaz de quebrar `ng test` (gate).
- **E2E (Playwright)**: as specs dos AC3–AC7 verdes na 4201, headless, contra o stack isolado; o AC8
  (isolamento) é verificado e registrado no test-report.
- **Smoke**: o stack E2E sobe e `GET /api/system/health` responde UP antes de o Playwright rodar
  (healthcheck do compose + espera no job de CI).

## Open Questions

(nenhuma em aberto — as decisões autônomas estão em DL-0099..DL-0102; todas as Open Questions de
negócio que esta fase tocaria já estão fechadas nas specs das fases anteriores.)

## Out of Scope / Future

- Multi-browser, mobile viewport, visual regression (Playwright screenshots comparados).
- ~~Mutation testing (PIT) e elevação incremental do limiar de cobertura.~~ → **GRADUADO na Fase
  19i** (BR8/BR12, DL-0132).
- Testes de carga/performance/segurança automatizados.
- Tracing distribuído nos testes (não há saltos de rede num monólito modular).
