# Caderno de testes — Slice 12-3 (Stack E2E isolado + prova de isolamento)

## Escopo

- **Spec:** SPEC-0028 (BR1, BR6, AC8; base para AC3–AC9).
- **Entrega:** `compose.e2e.yaml` (Postgres efêmero/tmpfs, backend 8081, frontend 4201), `Dockerfile` +
  `nginx.conf` do frontend (Node→Nginx, proxy `/api`→`app:8080`), `playwright.config.ts` (chromium,
  headless, baseURL via `E2E_BASE_URL`), scripts `e2e`/`e2e:up`/`e2e:down`, `.dockerignore`, e o
  **smoke E2E**. Prova de que o stack sobe e que o **banco de dev fica intacto** (DL-0101).

## Casos de teste por tipo

### Smoke (E2E)

| Caso | O que verifica | AC / regra |
|---|---|---|
| `smoke.spec.ts` — login screen serve na 4201 | a SPA carrega (brand + campos de login) | AC8/base |
| `smoke.spec.ts` — health via proxy | `GET /api/system/health` → `{status:UP, db:UP}` pelo proxy 4201 | smoke |
| `smoke.spec.ts` — `/api/version` via proxy | `GET /api/version` → `{version:"x.y.z"}` (público) | smoke |

### Isolamento (verificação manual documentada — AC8/BR1)

| Verificação | Resultado |
|---|---|
| Stack E2E sobe (projeto `acme-e2e`) | `acme-e2e-db-1` (healthy), `acme-e2e-app-1` (8081), `acme-e2e-frontend-1` (4201) — todos Up |
| DB do E2E **não exposto ao host** | `acme-e2e-db-1` mapeia só `5432/tcp` (interno); **sem** porta de host → inalcançável da 5432 do host |
| DB do E2E **efêmero (tmpfs, sem volume)** | `docker volume ls` não cria volume `acme-e2e*`; dado em tmpfs (RAM) |
| Dev DB **não iniciado** pela corrida | `erpdev-db-1`/`erpdev-app-1` permanecem `Exited` durante todo o ciclo up→test→down |
| Pós-`down -v` | containers/rede `acme-e2e` removidos; **`erpdev_db-data` intacto**, `erpdev-*` ainda `Exited` |

## Resultado

- `docker compose -f compose.e2e.yaml up -d --build` → **COMPOSE_EXIT=0**; backend **UP em ~5s**
  (`{"status":"UP","db":"UP"}`); frontend HTTP **200** na 4201; `/api/version` via proxy 4201 →
  `{"version":"0.22.0",...}`.
- **Prova de isolamento (AC8):** durante e após a corrida, o dev (`erpdev`) permaneceu `Exited` e o
  volume `erpdev_db-data` intacto; o DB do E2E é tmpfs sem volume nem porta de host. `down -v` removeu
  tudo do `acme-e2e` sem tocar o dev.
- **Correção durante a fatia:** o `npm ci` do Dockerfile falhava com `ERESOLVE` (PrimeNG 21 declara
  peer Angular 21 enquanto o projeto usa Angular 22). Causa: o `.npmrc` (com `legacy-peer-deps=true`,
  DL-0090) não estava sendo copiado para a imagem. Corrigido (`COPY package*.json .npmrc ./`) +
  `.dockerignore`. Regressão evitada: build verde após o fix.

## Cobertura (o que NÃO está coberto e por quê)

- Smoke prova o **canal** (SPA + proxy + backend + DB efêmero). As **jornadas** de negócio/sad paths
  ficam na slice 12-4 (mesma config/stack).
- Isolamento é verificado por **inspeção de containers/volumes/portas** (determinístico), não por um
  teste automatizado — a garantia é estrutural (projeto/rede/porta/tmpfs distintos), não condicional.

## Como reproduzir

```bash
cd frontend && npm ci && npx playwright install chromium
npm run e2e:up                         # docker compose -f ../compose.e2e.yaml up -d --build
curl -s http://localhost:8081/api/system/health   # {"status":"UP","db":"UP"}
curl -s http://localhost:4201/api/version          # {"version":"..."}
npm run e2e                            # roda o Playwright headless na 4201
npm run e2e:down                       # down -v (remove tudo; dev DB intacto)
# prova de isolamento:
docker ps -a --format '{{.Names}} {{.Status}}' | grep erpdev   # permanece Exited
docker volume ls | grep erpdev_db-data                          # intacto
```
