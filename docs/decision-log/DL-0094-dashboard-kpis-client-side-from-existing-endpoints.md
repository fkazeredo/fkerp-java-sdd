# DL-0094 — Dashboard com KPIs calculados no cliente a partir dos endpoints de lista existentes

- **Fase:** 10 (UX & Frontend profissional)
- **Spec(s):** SPEC-0026
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O ROADMAP pede "dashboard com KPIs". A tarefa manda **preferir frontend-only** e só criar endpoint
de leitura novo se for inevitável. Falta decidir **quais KPIs** e **de onde vêm os dados** — agregar
no cliente a partir do que já existe, ou criar um read-model no backend.

## Decisão

KPIs **calculados no cliente** a partir dos endpoints de **lista já existentes**, sem nenhum endpoint
novo:

- **Contas** — total e quebra por status (`GET /api/accounts`).
- **Reservas** — total e quebra por status, com destaque de `PENDING`/`CONFIRMED`
  (`GET /api/bookings`).
- **Conciliação** — nº de casos, casos em `OPEN`/`DISCREPANCY` e soma do `expectedSpread`
  (`GET /api/reconciliation`).
- **Câmbio** — taxa congelada vigente do par padrão (`GET /api/exchange/pinned-rates/current`).

Cada cartão de KPI tem estados **loading/empty/error** e respeita o papel (um KPI cujo endpoint o
papel não acessa mostra estado de permissão, não quebra o painel). O dashboard vira a rota raiz
(`''`) protegida; a navegação antiga `''→accounts` passa a `''→dashboard`.

## Justificativa

- **Regra Zero + "preferir frontend-only" (tarefa):** os dados de KPI já são servidos pelas listas
  do núcleo; agregá-los no cliente entrega o valor sem schema, sem migração e sem novo contrato —
  mantendo os 468 testes do backend intactos por construção.
- **Sem FK cross-contexto / read-model prematuro:** criar um endpoint de dashboard agregando vários
  contextos seria um read-model cross-módulo cuja necessidade (volume/performance) ainda não existe;
  o ROADMAP reserva read-models de Intelligence para a Fase 7+ e observabilidade para a Fase 11.
- **Reversível barata:** se um KPI exigir agregação pesada no futuro, troca-se a fonte daquele cartão
  por um endpoint dedicado sem mexer no resto do painel.

## Alternativas descartadas

- **Endpoint `GET /api/dashboard/kpis` novo:** adiciona contrato + DTO + i18n + testes no backend para
  dados que o cliente já consegue compor; contra "preferir frontend-only" e Regra Zero. Fica como
  evolução natural se a agregação client-side não escalar.
- **Sem dashboard (só navegação):** o ROADMAP exige "dashboard com KPIs" no aceite.

## Impacto

- **Arquivos:** `features/dashboard/*` (página + serviço que orquestra os services de feature já
  existentes), `app.routes.ts` (rota raiz → dashboard), i18n. **Backend:** nenhum.
- **Migração/Contrato/OpenAPI:** nenhum.

## Como reverter

Remover a feature `dashboard` e reapontar a rota raiz. Barata. Migrar um KPID para endpoint dedicado
no futuro é trocar a fonte de um cartão — localizado.
