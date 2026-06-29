# DL-0036 — OverrideNudge desligado por feature flag (Q4) e seam de porta LLM desenhado mas não wired

- **Fase:** 7 (Intelligence / DSS)
- **Spec(s):** SPEC-0013 (BR6 "OverrideNudge requer o modelo de faixas — enquanto Q4 não definir,
  fica DESLIGADO por feature flag, sem dado falso"; Open Questions Q4; "cadência de recomputação")
- **ADR relacionado:** 0012 (camadas); ROADMAP "Recomendações Q4" (fixo no v1, Nudge atrás de flag)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

(a) O `OverrideNudge` calcula "distância até a próxima faixa de comissão", mas o **modelo de faixas
retroativas** (Q4) **não existe** — a recomendação do ROADMAP é "fixo por marca no v1; OverrideNudge
atrás de flag até existir a tabela de faixas". Era preciso decidir **como** mantê-lo desligado sem
dado falso, mas com o seam pronto. (b) A DESIGN GUIDANCE permite um LLM "se justificado", atrás de
porta com stub determinístico — era preciso decidir se entra agora. (c) A SPEC pede a cadência de
recomputação (on-event × batch).

## Decisão

1. **OverrideNudge fica DESLIGADO por feature flag** `intelligence.override-nudge.enabled`
   (default `false`), via `@Value` (mesmo padrão de `@Value` já usado em `point-clock.*`,
   `compliance.*`). O **seam** existe: o tipo de insight `OVERRIDE_NUDGE` no enum, o listener de
   `PriceOverridden` registrado, e o serviço **curto-circuita quando a flag está off** — não persiste
   nenhum Insight de Nudge, **sem dado falso** (BR6). Quando a tabela de faixas existir (Q4 fechada),
   liga-se a flag e implementa-se o cálculo da distância retroativa, sem refator do framework.
2. **Nenhum LLM é wired nesta fase.** Os dois insights da v1 (`PromoFxAdvisor`, `OverrideNudge`) são
   determinísticos (DL-0035). Fica **desenhado** o seam para IA preditiva futura: a porta de domínio
   `InsightNarrator` (gera a *redação* da recomendação a partir da evidência já calculada) com a
   implementação default **determinística** (`RuleBasedInsightNarrator`) — nenhuma chamada externa,
   nenhuma API key no build/testes. Se um dia um provedor real entrar, ele:
   - fica **atrás dessa porta** (ACL), com **stub determinístico nos testes** (nunca trava o gate);
   - **valida a saída antes de afetar estado** e tem fallback (messaging-and-integrations.md, IA);
   - **versiona** provider/model/prompt e **mascara dado pessoal**, logando como evento de IA;
   - usa o id de modelo Claude mais recente `claude-opus-4-8` para qualquer wiring real.
   A porta NÃO é uma dependência viva do build — é um seam com default determinístico.
3. **Cadência de recomputação = on-event (incremental).** As projeções são atualizadas no consumo de
   cada evento (idempotente, recomputável), não em batch noturno. É o mais simples e dá o insight em
   tempo quase real; recomputação total continua possível (read-model recomputável). Batch fica para
   quando o volume justificar (sem evidência hoje — Rule Zero).
4. **Observabilidade de IA (BR4 / Observability):** `InsightGenerated`/`InsightDecided` logados como
   eventos de negócio (insightId, type, subjectRef, correlationId), e a decisão humana registra
   `decidedBy`/`decidedAt` — é a métrica "aceitos × rejeitados". Sem dado pessoal no payload do
   insight (subject é id/ref de agência, não pessoa física).

## Justificativa

- **Recomendação explícita do ROADMAP (Q4)** e **BR6** da SPEC-0013: Nudge atrás de flag até a tabela
  de faixas; flag por `@Value` reusa o padrão já existente no projeto (sem nova infra de feature flag).
- **DESIGN GUIDANCE / Rule Zero:** não introduzir dependência viva de LLM; um seam atrás de porta com
  stub determinístico satisfaz a regra de IA sem travar o build numa chamada externa.
- **persistence.md (read models recomputáveis)** e **observability.md**: on-event incremental é o
  default simples; só complica (batch/cache) com hotspot medido.

## Alternativas descartadas

- **Implementar o OverrideNudge com faixas inventadas.** Descartada: violaria BR6 e "nunca inventar
  regra de negócio" (Q4 é incógnita do diretor) — geraria dado falso.
- **Wire de LLM real agora.** Descartada: overengineering; sem necessidade (insights determinísticos),
  e arriscaria travar o gate numa dependência externa/credencial.
- **Recomputação em batch noturno.** Descartada como default: adia o insight sem ganho mensurável;
  on-event é mais simples e recomputável.

## Impacto

- Enum `InsightType` com `PROMO_FX_ADVISOR` e `OVERRIDE_NUDGE`; flag `@Value` no serviço.
- Porta `InsightNarrator` + `RuleBasedInsightNarrator` (default determinístico) no módulo intelligence.
- Listener de `PriceOverridden` registrado (curto-circuito sob flag off); teste e2e prova que o Nudge
  **não** gera insight com a flag off (BR6).
- Sem migração além da `V17`. Sem dependência nova de runtime.

## Como reverter / graduar

Reversão **barata**: ligar a flag e implementar o cálculo de distância retroativa quando a tabela de
faixas (Q4) existir — o enum, o listener e o framework já estão prontos. Trocar o narrator
determinístico por um provedor real é implementar a porta `InsightNarrator` num adaptador de
`infra/integration` (ACL), com stub nos testes — sem tocar o domínio.
