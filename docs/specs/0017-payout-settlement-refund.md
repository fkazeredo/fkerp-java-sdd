# 0017 - Payout (Repasse, Liquidação e Reembolso)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Os pagamentos geram **comprovante** arquivado no **Compliance
> (SPEC-0008)**, lançam/baixam no **Finance (SPEC-0015)** e a **taxa de liquidação** do fornecedor
> alimenta a **Reconciliation (SPEC-0007)** e a **Exposure (SPEC-0011)**.

## Goal

Executar as **saídas e baixas financeiras** da operação: **repassar a comissão ao agente**, **liquidar
o fornecedor** (moeda estrangeira, à taxa real), **executar reembolsos** (originados em cancelamento) e
suportar **parcelamento** — sempre com comprovante e rastro (redesenho linha 152).

## Scope

**Em escopo:** o agregado `Payout` (kind AGENT_COMMISSION | SUPPLIER_SETTLEMENT | REFUND; valor;
moeda; **taxa de liquidação** quando em moeda estrangeira; status PENDING/EXECUTED/FAILED; plano de
parcelas); a execução via **porta de pagamento** (ACL — PIX/TED/boleto/gateway); o registro do
**comprovante** como `Document` (PAYMENT_PROOF/REFUND_PROOF) no Compliance; a publicação da liquidação
do fornecedor (com a taxa) para a Reconciliation/Exchange.

**Fora de escopo:** a **decisão** de reembolsar e seus encargos (Cancellation/AfterSales — SPEC-0010/0018);
a **emissão da NF** (Billing — SPEC-0016); conciliação bancária completa (Finance/comprar).

## Business Context

Repasse e liquidação acontecem em **tempos e moedas diferentes** (a agência já pagou em reais; o
fornecedor é pago em dólar depois). A **taxa de liquidação** registrada aqui é o número que fecha o
`fxGainLoss` na conciliação e o `realizedDrift` na exposição. O comprovante é documento hábil que o
fechamento mensal vai exigir.

## Business Rules

```txt
BR1  Payout MUST ter kind ∈ {AGENT_COMMISSION, SUPPLIER_SETTLEMENT, REFUND}, payee (id+tipo, valor),
     amount (Money) e, se moeda estrangeira, settlementRate (escala 6, > 0) + valor em BRL liquidado.
BR2  Execução é transição financeira → **locking pessimista**; status PENDING → EXECUTED | FAILED.
     Falha de gateway classifica o erro (sem "executado" falso).
BR3  Execução MUST ser idempotente por payoutId (não paga duas vezes); retries seguros.
BR4  Ao executar, MUST registrar o comprovante como Document no Compliance (PAYMENT_PROOF para
     comissão/liquidação; REFUND_PROOF para reembolso) e baixar/lançar no Finance.
BR5  SUPPLIER_SETTLEMENT executado MUST publicar SupplierSettled {bookingId, settlementRate, paidBrl}
     — consumido por Reconciliation (fecha o caso) e Exchange (fecha a posição).
BR6  Parcelamento: um Payout pode ter N parcelas com vencimentos; cada parcela executa e comprova
     individualmente; o Payout só fica EXECUTED quando todas as parcelas executam.
BR7  REFUND MUST referenciar a obrigação de origem (CancellationCharge / chamado de AfterSales) —
     não se cria reembolso "solto".
BR8  Meio de pagamento — ASSUMIDO (ver DL-0048): a execução vai ao mundo externo por uma porta
     `PaymentGateway` (ACL) com adaptador **mock rastreável de webhook assíncrono** (ADR 0006):
     request → PENDING; o callback assinado confirma/falha idempotente por
     (payoutId, installmentSeq, providerRef). Provedor real (PIX/TED/boleto/gateway) = trocar o
     adaptador. **Confiança=Baixa** (qual provedor é Open Question de negócio).
BR9  Moeda da liquidação — ASSUMIDO (ver DL-0049): o `Payout` modela `amount` na moeda original
     (USD) + `settlementRate` (escala 6, > 0) + `settledBrl` (a baixa em BRL = amount × rate,
     HALF_UP). A baixa no Finance usa `settledBrl`. Remessa internacional real fica para integração
     futura (mesmo gateway). **Confiança=Baixa, Reversibilidade=Cara.**
BR10 Parcelamento — ASSUMIDO (ver DL-0050): v1 **sem juros**; soma das parcelas == total **exata**
     (resto de centavos na 1ª parcela); cada parcela executa/comprova individualmente; Payout só
     EXECUTED quando todas executam; "sem plano" = 1 parcela implícita. Juros/elegibilidade ficam
     para CommercialPolicy (SPEC-0014) futura.
```

