# DL-0101 — Isolamento do stack E2E (Postgres efêmero/tmpfs, portas 4201/8081, perfil dev p/ seed)

- **Fase:** 12
- **Spec(s):** SPEC-0028 (BR1, BR6, AC8)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A Fase 12 exige rodar o Playwright contra um **stack isolado e descartável** que **NUNCA** toca o banco
de desenvolvimento (requisito **inegociável**). Faltava desenhar: (a) como garantir que o banco do E2E é
efêmero e distinto do dev; (b) portas/rede/nome para rodar lado a lado sem colisão; (c) como o E2E
obtém usuários para logar; (d) como o teardown apaga os dados.

## Decisão

Um `compose.e2e.yaml` na raiz, **self-contained** (não lê `.env`), com três serviços e rede própria:

- **`postgres`** efêmero: `image postgres:16-alpine`, dados em **`tmpfs: /var/lib/postgresql/data`**
  (RAM, apagado quando o container para) — **sem volume nomeado** (o dev usa `db-data`). DB/usuário/
  senha próprios (`acme_e2e`/`acme`/`acme`).
- **`app`** (backend): build do `./backend`, `SPRING_PROFILES_ACTIVE=dev` para o **`DevUserSeeder`**
  semear os usuários de teste (um por papel, senha `dev12345` — perfil dev/test apenas, nunca produção,
  SPEC-0024 BR6), `DB_URL` apontando para o `postgres` efêmero. **Porta host 8081** (dev usa 8080).
- **`frontend`**: build do `./frontend` (novo `Dockerfile` Node→Nginx) servindo o bundle, com proxy
  same-origin `/api/` → `app:8080`. **Porta host 4201** (dev usa 4200).
- **Rede:** `acme-e2e` (isolada). **Nome do projeto:** `acme-e2e` (`name:` no compose) — namespacing
  distinto do projeto default do `docker-compose.yml`.
- **Scripts** (em `frontend/package.json`): `e2e:up` (`docker compose -f ../compose.e2e.yaml up -d
  --build`), `e2e:down` (`... down -v`, o **`-v`** remove qualquer volume — o `tmpfs` já some sozinho),
  `e2e` (`playwright test`).

**Prova de isolamento (AC8):**
1. **Serviço/rede/nome distintos:** o E2E sobe seu próprio `postgres` em `acme-e2e`; o `db` do
   `docker-compose.yml` **não é iniciado** por uma corrida de E2E.
2. **Portas distintas:** 4201/8081 (E2E) × 4200/8080/5432 (dev) → rodam lado a lado sem colisão.
3. **DB efêmero:** `tmpfs` em vez de volume; ao `e2e:down` o container some e os dados evaporam — não há
   persistência que pudesse vazar para o dev.
4. **Mesmas migrações:** o banco efêmero roda **as mesmas migrações Flyway** do dev (validação de
   schema idêntica), mas em dados próprios e descartáveis. Nenhuma migração nova (Regra Zero).

## Justificativa

- **Recomendação/fonte:** espelha diretamente o `compose.e2e.yaml` do **fkerp-poc** (Postgres efêmero
  em `tmpfs`, frontend 4201, scripts `e2e:up`/`e2e:down`, `baseURL` via `E2E_BASE_URL`), que é a
  referência citada pelo ROADMAP (Fase 12 "do fkerp-poc"). `delivery.md`: "Docker Compose when external
  services are needed; reproducible local env". `testing.md`: "separate unit, integration and E2E
  commands; tests create their own data".
- **Perfil `dev` para seed:** sem usuário semeado o login do E2E não teria credencial; o `DevUserSeeder`
  já existe e só roda em `dev`/`test` (nunca produção). É o caminho mínimo e seguro.

## Alternativas descartadas

- **Reusar o `docker-compose.yml` de dev:** viola o requisito inegociável (E2E tocaria o banco de dev).
- **Postgres com volume nomeado próprio:** isola do dev, mas deixa lixo em disco entre corridas; `tmpfs`
  é mais limpo e descartável por construção (alinhado à POC).
- **Testcontainers para o E2E:** já cobre o nível de **integração** no backend; para E2E de navegador o
  app precisa estar servido de ponta a ponta (Nginx + proxy), o que o compose entrega melhor.
- **Mesma porta do dev (4200/8080):** colidiria com um dev rodando; portas próprias permitem rodar os
  dois ao mesmo tempo.

## Impacto

- **Specs:** SPEC-0028 (BR1/BR6/AC8).
- **Arquivos:** `compose.e2e.yaml` (raiz), `frontend/Dockerfile` (novo), `frontend/nginx.conf` (novo),
  `frontend/package.json` (scripts `e2e`/`e2e:up`/`e2e:down`).
- **Migrações/Contratos:** nenhum (o DB efêmero roda as migrações existentes; sem schema/OpenAPI novos).
- **Segurança:** perfil `dev` no E2E é intencional (seed); secrets do E2E são valores de teste
  inseguros e explícitos (nunca produção).

## Como reverter

Remover `compose.e2e.yaml` e os scripts `e2e:*`. O Dockerfile/nginx do frontend ficam úteis para
qualquer empacotamento. Refactoring nulo — é infraestrutura de teste isolada.
