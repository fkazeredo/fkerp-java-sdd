# 0013 - Intelligence (DSS): Insights que Aconselham, Nunca Comandam

Status: Approved
Related ADRs: 0010, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Onde houver modelo preditivo, valem as regras de IA de
> `messaging-and-integrations.md` (validação de saída, fallback, observabilidade). A regra de ouro do
> DSS (redesenho Parte 8): **só lê eventos, aconselha, nunca comanda**.

## Goal

Tornar o ERP **não-burro**: um contexto de inteligência que escuta eventos de **todos** os contextos
(somente leitura) e produz **Insights** — *evidência (número + proveniência) + recomendação (ação com
ganho/risco) + guardrail (alerta ao cruzar limite, sem bloquear)*. A primeira fatia entrega o
**framework de Insight** e dois insights de **lucro direto**: `PromoFxAdvisor` e `OverrideNudge`
(redesenho 8.2-C/B, 8.3).

## Scope

**Em escopo:** o agregado/read-model `Insight` (tipo, sujeito, evidência com proveniência, recomendação
com ganho/risco estimado, guardrail, status de aceite humano); o consumo **read-only** dos eventos
relevantes; o insight **`PromoFxAdvisor`** (onde o congelamento converte × só queima margem, por
rota/agência/produto) construído sobre os fatos da SPEC-0011; o seam do **`OverrideNudge`** (distância
para a próxima faixa) — **gated** no modelo de faixas (Q4); observabilidade de IA (aceitos × rejeitados).

**Fora de escopo:** os demais relatórios do catálogo 8.2 (churn, cross-sell, forecast, mix, supplier
leverage, etc.) — **cada um vira sua própria spec quando priorizado**; nenhuma saída do DSS escreve em
agregado de outro contexto (princípio do redesenho).

## Business Context

O DSS amarra-se às alavancas reais de lucro da Acme. `OverrideNudge` transforma uma faixa de comissão
invisível num empurrão concreto ("você está a R$ 30k da próxima faixa; +12 reservas = +3% retroativo no
ano"). `PromoFxAdvisor` responde se a promoção de câmbio **se paga** (subsídio gasto × volume/receita
atraídos) e **onde** mantê-la ou apertá-la. Ambos **sugerem**; o humano puxa o gatilho (8.3).

## Business Rules

```txt
BR1  Um Insight MUST conter: type; subjectRef (agência/fornecedor/rota/produto); evidence (os números
     e a FONTE — quais eventos/proveniência os sustentam); recommendation (ação + estimatedGain e/ou
     estimatedRisk em Money); guardrail (qual limite foi cruzado, se algum); generatedAt; status.
BR2  Intelligence MUST ser SOMENTE LEITURA: consome eventos e projeta read-models; MUST NOT publicar
     comando nem alterar estado de Booking/Quote/Exchange/etc. (princípio do redesenho).
BR3  Guardrail ALERTA, não bloqueia: cruzar um limite gera/realça um Insight, nunca impede a operação.
BR4  status ∈ {NEW, ACCEPTED, REJECTED, DISMISSED}; a decisão humana MUST ser registrada (quem, quando)
     — é a métrica "aceitos × rejeitados" (observabilidade de IA).
BR5  PromoFxAdvisor: por rota/agência/produto, compara subsídio acumulado (RateSubsidyAccrued) com
     volume/receita atraídos (BookingConfirmed/SpreadRealized) e classifica CONVERTE (manter) ×
     QUEIMA_MARGEM (apertar), com o ganho/risco estimado e a proveniência dos números.
BR6  OverrideNudge: calcula distância até a próxima faixa e o ganho retroativo — **requer o modelo de
     faixas** (Commissioning tiers). Enquanto Q4 não definir faixas, este insight fica DESLIGADO por
     feature flag (sem dado falso).
BR7  Saída de qualquer modelo PREDITIVO MUST ser validada (schema, faixas, regras, confiança) e ter
     fallback quando inválida; decisões com impacto relevante são auditáveis (`messaging-and-integrations.md`).
```

## Input/Output Examples

```http
GET /api/intelligence/insights?type=PROMO_FX_ADVISOR&status=NEW
200 OK
{ "items": [
  { "id":"i31...", "type":"PROMO_FX_ADVISOR", "subject":{"kind":"ROUTE","ref":"GRU-MCO"},
    "evidence": { "accruedSubsidy":{"amount":"4200.00","currency":"BRL"},
                  "volumeAttracted": 38, "realizedSpread":{"amount":"9100.00","currency":"BRL"},
                  "sources":["RateSubsidyAccrued","SpreadRealized","BookingConfirmed"] },
    "recommendation": { "verdict":"CONVERTE", "action":"manter congelamento nesta rota",
                        "estimatedGain":{"amount":"4900.00","currency":"BRL"} },
    "guardrail": null, "status":"NEW" } ] }
```

