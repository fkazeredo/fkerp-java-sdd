# Event Storming — Venda "Portal de Experiências" ponta a ponta

> Artefato da **Fatia 0** (SPEC-0001; OVERVIEW Parte 12). Objetivo: mapear o fluxo de uma venda
> manual de experiência (ex.: passeio em Orlando) do ponto de vista do **negócio**, em eventos,
> **antes** do código de domínio. Regra de leitura do Event Storming: **onde a linguagem muda, há
> uma fronteira de contexto** (OVERVIEW Parte 2). Prosa em pt-BR; nomes de código/eventos em inglês.
>
> Legenda (cores clássicas do Event Storming):
> 🟧 **Evento de domínio** (fato passado) · 🟦 **Comando** (intenção) · 🟨 **Ator/Agregado** ·
> 🟪 **Política/Process manager** ("sempre que… então…") · 🟩 **Read model** ·
> 🩷 **Sistema externo / hotspot** · ❓ **Hotspot / dúvida em aberto**.

## 1. Narrativa (uma frase)

Um agente de viagens pede, pela Acme Travel, uma experiência cotada a partir de um **preço-base**
(de catálogo/manual); a Acme **compõe** o preço com **câmbio congelado** + **comissão de duas
pontas** + markup, **sugere** o valor (o humano pode divergir com rastro), **reserva**, e ao
**confirmar** dispara o reconhecimento das comissões, a **conciliação** das pontas e os repasses —
tudo lastreado por **documentos** no compliance.

## 2. Linha do tempo (pivotal events em **negrito**)

```
🟦 Registrar conta comercial
        │
        ▼
🟧 AccountRegistered ───────────────────────────────────────────── [Accounts]
        │
🟦 Informar oferta (produto + preço-base, manual/catálogo)
        ▼
🟧 OfferSourced  (priceOrigin = MANUAL)                              [Sourcing]
        │
🟦 Compor cotação
        │   usa 🟩 PinnedSellRate (Open-Host)  ◄────────────────── [Exchange]
        │   usa 🟩 CommissionPreview            ◄────────────────── [Commissioning]
        │   usa 🟩 MarkupParameter (stub→SPEC-0014) ◄────────────── [CommercialPolicy]
        ▼
🟧 QuoteComposed  (suggestedAmount calculado, com proveniência)     [Quoting]
        │
🟦 Aplicar valor (aceitar a sugestão OU divergir)
        ▼
🟧 PriceSuggested            🟧 OverrideRecorded {quem,quando,de→para,motivo}   [Quoting]
        │
🟦 Reservar (localizador manual)
        ▼
🟧 **BookingPlaced**  (QUOTED→ORDERED→PENDING≤72h)                  [Booking]
        │
🟪 Política: pendente expira em 72h sem confirmação → auto-rejeita
        │
🟦 Confirmar reserva
        ▼
🟧 **BookingConfirmed**                                             [Booking]
        │
🟪 Política: "ao confirmar, reconhecer as comissões"
        ▼
🟧 ExpectedCommissionAccrued (supplier=a receber, agent=a pagar)    [Commissioning]
🟧 SpreadRealized (derivado: receita real da Acme)                  [Commissioning]
        │
🟦 Conciliar a venda
        ▼
🟧 **ReconciliationCaseOpened** → 🟧 ReconciliationMatched          [Reconciliation]
        │            (a pagar × a receber × comissão esperada×recebida × ganho/perda cambial)
        ▼
🟦 Repassar agente / liquidar fornecedor     🟦 Emitir NF da comissão
        ▼                                          ▼
🟧 PayoutScheduled  [Payout]              🟧 CommissionInvoiced  [Billing]
        │                                          │
        └──────────────► lança em AP/AR ◄──────────┘             [Finance]
                              │
🟪 Política: lançamento exige documento hábil
                              ▼
🟧 DocumentAttached / 🟧 RequirementUnmet → 🟧 PeriodClosed         [Compliance]
```

> Fluxo de cancelamento (resumo): 🟦 Cancelar → 🟧 **BookingCancelled** → 🟪 "estorna as duas
> pontas" → 🟧 CommissionReversed [Commissioning]; a política rica (janelas de multa, merchant
> ALL_SALES_FINAL) é da **Fase 4 (SPEC-0010)**.

## 3. Onde a linguagem muda → fronteiras de contexto

| Transição no fluxo | A palavra muda de… para… | Fronteira (bounded context) |
|---|---|---|
| Quem compra | "lead" → **Conta Comercial** (CNPJ/MEI/CPF) | **Accounts** |
| De onde veio a oferta | catálogo/site → **Oferta** + nível de integração | **Sourcing** |
| Quanto custa em BRL hoje | dólar do fornecedor → **Taxa Congelada** servida | **Exchange** (Open-Host) |
| Direito à comissão | preço → **comissão a receber/pagar + spread** | **Commissioning** |
| Número proposto | custo → **Sugestão de Preço** (+ Override) | **Quoting** (keystone) |
| Compromisso operacional | proposta → **Reserva** + localizador | **Booking** |
| "Casar" as pontas | pagamentos soltos → **Caso de Conciliação** | **Reconciliation** |
| Pagar/liquidar | dinheiro → **Repasse / Liquidação** | **Payout** |
| Nota fiscal | comissão → **NF sobre a comissão (ISS)** | **Billing** |
| "O mês fecha?" | lançamento → **Documento hábil + Fechamento** | **Compliance** + **Finance** |
| Regras governadas | número fixo → **Parâmetro/Diretiva** (precedência) | **CommercialPolicy** |

Padrões de integração observados: **Exchange** e **CommercialPolicy** = *Open-Host Service*
(servem um "cardápio" a quem compõe); **Compliance** = cofre transversal; **Intelligence (DSS)**
(futuro) só **lê** os eventos e aconselha; sistemas externos (Marketplace de Tours, GDS, REP de
ponto) entram por **ACL** no contexto **Integration**.

## 4. Hotspots / dúvidas em aberto (não resolvidos aqui)

- ❓ **Fórmula exata de preço** no Quoting (markup sobre base × repasse de tarifa; moeda da base
  comissionável) — decisão de negócio (SPEC-0005). Recomendação de partida: base comissionável em
  BRL, preço = base BRL + markup (default 0). (ROADMAP)
- ❓ **Override do fornecedor**: fixo por marca × faixas retroativas (Q4 — fixo no v1).
- ❓ **Escopo da comissão do agente** (agência/produto/canal) (Q5).
- ❓ **Merchant of record** do portal (Q3 — define motor de cobrança/reembolso; Fase 4).
- ❓ **Tipo de REP** do ponto (Q6 — Fase 6).

> Estas perguntas **travam fatias específicas** (Fase 1+) e seguem registradas nas *Open Questions*
> das specs donas; não são inventadas aqui (CLAUDE.md, invariante 3).

## 5. Onde a Fase 0 (este esqueleto) se encaixa

A Fase 0 **não implementa** nenhum desses eventos de negócio. Ela entrega a **fundação** onde eles
vão pousar: o monólito modular `com.fksoft` (camadas domain/application/infra), o kernel de erros,
i18n, correlation id, o `GET /api/system/health` e os portões (ArchUnit + Spring Modulith +
Spotless/Checkstyle). O **primeiro** evento de negócio (`AccountRegistered`) nasce na **SPEC-0002**
(Fase 1), seguindo a ordem de dependência: Accounts/Exchange/Commissioning → Quoting → Booking →
Reconciliation (ADR 0014).
