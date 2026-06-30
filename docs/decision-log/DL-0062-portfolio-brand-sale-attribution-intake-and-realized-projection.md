# DL-0062 — Portfolio: realizado por marca via **intake próprio** (reserva→marca) projetado de eventos de venda, sem alterar o evento do Booking

- **Fase:** 8g (Portfolio — SPEC-0020)
- **Spec(s):** SPEC-0020 (BR4 "O realizado por marca MUST ser projetado a partir de eventos de venda
  (`BookingConfirmed`/`SpreadRealized`) filtrados pela marca representada — read-model, sem alterar a
  venda"; BR6 "Portfolio MUST NOT precificar nem calcular comissão"; Open Question "Como a
  marca/produto se liga à reserva (qual campo identifica a marca na venda) — confirmar com o dono").
- **ADR relacionado:** modules-and-apis.md (sem FK cross-contexto; id de outro contexto é **valor**;
  fronteira preserva extração futura); messaging-and-integrations.md (idempotência por constraint);
  **DL-0057** (mesmo padrão de intake de atribuição usado em Marketing); persistence.md (read-model/
  projeção em vez de aggregate).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Moderada

## Lacuna

A BR4 exige projetar o **realizado por marca** a partir dos eventos de venda **filtrados pela marca**.
Mas, hoje, **nem `BookingConfirmed` nem `SpreadRealized` carregam a marca representada** — e o
`booking`/`quoting` **não coletam** uma referência de marca/fornecedor estruturada (o Booking só tem
um `scopeRef` livre, usado para política de cancelamento, sem semântica de "marca representada"). A
própria spec marca como **Open Question**: "qual campo identifica a marca na venda — confirmar com o
dono". Sem resolver, não há como agrupar a venda por marca.

## Decisão

Espelhar o padrão já validado do **DL-0057** (atribuição de Marketing), mantendo a propriedade do
dado no Portfolio e **sem alterar o contrato de evento do Booking**:

1. **Intake de atribuição de venda, próprio do Portfolio.** Um agregado
   `BrandSaleAttribution(bookingId, brandRef, attributedAt)` com `UNIQUE (booking_id)` (idempotência):
   liga **uma reserva a uma marca representada**. A ligação é **registrada** por um endpoint próprio
   `POST /api/portfolio/brands/{brandRef}/sales` (corpo `{bookingId}`) — quem origina a venda (o
   agente/portal que sabe qual marca foi vendida) informa o vínculo. É o ponto de entrada que a spec
   coloca "filtrado pela marca representada **como valor**".
2. **Projeção do realizado a partir dos eventos de venda.** Listeners internos consomem
   `BookingConfirmed` (incrementa o **VOLUME** — contagem de vendas confirmadas da marca) e
   `SpreadRealized` (acumula a **REVENUE** — o spread realizado, em BRL). Cada evento é casado à marca
   **pelo intake** (passo 1); um evento sem marca pré-registrada **não conta para marca nenhuma** (não
   há atribuição forçada). A projeção é **idempotente** por `(bookingId/caseId, métrica)`: re-entrega
   do mesmo evento não soma duas vezes.
3. **`BrandGoal` + progresso.** `BrandGoal(brandRef, period, metric ∈ {VOLUME, REVENUE}, target)` e
   `GET .../goals/{period}/progress` cruzam **realizado projetado × target**, calculando o
   `attainmentPct`. O período da venda vem do `occurredAt` do evento (UTC → ano/mês).
4. **REVENUE em BRL.** O `SpreadRealized.realizedSpread` é a fonte de receita (o spread é a receita
   real da Acme, OVERVIEW Parte 3.2); a meta de REVENUE é comparada **na mesma moeda** (BRL). VOLUME é
   contagem (sem moeda). Portfolio **não precifica nem calcula comissão** (BR6): só **soma** o que os
   eventos já trazem.
5. **Liga `SpreadRealized` (caseId) à marca.** `SpreadRealized` carrega `caseId`, não `bookingId`. O
   casamento case→booking→marca usa o `bookingId` quando disponível no fluxo; no v1, como o
   `ReconciliationCase` é por venda, o intake aceita também registrar a marca pelo caso quando o
   produtor só expõe o caseId — **seam rastreável**: quando a venda passar a carregar a marca nativa,
   troca-se a fonte sem mexer na tabela de metas.

## Justificativa

- **ROADMAP "Recomendações"** não cobre esta Q específica → pesquisa de padrão interno: o **DL-0057**
  resolveu exatamente a mesma classe de problema (ligar um fato externo a um agregado sem alterar o
  evento já consumido por vários módulos) com **intake próprio + projeção idempotente**. Reusar o
  padrão estabelecido é a opção mais defensável e consistente (core-principles.md: preferir o padrão
  já adotado).
- modules-and-apis.md **proíbe FK cross-contexto** e pede que a fronteira **preserve a extração
  futura**: o Portfolio **não pode** forçar um campo "marca" no agregado do Booking só para sua
  conveniência, nem depender do `internal` do Booking. Manter a atribuição **dentro do Portfolio**
  respeita a propriedade do dado e mantém o Booking ignorante de representação.
- Alterar `BookingConfirmed`/`SpreadRealized` para carregar `brandRef` mudaria **contrato de evento já
  consumido** (Reconciliation/Finance/Intelligence/Marketing) por um dado que o produtor nem coleta —
  risco desproporcional. O intake entrega o mesmo resultado de negócio (realizado por marca) sem isso.
- **Confiança=Baixa**: a Open Question "qual campo identifica a marca na venda" é **dado de negócio**
  que só o dono fecha (pode existir um campo de marca no portal/Sourcing que deveria alimentar o
  intake automaticamente). Adotamos o **valor mais defensável** (intake explícito + seam) e marcamos
  para confirmação.

## Alternativas descartadas

- **Adicionar `brandRef` a `BookingConfirmed`/`SpreadRealized` (e ao Booking/Quoting/Sourcing).**
  Descartada no v1: muda contrato de evento consumido por ≥4 módulos e exige coleta estruturada de
  marca no fluxo de venda (escopo de Sourcing/Quoting). Fica como **seam** para quando a captura de
  marca existir — então o listener lê a marca do próprio evento e o intake vira opcional.
- **Portfolio ler o `scopeRef` do Booking como "marca".** Descartada: `scopeRef` é texto livre para
  **política de cancelamento** (SPEC-0010), sem garantia de ser a marca representada; reinterpretá-lo
  seria inventar semântica (Regra Zero / "nunca inventar regra de negócio") e violaria a fronteira
  (leria internals do Booking).
- **Projeção materializada por job batch lendo o razão do Finance.** Descartada: acoplaria Portfolio a
  Finance e à contabilidade; a spec pede projeção **de eventos de venda**, não do razão.
- **Portfolio chamar `BookingService`/`ReconciliationService` para descobrir a marca.** Descartada:
  inverteria a direção (Portfolio é **referência/consumidor de evento**, não comanda) e criaria ciclo.

## Impacto

- **Specs:** SPEC-0020 — a Open Question "como a marca se liga à reserva" fecha como "ASSUMIDO (ver
  DL-0062): intake próprio `(bookingId→brandRef)` + projeção idempotente de `BookingConfirmed`
  (VOLUME) e `SpreadRealized` (REVENUE), sem alterar o evento". BR4 concretizada.
- **Arquivos:** agregado `BrandSaleAttribution` (+repo UNIQUE), projeção `BrandRealizedProjection`
  (+repo), listeners internos `PortfolioBookingEventsListener`/`PortfolioReconciliationEventsListener`,
  `PortfolioService.attributeSale / goalProgress`.
- **Migração:** `V25` — `brand_sale_attributions (booking_id UNIQUE, brand_ref)` e
  `brand_realized (brand_ref, period, metric, ...)` (projeção idempotente).
- **Contratos:** `POST /api/portfolio/brands/{brandRef}/sales`;
  `GET /api/portfolio/brands/{id}/goals/{period}/progress`.
- **Modulith:** `portfolio → {booking (evento), reconciliation (evento), money, error}` — **acíclico**
  (nenhum desses depende de volta no Portfolio).

## Como reverter

Moderada: se o dono confirmar um campo de marca nativo na venda (ex.: `brandRef` capturado no Sourcing
e propagado no evento), troca-se a **fonte** do casamento no listener (intake → campo do evento) sem
mexer nas tabelas `brand_goals`/`brand_realized` nem no contrato de progresso — refator localizado de
uma/duas classes. O intake explícito pode coexistir (canais sem marca estruturada).
