# 0032 - Exchange: Hedge cambial com contratos a termo (forwards)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Estende o `Exchange` (SPEC-0003 taxa congelada,
> SPEC-0011 exposição/subsídio×drift) com o instrumento de tesouraria que o setor usa para
> **travar taxa futura**: o contrato a termo (forward). Fase 19h do refactoring de maturidade.

## Goal

Dar à tesouraria o **hedge** da exposição que o livro acumula: registrar forwards manualmente,
casá-los com as posições abertas por moeda e fazer o **alerta de drift (DL-0027) olhar apenas o
descoberto** — um livro totalmente coberto não alerta, porque a ponta coberta está travada.

## Scope

**Em escopo:** agregado `ForwardContract` (registro manual, liquidação à taxa efetiva,
cancelamento); **cobertura** por moeda no `LiveExposure` (forwards OPEN abatem a fração
descoberta); o alerta de drift recalibrado sobre a base descoberta; resultado realizado da
liquidação (`settlementResultBrl`); tela do FX desk estendida (livro de forwards + exposição
descoberta).

**Fora de escopo:** integração bancária (registro é manual); IOF/spread bancário como custo de
liquidação (seam da DL-0049 — campo futuro); o insight prescritivo `HedgeAdvisor` foi
**adiado para a Fase 20c** (DSS real) — ver DL-0130.

## Business Context

A Acme vende em BRL hoje e paga o fornecedor em moeda estrangeira depois (OVERVIEW 7.2). A
SPEC-0011 mede o risco (subsídio × drift); esta spec dá o **instrumento para neutralizá-lo**: o
diretor/financeiro contrata com o banco um forward que trava a taxa de compra futura. Enquanto o
forward está aberto, aquela parcela do livro não sofre drift econômico — o alerta que dispara
sobre ela seria ruído.

## Business Rules

```txt
BR1  ForwardContract { currency ISO-4217 (3 letras), notional >0 (escala 2), contractRate >0
     (escala 6), tradeDate, maturityDate > tradeDate, counterparty não-vazia, status } com
     proveniência (createdBy/resolvedBy). Registro manual — sem integração bancária.
BR2  Status é máquina de estado OPEN → SETTLED | CANCELLED (permanece enum — critério da Fase 18).
     Liquidar/cancelar um forward não-OPEN → 409.
BR3  Liquidação registra a settledRate (>0) e o resultado realizado
     settlementResultBrl = (settledRate − contractRate) × notional (escala 2, HALF_UP).
     Positivo = o hedge compensou (travou mais barato que o mercado na liquidação).
BR4  Cobertura por moeda: para cada moeda com posições OPEN,
     uncoveredFraction = max(foreignAberto − Σ notional dos forwards OPEN da moeda, 0) / foreignAberto;
     unhedgedExposureBase = Σ (baseBrlNoFreeze da moeda × uncoveredFraction). Escala 8 na fração.
BR5  REVISA a DL-0027 (ver DL-0130): o limiar do alerta de drift = 2% da base DESCOBERTA
     (unhedgedExposureBase), não mais da exposição total. alerta = |drift| > limiar E descoberto > 0.
     Livro 100% coberto nunca alerta. Continua alerta — não bloqueia.
BR6  LiveExposure ganha openForwards (contagem) e unhedgedExposureBase (BRL). Projeção — não muda
     posições nem forwards.
BR7  Escritas de forwards exigem papel DIRECTOR ou FINANCE (matriz 19a); leitura é autenticada.
```

## Input/Output Examples

```http
POST /api/exchange/forwards
{ "currency":"USD", "notional":"2000.00", "contractRate":"5.500000",
  "tradeDate":"2026-07-01", "maturityDate":"2026-09-30", "counterparty":"Banco Alfa" }
201 Created  → { "id":"…", "status":"OPEN", … }

POST /api/exchange/forwards/{id}/settle
{ "effectiveRate":"5.700000" }
200 OK → { "status":"SETTLED", "settledRate":5.700000, "settlementResultBrl":"400.00", … }

GET /api/exchange/exposure          # com o livro USD 2000 totalmente coberto
200 OK → { …, "openForwards":1, "unhedgedExposureBase":{"amount":"0.00","currency":"BRL"},
           "driftThreshold":{"amount":"0.00","currency":"BRL"}, "driftAlert":false }
```