## Input/Output Examples

```http
POST /api/payouts
{ "kind":"SUPPLIER_SETTLEMENT", "payee":{"id":"sup-12","type":"SUPPLIER"}, "bookingId":"b71...",
  "amount":{"amount":"500.00","currency":"USD"}, "settlementRate":"5.700000" }
201 Created  { "id":"p55...", "status":"PENDING", "settledBrl":{"amount":"2850.00","currency":"BRL"} }

POST /api/payouts/{id}/execute
200 OK  { "id":"p55...", "status":"EXECUTED", "proofDocumentId":"d80..." }
# efeito: SupplierSettled -> Reconciliation fecha o caso (fxGainLoss), Exchange fecha a posição (drift)
```

## API Contracts

- `POST /api/payouts` — cria repasse/liquidação/reembolso (com plano de parcelas opcional) → 201.
- `POST /api/payouts/{id}/execute` — executa (ou executa a próxima parcela) → 200 | 502/409.
- `GET /api/payouts/{id}` → 200 | 404 `payout.not-found`.
- `GET /api/payouts?kind=&status=&payee=&page=&size=` → `PageResponse`.
- OpenAPI atualizada; gateway de pagamento isolado na ACL.

## Events

- `SupplierSettled` — `{payoutId, bookingId, settlementRate, paidBrl, occurredAt}`. Produtor: `payout`.
  Consumidor entregue: `finance` (posta o AP SUPPLIER_SETTLEMENT **uma vez**, idempotente). Consumo por
  `reconciliation`/`exchange` **adiado** (DL-0051): eles já fecham o FX pela liquidação própria
  (DL-0028); religá-los ao evento duplicaria o fechamento/arriscaria ciclo — costura registrada como
  pendência, sem perda de cobertura (a consistência `settlementRate → fxGainLoss/totalGap` segue provada
  pela regressão da SPEC-0007/0011).
- `AgentCommissionPaid` — `{payoutId, agentId, amount, occurredAt}`. Produtor: `payout`. Consumidor
  entregue: `finance` (baixa COMMISSION_PAYABLE, idempotente). `intelligence` — adiado.
- `RefundExecuted` — `{payoutId, originRef, amount, occurredAt}`. Produtor: `payout`. Consumidor
  entregue: `finance` (baixa REFUND, idempotente; **não** toca a obrigação do fornecedor — armadilha do
  merchant intacta, DL-0024/0051). `aftersales` — adiado (módulo ainda não existe, 8e).

## Persistence Changes

> Migrações reais entregues: **V21** (payout) e **V22** (mock do gateway + idempotência do webhook).
> O `V17` do rascunho abaixo foi renumerado para **V21** (a última migração aplicada era V20).

```txt
V21__create_payout.sql
  payouts(
    id uuid PK, kind varchar not null, payee_id varchar not null, payee_type varchar not null,
    booking_id uuid null, origin_ref varchar null,              -- p/ REFUND (BR7), valores
    amount numeric(18,2) not null, currency varchar not null,
    settlement_rate numeric(18,6) null, settled_brl numeric(18,2) null,
    status varchar not null, proof_document_id uuid null,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null
  )
  payout_installments(
    id uuid PK, payout_id uuid not null REFERENCES payouts(id),
    seq int not null, due_date date not null, amount numeric(18,2) not null,
    status varchar not null, executed_at timestamptz null, proof_document_id uuid null,
    UNIQUE (payout_id, seq)
  )

V22__create_payment_mock_and_webhooks.sql           -- ADR 0006 / DL-0048 (mock do gateway)
  mock_payout_jobs(id, payout_id, installment_seq, provider_ref UNIQUE, outcome, deliver_after,
                   delivered, created_at)            -- a perna assíncrona do mock (webhook adiado)
  processed_payout_webhooks(id, payout_id, installment_seq, provider_ref, outcome, processed_at,
                   UNIQUE (payout_id, installment_seq, provider_ref))  -- idempotência do webhook (BR3)
```

