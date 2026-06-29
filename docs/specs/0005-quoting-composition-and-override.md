# 0005 - Quoting · Composição da cotação, sugestão e override

Status: Approved
Related ADRs: 0011, 0012, 0014

## Goal

Compor o **preço sugerido** de uma venda **manual** a partir de preço-base + câmbio congelado +
comissão de duas pontas + markup; persistir a cotação com `suggestedAmount` × `appliedAmount`; e
registrar um `OverrideRecord {quem, quando, de→para, motivo}` sempre que o humano divergir da
sugestão — tudo com **proveniência** congelada na composição. Esta é a fatia que materializa a tese
do redesenho: *o sistema sempre calcula e sugere; o humano pode divergir, mas a divergência fica
registrada contra a sugestão*.

## Scope

**Em escopo:** compor cotação `priceOrigin = MANUAL`; aplicar override com motivo obrigatório;
consultar a cotação com seu histórico de overrides. Consome as fachadas de `Accounts`, `Exchange`
e `Commissioning`, e um `MarkupProvider` **stub** de `CommercialPolicy`. Tela mínima: formulário de
composição mostrando sugerido vs. aplicado + ação de override com motivo.

**Fora de escopo:** origem `INTEGRATED` (SPEC-0009); ciclo de vida completo da reserva (SPEC-0006);
**motor de precedência** de `CommercialPolicy` (Diretiva > Promoção > Contrato > Política > Padrão) —
adiado; promoções; múltiplas moedas além do par configurado; tributos; lançamento em Finance.

## Business Context

A Acme Travel compõe a cotação a partir de um **preço-base externo/manual**; não precifica o produto
(redesenho Parte 4.1). Sobre a base, aplica câmbio + comissão + markup e **sugere** um número. A
fronteira do produto é aberta; a do dinheiro é **governada, não travada** (Parte 4.2): o humano pode
divergir, com rastro.

## Convenção de composição (v1) — **a confirmar com o dono** (ver Open Questions)

```txt
Entrada: accountId, basePrice (Money em moeda do fornecedor, ex. USD), currencyPair (ex. USD/BRL),
         supplierCommissionPct, agentCommissionPct, validUntil?

1. rate        = ExchangeRateProvider.currentRate(currencyPair)   // ausente => quoting.rate.missing
2. baseBrl     = basePrice.amount × rate.rate            (scale 2, HALF_UP)
3. commission  = CommissionCalculator.compute(base=baseBrl, supplierPct, agentPct)  // SPEC-0004
4. markup      = MarkupProvider.markup(...)              // stub: % padrão, source = SYSTEM_DEFAULT
   markupAmount= baseBrl × markup.pct                    (scale 2, HALF_UP)
5. suggestedAmount (BRL) = baseBrl + markupAmount        // preço de venda sugerido
6. appliedAmount = suggestedAmount                       // até haver override
```

> A comissão de duas pontas (fornecedor a receber / agente a pagar / spread) é a **decomposição
> financeira** que viaja junto da cotação para accrual futuro; o **preço de venda** é
> `baseBrl + markup`. Se a interpretação econômica correta for outra (ex. agência paga a tarifa e a
> margem da Acme é só o spread, sem markup), isso é decisão de negócio — ver Open Questions; **não
> inventar** (`CLAUDE.md`, invariante 3).

## Business Rules

```txt
BR1  priceOrigin = MANUAL nesta fatia.
BR2  Compor exige accountId existente (via fachada Accounts). Inexistente => 404 quoting.account.not-found.
BR3  Compor exige taxa vigente para o par. Ausente => 422 quoting.rate.missing.
BR4  A cotação MUST congelar a proveniência da composição: basePrice, rate (id+valor), supplierPct,
     agentPct, markup (pct+source), baseBrl, as 3 comissões, spread, suggestedAmount. Mudanças
     futuras de taxa/política NÃO alteram cotações já compostas.
BR5  suggestedAmount é IMUTÁVEL após a composição (é o registro do que o sistema aconselhou).
BR6  Override: o humano MAY definir appliedAmount ≠ valor atual, informando motivo não-vazio.
     Motivo vazio => 400 quoting.override.reason-required.
     Cada override cria um OverrideRecord {fromAmount(=appliedAmount atual), toAmount, reason,
     performedBy, performedAt} e atualiza appliedAmount. Vários overrides são permitidos (encadeados).
BR7  A moeda do appliedAmount MUST ser a mesma do suggestedAmount (BRL). Senão 400
     quoting.override.currency-mismatch.
BR8  Divergência = (appliedAmount ≠ suggestedAmount); sempre rastreável pelos OverrideRecords.
```