## API Contracts

- `POST /api/exchange/forwards` → 201 | 400 (`exchange.forward.invalid`) — papéis DIRECTOR/FINANCE.
- `POST /api/exchange/forwards/{id}/settle` `{effectiveRate}` → 200 | 400 | 404 | 409.
- `POST /api/exchange/forwards/{id}/cancel` → 200 | 404 | 409.
- `GET /api/exchange/forwards?status=` → 200 (lista, vencimento asc; filtro opcional).
- `GET /api/exchange/exposure` — resposta estendida com `openForwards` e `unhedgedExposureBase`.
- OpenAPI/snapshot atualizados.

## Events

Nenhum evento novo. `BookPositionDrifted` (SPEC-0011) continua sendo publicado — agora só quando o
drift cruza o limiar **descoberto** (BR5).

## Persistence Changes

```txt
V40__create_fx_forward_contracts.sql
  fx_forward_contracts(
    id uuid PK, currency varchar(3) not null, notional numeric(18,2) not null,
    contract_rate numeric(18,6) not null, trade_date date not null, maturity_date date not null,
    counterparty varchar not null, status varchar not null,   -- OPEN | SETTLED | CANCELLED
    settled_rate numeric(18,6) null, settled_at timestamptz null, cancelled_at timestamptz null,
    created_at, updated_at timestamptz not null, created_by varchar, resolved_by varchar,
    version bigint not null,
    INDEX ix_fx_forward_contracts_status (status)
  )
```

## Validation Rules

- Domain: invariantes do BR1 no factory `register` (400); `settle`/`cancel` exigem OPEN (409);
  `effectiveRate > 0` (400).
- Application: `RegisterForwardRequest` com Bean Validation espelhando o BR1.

## Error Behavior

`exchange.forward.invalid` → 400; `exchange.forward.not-found` → 404;
`exchange.forward.not-open` → 409. i18n em `messages(.pt_BR).properties`; entradas no
`HttpErrorMapping`.

## Observability Requirements

- Log de negócio no registro/liquidação/cancelamento (id, moeda, nocional, taxas, ator).
  O `BookPositionDrifted` já loga o cruzamento com o novo limiar.

## Tests Required

- **Integração (Testcontainers):** ciclo de vida completo (registrar → listar → liquidar com
  resultado BR3; cancelar; 409 em dupla resolução; 400 nos invariantes BR1).
- **Cobertura (BR4/BR5):** hedge total silencia o alerta (`unhedgedExposureBase` → 0.00);
  hedge parcial reduz o limiar proporcionalmente e mantém o alerta quando o drift ainda cruza.
- **Matriz 19a:** completude da autorização inclui as rotas de forwards.

## Acceptance Criteria

- Livro USD 2000 exposto com drift acima de 2%: sem forward → alerta ON; forward USD 2000 →
  `unhedgedExposureBase 0.00` e alerta OFF; forward USD 1000 → limiar cai à metade da base
  descoberta e o alerta permanece ON se o drift continuar acima.
- Liquidação a 5,70 de um forward travado a 5,50 × USD 2000 → `settlementResultBrl 400.00`.
- FX desk mostra o livro de forwards e a exposição descoberta; `./mvnw verify` + gates do
  frontend verdes.

## Open Questions

- **IOF/spread bancário** como custo de liquidação do forward (seam da DL-0049) — aguarda o
  fluxo bancário real do cliente (checklist 19l).
- **`HedgeAdvisor` (DSS)** — adiado para a Fase 20c; será construído sobre os modelos reais de
  DSS (ver DL-0130).

## Out of Scope

Integração bancária, hedge automatizado, NDF vs forward deliverable (registro manual não
distingue — decisão barata quando o fluxo bancário existir).