```http
POST /api/intelligence/insights/{id}/decision
{ "decision":"ACCEPTED", "note":"vamos manter por mais 30 dias" }
200 OK   # registra a decisão humana (BR4) — não dispara ação automática
```

## API Contracts

- `GET /api/intelligence/insights?type=&subjectRef=&status=&page=&size=` → `PageResponse` de Insights,
  sort default por `estimatedGain desc` (prioriza o que vale mais).
- `GET /api/intelligence/insights/{id}` → 200 | 404 `intelligence.insight.not-found`.
- `POST /api/intelligence/insights/{id}/decision` — registra `{decision, note}` (NÃO executa a ação).
- **Não há** endpoint que faça o DSS agir sobre a operação — por princípio.
- OpenAPI atualizada.

## Events

- `InsightGenerated` — `{insightId, type, subjectRef, estimatedGain?, occurredAt}`. Produtor:
  `intelligence`. Consumidor: notificação/UI (alerta ao humano).
- `InsightDecided` — `{insightId, decision, decidedBy, occurredAt}` (métrica aceitos × rejeitados).
- Intelligence **consome** (read-only): `QuoteComposed`, `PriceOverridden`, `BookingConfirmed`,
  `SpreadRealized`, `ReconciliationDiscrepancyFlagged`, `RateSubsidyAccrued`, `FxPositionClosed`,
  `OverrideTierReached` (quando existir) e outros — **publica nenhum comando**.

## Persistence Changes

```txt
V13__create_intelligence.sql
  insights(
    id uuid PK, type varchar not null, subject_kind varchar not null, subject_ref varchar not null,
    evidence_json jsonb not null,             -- números + sources (proveniência)
    recommendation_json jsonb not null,       -- ação + estimatedGain/estimatedRisk
    guardrail_json jsonb null,                -- limite cruzado, se houver
    confidence numeric(5,4) null,             -- só p/ insights preditivos
    status varchar not null,
    generated_at timestamptz not null, decided_by varchar null, decided_at timestamptz null,
    created_at, updated_at timestamptz not null, version bigint not null
  )
```

Tudo aqui é **read-model/projeção** alimentado por eventos; sem escrita cross-módulo. Os cálculos
(`PromoFxAdvisor`) são projeções sobre eventos já persistidos (`persistence.md`: read models). O
`OverrideNudge` fica atrás de **feature flag** (`delivery.md`) até existir o modelo de faixas.

## Validation Rules

- Application: consumo idempotente dos eventos; projeções recomputáveis.
- Domain: invariantes do Insight (BR1); `status`/decisão (BR4).
- IA: para qualquer insight preditivo, validação de saída + fallback + threshold de confiança (BR7).
- Princípio: ArchUnit garante que `intelligence` **não** chama fachada de comando de outro módulo.

## Error Behavior

`intelligence.insight.not-found` → 404; `intelligence.decision.invalid` → 400 (decisão fora do enum).
i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Métricas de IA (`messaging-and-integrations.md`): por tipo de insight — gerados, aceitos × rejeitados,
  distribuição de confiança (preditivos), uso de fallback; **decisões auditáveis**. Logar
  `InsightGenerated`/`InsightDecided` como eventos de negócio (insightId, subjectRef, correlation id).

## Tests Required

- **Unit/domain:** `PromoFxAdvisor` classifica CONVERTE × QUEIMA_MARGEM corretamente (cenários de
  subsídio alto/baixo × volume); evidência carrega a proveniência (sources).
- **Arquitetura (ArchUnit):** `intelligence` não depende de fachadas de comando de outros módulos
  (só consome eventos / lê projeções) — **trava o "aconselha, nunca comanda"**.
- **Integração (Testcontainers):** eventos consumidos geram Insight; `decision` registra sem disparar
  ação; `OverrideNudge` permanece desligado sem o modelo de faixas (flag off).
- **Regressão:** nenhum endpoint/efeito do DSS altera estado operacional (falha antes, passa depois).

## Acceptance Criteria

- A partir dos eventos de câmbio/venda, o DSS gera um `PromoFxAdvisor` por rota com veredito e ganho
  estimado, citando as fontes.
- Registrar uma decisão humana não executa nenhuma ação automática.
- `OverrideNudge` só liga quando o modelo de faixas existir (flag), sem dado falso antes disso.
- `./mvnw verify` verde (ArchUnit confirma o princípio "só lê, aconselha").

## Open Questions

- **Q4 (faixas de override):** `OverrideNudge` depende do modelo de faixas retroativas; **em aberto** —
  enquanto fixo, o insight fica desligado.
- Cadência de recomputação dos read-models (on-event × batch noturno) e limites de guardrail
  (parâmetros governados, SPEC-0014) — confirmar.
- Quais insights do catálogo 8.2 entram a seguir (churn, forecast, mix…) — priorização do dono; cada um
  é uma spec própria.

## Out of Scope

Os demais relatórios do catálogo 8.2 (specs próprias); qualquer ação automática sobre a operação
(proibida por princípio).