## Input/Output Examples

```http
POST /api/quotes
{ "accountId": "8f1c...", "basePrice": {"amount": 500.00, "currency": "USD"},
  "currencyPair": "USD/BRL", "supplierCommissionPct": 0.15, "agentCommissionPct": 0.10,
  "validUntil": "2026-07-03T23:59:59Z" }
201 Created
{ "id": "q-001", "accountId": "8f1c...", "priceOrigin": "MANUAL",
  "basePrice": {"amount":500.00,"currency":"USD"},
  "fxRate": 5.400000, "baseAmountConverted": {"amount":2700.00,"currency":"BRL"},
  "commission": { "supplier": {"amount":405.00,"currency":"BRL"},
                  "agent": {"amount":270.00,"currency":"BRL"},
                  "spread": {"amount":135.00,"currency":"BRL"}, "spreadNegative": false },
  "markup": { "pct": 0.00, "amount": {"amount":0.00,"currency":"BRL"}, "source": "SYSTEM_DEFAULT" },
  "suggestedAmount": {"amount":2700.00,"currency":"BRL"},
  "appliedAmount":   {"amount":2700.00,"currency":"BRL"},
  "status": "COMPOSED", "validUntil": "2026-07-03T23:59:59Z",
  "provenance": { "rateId": "a12...", "policySource": "SYSTEM_DEFAULT" },
  "overrides": [] }
```

```http
POST /api/quotes/q-001/override
{ "appliedAmount": {"amount": 2650.00, "currency": "BRL"}, "reason": "fechamento com cliente recorrente" }
200 OK
{ ...quote..., "appliedAmount": {"amount":2650.00,"currency":"BRL"},
  "overrides": [ { "fromAmount":{"amount":2700.00,"currency":"BRL"},
                   "toAmount":{"amount":2650.00,"currency":"BRL"},
                   "reason":"fechamento com cliente recorrente",
                   "performedBy":"operador1", "performedAt":"2026-06-26T12:10:00Z" } ] }

POST /api/quotes/q-001/override   (sem motivo)
400 Bad Request
{ "code": "quoting.override.reason-required", "message": "Motivo do override é obrigatório.", "fields": ["reason"] }

POST /api/quotes   (sem taxa vigente p/ o par)
422 Unprocessable Entity
{ "code": "quoting.rate.missing", "message": "Não há taxa de câmbio vigente para compor a cotação.", "fields": [] }
```

## API Contracts

- `POST /api/quotes` — compõe (body acima) → 201 | 404 `quoting.account.not-found` |
  422 `quoting.rate.missing`.
- `POST /api/quotes/{id}/override` — `{appliedAmount, reason}` → 200 | 400
  `quoting.override.reason-required` | 400 `quoting.override.currency-mismatch` | 404 `quoting.not-found`.
- `GET /api/quotes/{id}` → 200 (inclui `overrides[]`) | 404 `quoting.not-found`.
- OpenAPI atualizada. `priceOrigin`, `policySource`, `status` com valores externos explícitos.

## Events

- `QuoteComposed` — `{quoteId, accountId, suggestedAmount, occurredAt}`. Produtor: `quoting`.
- `PriceOverridden` — `{quoteId, fromAmount, toAmount, reason, performedBy, occurredAt}`.
  Produtor: `quoting`.
- Consumidores: nenhum ainda (futuro: Intelligence — `OverrideNudge`; AfterSales). **Internos
  in-process** por ora; viram contrato estável/outbox quando outro módulo/serviço consumir.

## Persistence Changes

