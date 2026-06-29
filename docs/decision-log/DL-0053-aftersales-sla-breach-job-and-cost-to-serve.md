# DL-0053 — AfterSales: detecção de breach por job de relógio controlado + modelo de custo de servir

- **Fase:** 8e (AfterSales — SPEC-0018)
- **Spec(s):** SPEC-0018 (BR4 SLA: now > dueAt e não resolvido → BREACHED + `SlaBreached`,
  alerta não bloqueia; BR5 registrar esforço/custo de servir ao fechar; Events `SlaBreached`).
- **ADR relacionado:** messaging-and-integrations.md (jobs: idempotência/locking/relógio);
  backend.md (datas em UTC, relógio controlável); DL-0028 (relógio controlado em job análogo).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0018 diz que o SLA é avaliado por **job** que marca `BREACHED`, mas não fixa (a) **como**
o job é tornado testável com relógio controlado; (b) o que conta como **breach** (qual prazo);
(c) o **modelo concreto do custo de servir** (BR5: "quais custos contam" está em Open Question).

## Decisão

1. **Job com relógio controlado, instante como parâmetro.** O domínio expõe
   `AfterSalesService.markBreaches(Instant now)` — recebe o instante de avaliação, exatamente como
   `BookingService.expirePendingBookings(Instant cutoff)` (padrão já validado no projeto). O
   scheduler técnico (`AfterSalesSlaScheduler` em `infra.jobs`) injeta o `Clock` e chama
   `markBreaches(clock.instant())`. Assim os testes passam um `Instant` fixo (dentro × fora do
   SLA) **sem** mockar o bean `Clock` — determinístico e à prova de fuso (UTC).
2. **O que é breach (BR4):** um caso **não terminal** (status ≠ RESOLVED/CLOSED) cujo `dueAt`
   (prazo de resolução, DL-0052) é anterior a `now` é marcado `breached=true` e publica
   `SlaBreached{caseId, dueAt, occurredAt}`. **Idempotente:** um caso já marcado não republica
   (flag + filtro na query). É **alerta**: não muda o status de workflow nem bloqueia — o caso
   segue operável (o operador ainda resolve/fecha). O `firstResponseDueAt` é rastreado no
   agregado para o breach de 1ª resposta (caso ainda OPEN após 24h conta como first-response
   breach), provado por teste; o flag agregado `breached` cobre o alerta.
3. **Modelo de custo de servir (BR5):** value object `CostToServe` (serializado em
   `cost_to_serve_json`) com **Money (BRL, scale 2 HALF_UP)** acumulado:
   `handlingCost` (esforço — somado a cada registro), `refundCost` (reembolso associado, vindo do
   Payout) e `reopenCount`. Acumula via `accrue(Money)`/`linkRefund(Money)`; ao **fechar**, o
   total fica congelado e o `SupportCaseResolved`/leitura expõe o custo para a Intelligence
   atribuir "margem real". No v1 o esforço é um **valor informado** (handling cost) — a derivação
   automática a partir de tempo/reaberturas fica como evolução (a estrutura já comporta).

## Justificativa

- O supervisor exige "controlled clock (testable)" e "advises/flags, it does not block";
  passar o instante ao método é o caminho mais simples e já usado (Booking), sem `MutableClock`.
- BR5 pede o custo de servir mas "quais custos contam" é Open Question — modelar como Money
  acumulável (handling + refund + reaberturas) é o mínimo defensável que alimenta a Intelligence
  hoje e não trava a evolução. Scale 2 HALF_UP é o kernel `Money` do projeto.
- `SlaBreached` idempotente evita spam de alerta em sweeps repetidos (jobs: idempotência).

## Alternativas descartadas

- **Mockar/avançar o bean `Clock` global nos testes.** Descartada: contamina o contexto Spring
  compartilhado (singleton container) e é mais frágil que passar o instante ao método puro.
- **Transição de status dedicada `BREACHED`.** Descartada: BR4 diz que é **alerta, não bloqueia**;
  um status terminal de breach impediria o caso de continuar sendo trabalhado. Usa-se um **flag**
  ortogonal ao status de workflow.
- **Custo de servir derivado automaticamente de tempo no v1.** Descartada: "quais custos contam"
  é Open Question; derivar agora inventaria regra de negócio. Estrutura acumulável já preparada.

## Impacto

- **Specs:** SPEC-0018 — BR4/BR5 concretizadas; Open Question "modelo de atribuição de custo de
  servir" → Business Rules como "ASSUMIDO parcial (ver DL-0053) — quais custos contam segue a
  confirmar com o dono".
- **Arquivos:** `AfterSalesService.markBreaches`; `AfterSalesSlaScheduler` (infra.jobs);
  `CostToServe` (value object) + codec jsonb; evento `SlaBreached`; flag/coluna `breached`.
- **Migrações:** `V23` inclui `breached boolean` e `cost_to_serve_json jsonb` na `support_cases`.
- **Observabilidade:** log de evento de negócio (caseId, dueAt) + métrica `sla_breached_total`.

## Como reverter

Barata: o flag e o job são aditivos; mudar a definição de breach (ex.: incluir WAITING como
pausa do relógio) é editar o filtro de `markBreaches`. Trocar o modelo de custo de servir é
alterar o value object `CostToServe` e o codec — refator local, sem mudar contrato externo.