A porta de pagamento (`PaymentGateway`) e o comprovante (`FileStorage`/Compliance) ficam em
`infra/integration.payment`. Execução com **locking pessimista** + idempotência por payoutId/parcela;
o webhook é idempotente por `(payoutId, installmentSeq, providerRef)`. O comprovante é arquivado no
Compliance como `PAYMENT_PROOF` (comissão/liquidação) ou `REFUND_PROOF` (reembolso) pelo orquestrador
em `infra.integration.payment` (Payout é folha — não importa Compliance).

## Validation Rules

- Integração: idempotência da execução; resposta do gateway validada; taxa > 0 quando estrangeira.
- Domain: máquina de status (BR2) e regra de parcelamento (BR6) como invariantes; REFUND exige origem (BR7).
- Application: existência de payee/lançamento; arquivamento do comprovante (BR4).

## Error Behavior

`payout.not-found` → 404; `payout.already-executed` → 409; `payout.refund.origin-required` → 400;
falha de gateway → 502 classificado. i18n em `messages_pt_BR.properties`. **Nunca** logar dados de
pagamento sensíveis.

## Observability Requirements

- Logar execução/falha como evento de negócio + **log de integração** com o gateway (latência, classe,
  correlation id). Métricas: `payouts_executed_total{kind}`, `payouts_failed_total`, valor liquidado.

## Tests Required

- **Unit/domain:** conversão BRL pela `settlementRate`; máquina de status; parcelamento (Payout só
  EXECUTED quando todas as parcelas executam); REFUND sem origem → erro.
- **Integração (Testcontainers + gateway fake):** liquidar fornecedor publica `SupplierSettled`
  (Reconciliation fecha o caso, Exchange fecha a posição); execução idempotente não paga duas vezes;
  comprovante arquivado no Compliance.
- **Regressão:** a taxa de liquidação registrada aqui produz o `fxGainLoss`/`totalGap` esperado nas
  SPEC-0007/0011 (consistência ponta a ponta).

## Acceptance Criteria

- Liquidar o fornecedor a 5,70 baixa R$ 2.850, arquiva o comprovante e fecha o caso de conciliação e a
  posição de câmbio.
- Repassar a comissão ao agente e executar um reembolso de cancelamento geram comprovante e lançamento.
- Execução é idempotente (sem pagamento dobrado).
- `./mvnw verify` verde.

## Open Questions

> Todas as Open Questions iniciais foram **resolvidas em modo autônomo** (RUN-PHASE) e movidas para
> Business Rules como **ASSUMIDO** (ver os DL abaixo). Permanecem decisões de negócio a confirmar com
> o dono (Confiança=Baixa); o código adota o default mais defensável e a troca é localizada.

- ~~**Meio de pagamento/gateway** real~~ → **ASSUMIDO (BR8, DL-0048)**: porta + mock rastreável de
  webhook assíncrono (ADR 0006). **Confiança=Baixa** — o provedor real é decisão do dono.
- ~~Pagamento ao fornecedor em **moeda estrangeira** vs. BRL~~ → **ASSUMIDO (BR9, DL-0049)**: modela
  `amount` (USD) + `settlementRate` + `settledBrl`. **Confiança=Baixa, Reversibilidade=Cara** —
  o fluxo bancário real (remessa) é decisão do dono.
- ~~Política de **parcelamento** (quem pode, juros)~~ → **ASSUMIDO (BR10, DL-0050)**: v1 sem juros,
  centavos exatos; juros/elegibilidade ficam para CommercialPolicy (SPEC-0014).

## Decisões registradas (decision-log)

- **DL-0048** — `PaymentGateway` (porta ACL) + mock rastreável de webhook assíncrono (ADR 0006); DTO
  do provedor não cruza para o domínio; falha classificada (sem "pago" falso). **Confiança=Baixa.**
- **DL-0049** — liquidação do fornecedor: `settlementRate` (escala 6) + `settledBrl` (baixa BRL).
  **Confiança=Baixa, Reversibilidade=Cara.**
- **DL-0050** — parcelamento v1 sem juros + distribuição exata de centavos.
- **DL-0051** — `SupplierSettled` consumido pelo **Finance** (posta uma vez); Payout é **folha**
  (acíclico); REFUND não cancela a obrigação do fornecedor (armadilha do merchant intacta).

## Out of Scope

Decisão/encargos de reembolso (SPEC-0010/0018), emissão de NF (SPEC-0016), conciliação bancária plena
(Finance/comprar).