```txt
V4__create_quotes.sql
  quotes(
    id uuid PK,
    account_id uuid not null,                 -- valor de referência; SEM FK cross-módulo (ver nota)
    price_origin varchar not null,            -- MANUAL
    base_price_amount numeric(18,2) not null, base_price_currency varchar not null,
    currency_pair varchar not null,
    fx_rate numeric(18,6) not null, rate_id uuid not null,
    base_converted_amount numeric(18,2) not null,   -- BRL
    supplier_pct numeric(7,6) not null, agent_pct numeric(7,6) not null,
    supplier_commission numeric(18,2) not null, agent_commission numeric(18,2) not null,
    spread numeric(18,2) not null, spread_negative boolean not null,
    markup_pct numeric(7,6) not null, markup_amount numeric(18,2) not null, markup_source varchar not null,
    suggested_amount numeric(18,2) not null,   -- BRL, imutável
    applied_amount numeric(18,2) not null,     -- BRL, muda via override
    status varchar not null, valid_until timestamptz null,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null,
    version bigint not null
  )
  override_records(
    id uuid PK, quote_id uuid not null REFERENCES quotes(id),
    from_amount numeric(18,2) not null, to_amount numeric(18,2) not null,
    reason varchar not null, performed_by varchar not null, performed_at timestamptz not null
  )
  INDEX ix_overrides_quote (quote_id)
```

> **Nota de fronteira (modular monolith).** `account_id` é guardado como valor e **validado pela
> fachada de Accounts** na composição; **não** criamos FK cross-módulo, para preservar extração
> futura e respeitar a posse de dados (`modules-and-apis.md`). `@Version` na cotação.

## Validation Rules

- Delivery: amounts, pct, moeda, presença.
- Application (preconditions): conta existe (fachada Accounts); taxa existe (fachada Exchange).
- Domain (invariantes): motivo de override não-vazio (BR6); moeda do applied = BRL (BR7);
  `suggestedAmount` imutável (BR5); proveniência congelada (BR4).
- Persistence: `override_records.quote_id` referencia `quotes`; constraints de não-nulos.

## Error Behavior

- `quoting.account.not-found` → 404; `quoting.rate.missing` → 422;
  `quoting.override.reason-required` → 400; `quoting.override.currency-mismatch` → 400;
  `quoting.not-found` → 404. Chaves i18n pt-BR. `HttpErrorMapping` (infra.web) faz o mapa (ADR 0011).

## Observability Requirements

- Logar `QuoteComposed` e `PriceOverridden` como eventos de negócio; **auditar override**
  (estilo `ManualOverridePerformed`, `delivery.md`). Correlation id.
- Métricas: `quotes_composed_total`, `price_overrides_total`.

## Tests Required

- **Unit:** matemática da composição (conversão FX, comissão, markup, sugerido) com arredondamento;
  override cria record, atualiza applied, exige motivo, exige moeda BRL; overrides encadeados
  (from→to corretos).
- **Integração (Testcontainers):** happy path do carro em Orlando (USD 500, taxa 5,40, 15%/10%);
  taxa ausente → 422; conta inexistente → 404; override feliz + motivo ausente 400 + records
  persistidos; GET retorna overrides.
- **Regressão:** (a) rastreabilidade do override — quando applied diverge, existe `OverrideRecord`;
  (b) **imutabilidade da proveniência** — fixar nova taxa em Exchange após compor **não** muda a
  cotação existente.
- **Arquitetura:** `quoting` não acessa repositórios de `accounts`/`exchange`/`commissioning`
  (Spring Modulith verify) — colaboração só por fachada.

## Acceptance Criteria

- Compor a venda manual de carro em Orlando produz `suggestedAmount` em BRL com a decomposição de
  comissão e spread, tudo com proveniência.
- Aplicar override com motivo registra um `OverrideRecord` e atualiza `appliedAmount`; sem motivo →
  400.
- Sem taxa vigente → 422 `quoting.rate.missing`.
- Trocar a taxa congelada depois **não** altera uma cotação já composta.
- A tela mostra sugerido vs. aplicado e permite override com motivo.
- `mvnw verify` verde (ArchUnit/Modulith inclusos).

## Open Questions

- ~~**Fórmula de preço (a confirmar):** preço = `baseBrl + markup` ou só spread? Base comissionável
  em BRL ou USD?~~ → **ASSUMIDO** (2026-06-29): preço = `baseBRL + markup` (markup governado, default
  0); base comissionável em **BRL**. Margem primária = spread. Ver
  [DL-0009](../decision-log/DL-0009-quoting-formula-de-preco.md).
- **Q4** (faixas de override) e **Q5** (escopo da comissão do agente) — herdadas de SPEC-0004.
- **Motor de precedência** de `CommercialPolicy` (markup hoje é stub `SYSTEM_DEFAULT`) — spec futura
  harmoniza a costura (`simulation-and-mocking.md`).

## Out of Scope

Origem `INTEGRATED`, ciclo de vida da reserva, motor de precedência/promoções, múltiplas moedas,
tributos, accrual em Finance.
