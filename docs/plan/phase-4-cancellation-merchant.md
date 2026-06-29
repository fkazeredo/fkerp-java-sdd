# Plano — Fase 4: Cancelamento como Objeto + Armadilha do Merchant + No-Show

- **Spec:** SPEC-0010 (graduando SPEC-0006). ADRs relacionados: 0011, 0012, 0014, 0015.
- **Versão alvo (ADR 0015):** próxima MINOR = **0.5.0**.
- **Migrações:** começam em **V12** (última aplicada é V11).
- **Decisões:** DL-0020…DL-0024 (registradas **antes** do código dependente).
- **Baseline:** 135 testes verdes, 10 módulos Modulith.

## Tese da fase

O cancelamento simples da SPEC-0006 (estorno integral, sem multa) é **graduado** para
**política como objeto**: `CancellationPolicy {type, windows[{hoursBefore, penaltyPct}],
refundable, costBearer, merchantOfRecord}` **congelada na Booking na confirmação** (snapshot,
como a proveniência do Quote — BR1). O cancelamento usa a **política congelada**, calcula a
**multa** pela janela aplicável e materializa os **encargos como fatos distintos** (BR5).

A **keystone** é a **armadilha do merchant**: sob `ALL_SALES_FINAL` em venda *merchant of record*,
o portal/Acme **continua devendo** ao fornecedor/marketplace o custo da reserva **mesmo
reembolsando o cliente** — **duas obrigações que NÃO se anulam**. São modeladas e gravadas como
encargos separados (`SUPPLIER` + `CUSTOMER_REFUND`), provadas por **teste de regressão**.

## Decisão de arquitetura (Rule Zero): onde isto vive

