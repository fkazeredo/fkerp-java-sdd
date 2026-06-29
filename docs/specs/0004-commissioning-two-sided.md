# 0004 - Commissioning · Comissão de duas pontas (% fixo)

Status: Approved
Related ADRs: 0012, 0014

## Goal

Calcular a **comissão de duas pontas** — a receber do fornecedor (override) e a pagar ao agente — e
o **spread** derivado, para uma venda manual, usando percentuais fixos. Capacidade consumida por
`Quoting` (SPEC-0005).

## Scope

**Em escopo:** cálculo puro `supplierCommission`, `agentCommission`, `spread` a partir de uma base
comissionável e dois percentuais; fachada de domínio `CommissionCalculator`; endpoint de **preview**
para a tela mostrar a decomposição ao agente. **Fatia sem tabela** (stateless).

**Fora de escopo:** faixas de override retroativas por volume (Q4); escopo da comissão do agente por
agência/produto/canal (Q5); exclusões de base comissionável por fornecedor (ex. Locadora
Internacional exclui extras); imposto (ISS) sobre a comissão (Billing, Fase 8); eventos de
accrual/reversal (disparam na confirmação da reserva — Booking/Reconciliation).

## Business Context

A Acme Travel vive da **diferença (spread)**: recebe comissão do fornecedor e paga comissão ao
agente (redesenho 3.2). "Override" é a comissão da ponta de cima (fornecedor → Acme). Imposto incide
só sobre a comissão (separar "dinheiro que passa" de "receita") — mas isso é Billing.

## Business Rules

```txt
BR1  Dado commissionableBase (Money), supplierCommissionPct e agentCommissionPct (decimais em [0,1]):
       supplierCommission = base × supplierPct        (a receber)
       agentCommission    = base × agentPct           (a pagar)
       spread             = supplierCommission − agentCommission
     Tudo na moeda da base, scale 2, HALF_UP.
BR2  pct MUST estar em [0,1]; senão 400 commissioning.pct.invalid.
     base.amount MUST ser >= 0; senão 400 commissioning.base.invalid.
BR3  spread MAY ser negativo (quando agentPct > supplierPct). O resultado MUST expor
     spreadNegative=true. Commissioning NÃO bloqueia (governado, não travado); a decisão de
     bloquear/alertar é de Quoting/Intelligence.
BR4  Exclusões de base por fornecedor NÃO são calculadas aqui — o chamador passa a base já correta.
```

## Input/Output Examples

```http
POST /api/commissioning/preview
{ "commissionableBase": { "amount": 500.00, "currency": "USD" },
  "supplierCommissionPct": 0.15, "agentCommissionPct": 0.10 }
200 OK
{ "supplierCommission": { "amount": 75.00, "currency": "USD" },
  "agentCommission":    { "amount": 50.00, "currency": "USD" },
  "spread":             { "amount": 25.00, "currency": "USD" },
  "spreadNegative": false }
```

```http
POST /api/commissioning/preview   (agente 20% > fornecedor 15%)
200 OK
{ "supplierCommission": {"amount":75.00,...}, "agentCommission": {"amount":100.00,...},
  "spread": {"amount":-25.00,"currency":"USD"}, "spreadNegative": true }

POST /api/commissioning/preview   (pct fora de [0,1])
400 Bad Request
{ "code": "commissioning.pct.invalid", "message": "Percentual deve estar entre 0 e 1.", "fields": ["supplierCommissionPct"] }
```

## API Contracts

- `POST /api/commissioning/preview` — body `{commissionableBase:{amount,currency},
  supplierCommissionPct, agentCommissionPct}` → 200 com a decomposição. Usuário autenticado.
- **Fachada (porta) consumida por Quoting:** `CommissionCalculator.compute(CommissionInput)` →
  `CommissionStatement` (record/value object). OpenAPI atualizada para o preview.

## Events

Not applicable nesta fatia. Os eventos `ExpectedCommissionAccrued`, `CommissionReversed`,
`SpreadRealized` disparam quando uma **reserva confirma/cancela** — pertencem a Booking/Reconciliation
(fatias 5/6). Aqui é cálculo puro.

## Persistence Changes

Not applicable — fatia stateless, sem migração. (Quando uma cotação/reserva guardar a decomposição,
quem persiste é Quoting/Booking, não Commissioning.)

## Validation Rules

- Delivery: tipos e presença dos campos.
- Domain: `pct ∈ [0,1]` e `base.amount ≥ 0` como invariantes do cálculo (BR2).
- Persistence: não aplicável.

## Error Behavior

- `commissioning.pct.invalid` → 400; `commissioning.base.invalid` → 400. Chaves i18n pt-BR.

## Observability Requirements

- Sem mudança de estado de negócio → log mínimo. Métrica opcional `commission_preview_total`.

## Tests Required

- **Unit:** corretude do cálculo (incl. arredondamento HALF_UP); flag `spreadNegative`; limites
  `pct = 0` e `pct = 1`; `pct` fora de faixa e base negativa rejeitados.
- **Integração:** endpoint de preview (happy path e erros 400).
- **Regressão:** spread negativo **exposto** (não escondido); arredondamento HALF_UP num caso com
  dízima.

## Acceptance Criteria

- Base USD 500, fornecedor 15%, agente 10% → 75,00 / 50,00 / spread 25,00, `spreadNegative=false`.
- Agente 20% > fornecedor 15% → spread −25,00, `spreadNegative=true`.
- `pct = 1.5` → 400 `commissioning.pct.invalid`.
- `CommissionCalculator.compute(...)` devolve o `CommissionStatement` correto para Quoting.
- `mvnw verify` verde.

## Open Questions

- **Q4:** override do fornecedor tem faixas retroativas por volume ou é fixo por marca? (No v1:
  fixo; faixas adiadas.)
- **Q5:** comissão ao agente é escopada por agência/produto/canal? (Adiado.)
- Exclusões de base comissionável por fornecedor — adiadas para spec própria.

## Out of Scope

Faixas/tiers, escopo de comissão, regras de base por fornecedor, ISS/tributos, eventos de
accrual/reversal, persistência.