**Vive no módulo `booking`** — NÃO num módulo `cancellation`/`policy` novo. Justificativa em
**DL-0020**: o snapshot é congelado na Booking, o cancelamento é uma transição do ciclo de vida da
Booking, e os encargos pertencem à Booking que os originou. Não há linguagem, ciclo de vida nem
dono separados que justifiquem um bounded context novo (Regra Zero: "patterns/layers/modules
existem só quando resolvem um problema real"). A administração da **fonte** da política
(`cancellation_policies`, por produto/fornecedor) é uma sub-capacidade administrativa do booking,
no `internal`.

## Open Questions → decisões

| Q | Decisão | DL |
|---|---|---|
| Q3 merchant of record × afiliado | Atributo `merchantOfRecord` por marca/contrato; **default afiliado** (costBearer=SUPPLIER); merchant=true → costBearer=ACME (Portal de Experiências). Adota a Recomendação do ROADMAP. | DL-0021 |
| Multa em moeda estrangeira vs BRL | Multa **na moeda da política/encargo** (sem conversão nesta fase); `CustomerRefund` na moeda do que foi pago. Não inventa motor de câmbio (fora de escopo; Exchange congelada não tem taxa de cancelamento). | DL-0022 |
| Modelagem das 3 obrigações | `Charge {kind, amount(Money), costBearer}` — `SUPPLIER`, `CUSTOMER_REFUND`, `PENALTY`. **Nunca** se compensam (não há subtração entre eles). | DL-0024 |
| NoShowPolicy | fee + `waivedIfFlightCancelled`; dispensa exige prova (documento) — aqui um flag `flightCancelledProof` rastreável (a verificação de conformidade do documento é Compliance, fora de escopo). | DL-0023 |

## Fatias (uma feature branch por fatia, merge --no-ff em develop)

### Fatia 1 — `feature/slice-9a-cancellation-policy-object`
Política como objeto + fonte administrável.
- Value objects: `PenaltyWindow {hoursBefore, penaltyPct}`, `CancellationType {STANDARD,
  ALL_SALES_FINAL, CUSTOM}` (enum com comportamento), `CostBearer {AGENCY, ACME, SUPPLIER}`,
  `CancellationPolicy {type, windows, refundable, costBearer, merchantOfRecord}` com o **cálculo
  da multa** `penaltyFor(hoursUntilService, paidAmount)` (BR2/BR3/BR4) — método de domínio puro,
  Money scale 2 HALF_UP.
- `CancellationPolicyInvalidException` (`cancellation.policy.invalid` → 400).
- Fonte: `cancellation_policies` (V12, por `scope_ref`), agregado `CancellationPolicySource`
  (internal), `CancellationPolicyAdminService`, endpoints `GET/PUT
  /api/products/{ref}/cancellation-policy` (papel administrativo).
- **RED:** unit do cálculo de multa (vários hoursBefore; sem janela ⇒ 0; ALL_SALES_FINAL);
  integração GET/PUT da fonte; janelas malformadas ⇒ 400.

### Fatia 2 — `feature/slice-9b-cancel-charges-merchant-trap`
Snapshot congelado + cancelamento rico + **armadilha do merchant**.
- `booking_cancellation_snapshots` (V13): congela a política na **confirmação** (BR1) lendo a
  fonte por `scope_ref` (o booking guarda o `scopeRef`/`serviceCurrency` do produto). Snapshot
  inclui `noShowFee`/`waivedIfFlightCancelled` (preparando a Fatia 3).
- `Charge` (value), `cancellation_charges` (V13), agregado de encargos por booking.
- `BookingService.cancel(bookingId, reason, serviceStartsAt, refundAmount?, actor)`:
  - calcula horas-até-serviço (UTC, **relógio controlado**);
  - STANDARD/CUSTOM ⇒ `PENALTY` pela janela (costBearer da política);
  - ALL_SALES_FINAL ⇒ `SUPPLIER` (custo irrecuperável, costBearer pela merchant rule) **+**
    `CUSTOMER_REFUND` (se houver reembolso comercial) — **dois encargos**, sem netting (BR5);
  - publica `CancellationCharged`; no caso merchant, `MerchantObligationIncurred` (8.2-G/H).
- Endpoint `POST /api/bookings/{id}/cancel` agora aceita `{reason, serviceStartsAt, refundAmount?}`
  e retorna os encargos (substitui o cancel simples — atualiza o controller/DTO).
- **RED + REGRESSÃO (a armadilha):** ALL_SALES_FINAL com reembolso ⇒ existem **SUPPLIER e
  CUSTOMER_REFUND** e a soma das obrigações **não** é líquida-zero (falha antes, passa depois).
- Auditoria de todo cancelamento com encargo (BR7).

### Fatia 3 — `feature/slice-9c-no-show-policy`
- `NoShowPolicy {fee, waivedIfFlightCancelled}` (lida do snapshot).
- `POST /api/bookings/{id}/no-show` aceita prova opcional de voo cancelado; aplica fee, dispensa
  com prova; publica `NoShowCharged` (`{bookingId, fee, waived, occurredAt}`); grava `Charge`
  `NO_SHOW` quando cobrada.
- **RED:** no-show cobra fee; no-show com prova + `waivedIfFlightCancelled` dispensa.

## Definition of Done por fatia (TUTORIAL §3.6)
ArchUnit + Modulith + Spotless + Checkstyle verdes; `./mvnw verify` verde (Docker up); i18n
pt-BR + fallback; `HttpErrorMapping` completo; OpenApiConfig/contratos atualizados; eventos
in-process; sem FK cross-módulo; caderno de testes + MANUAL.md atualizados; Conventional Commits.

## Eventos novos
- `CancellationCharged {bookingId, charges[], policyType, occurredAt}` (produtor booking).
- `MerchantObligationIncurred {bookingId, supplierCharge, occurredAt}` (ALL_SALES_FINAL).
- `NoShowCharged {bookingId, fee, waived, occurredAt}`.
Sem consumidor obrigatório nesta fase (Finance/Payout/Intelligence são fases futuras) — in-process,
viram contrato/outbox ao cruzar serviço (como os demais).

## Fora de escopo (SPEC-0010)
Execução do reembolso (Payout/SPEC-0017), chamado de pós-venda (AfterSales/SPEC-0018), lançamento
contábil (Finance/SPEC-0015), conversão cambial da multa, verificação de conformidade do documento
de prova (Compliance). Telas Angular (backend-first, como Fases 2–3; follow-up).
