# DL-0035 — PromoFxAdvisor: regra determinística + limites do veredito (CONVERTE × QUEIMA_MARGEM) e ganho estimado

- **Fase:** 7 (Intelligence / DSS)
- **Spec(s):** SPEC-0013 (BR5 veredito CONVERTE × QUEIMA_MARGEM com ganho/risco e proveniência;
  BR7 saída de modelo preditivo validada + fallback; Validation Rules "ArchUnit garante que
  intelligence não chama fachada de comando de outro módulo"; Open Questions: limites de guardrail
  = parâmetros governados SPEC-0014, a confirmar)
- **ADR relacionado:** 0011 (exceções), 0012 (camadas)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0013 (BR5) pede que o `PromoFxAdvisor` classifique **CONVERTE (manter)** × **QUEIMA_MARGEM
(apertar)** "com o ganho/risco estimado", mas **não fixa**: (a) a **fórmula/limite** que separa as
duas classes; (b) como calcular o `estimatedGain`; (c) se a "inteligência" é regra determinística ou
modelo (LLM/ML). Os limites de guardrail estão como "parâmetro governado (SPEC-0014) — confirmar".

## Decisão

1. **Advisor determinístico, baseado em regras** (sem LLM/ML na v1). A inteligência aqui é
   *prescritiva a partir de fatos observados*, não geração de linguagem — logo é testável,
   reproduzível e não depende de chamada externa. (O seam de LLM atrás de porta com stub fica
   registrado em DL-0036, **não** wired nesta fase.)
2. **Sinal por agência** acumulado dos eventos (DL-0034):
   - `accruedSubsidy` = Σ `RateSubsidyAccrued.subsidy` atribuído à agência (custo intencional da promo).
   - `realizedGap` = Σ `FxPositionClosed.totalGap` atribuído à agência (subsídio + drift realizado).
   - `volumeAttracted` = nº de posições/reservas confirmadas da agência no período observado.
3. **Veredito (limite governado, default v1):**
   - **CONVERTE** quando `volumeAttracted ≥ MIN_VOLUME` **e** `realizedGap ≥ 0` (a promoção se pagou:
     o gap total ficou não-negativo, i.e. drift cobriu o subsídio, com volume mínimo atraído).
   - **QUEIMA_MARGEM** quando `realizedGap < 0` **e** `|realizedGap| > BURN_THRESHOLD` (a promo só
     queimou margem além do limite tolerado).
   - **NEUTRO/sem insight** nos demais casos (não gera ruído).
   - Defaults v1: `MIN_VOLUME = 5` posições; `BURN_THRESHOLD = R$ 1.000,00` (em BRL). São **constantes
     governadas** num único ponto do serviço — o mesmo padrão de DL-0011/DL-0027 (graduam para
     parâmetro real quando a SPEC-0014 existir). **Confiança=Média** porque o número exato é apetite do
     diretor; reversão barata (trocar a constante + o fixture do teste).
4. **`estimatedGain` (Money, BRL):**
   - CONVERTE → `estimatedGain = realizedGap` projetado (o ganho de **manter**: o gap positivo já
     observado; quando o gap é exatamente 0 mas o volume é alto, gain = `accruedSubsidy` recuperado).
   - QUEIMA_MARGEM → `estimatedRisk = |realizedGap|` (o que se **deixa de queimar** ao apertar).
   - Os números **carregam a proveniência** (`sources = [RateSubsidyAccrued, FxPositionClosed,
     BookingConfirmed]`) na evidência (BR1/BR5).
5. **Guardrail (BR3 alerta, não bloqueia):** quando `realizedGap < 0` e `|realizedGap|` cruza
   `BURN_THRESHOLD`, o Insight carrega `guardrail` com o limite cruzado — **realça**, nunca impede.
6. **Validação de saída (BR7):** mesmo determinística, a saída é validada antes de persistir (Money em
   BRL, enum de veredito válido, sources não-vazio, ganho coerente com o veredito); saída inválida não
   gera Insight (fallback = nenhum insight + log de fallback), nunca um insight inconsistente.

## Justificativa

- **Rule Zero / DESIGN GUIDANCE da fase:** "Prefira advisors determinísticos, baseados em regra, na
  v1 — testáveis, reproduzíveis, sem dependência externa." Um LLM aqui seria overengineering: a
  decisão é uma classificação aritmética sobre fatos.
- **BR5** pede exatamente um veredito binário com ganho/risco e proveniência — uma regra com limite é
  a expressão mais direta e auditável disso.
- **Consistência de governança:** limites como constante num ponto único espelha DL-0011 (tolerância)
  e DL-0027 (drift 2%), já adotados no projeto, prontos para graduar à SPEC-0014.

## Alternativas descartadas

- **Modelo ML/LLM para classificar.** Descartada na v1: não há dado de treino, adiciona dependência
  probabilística e não-determinismo a uma decisão que é aritmética; contraria o DESIGN GUIDANCE.
- **Limite puramente relativo (% do subsídio).** Considerada; o default absoluto + volume mínimo é
  mais legível para o diretor e evita falso-positivo em agência minúscula. Fica como alternativa de
  graduação na SPEC-0014.
- **Usar `SpreadRealized` como receita atraída.** Descartada nesta fatia (DL-0034 §3): não há
  correlação por booking no payload.

## Impacto

- `PromoFxAdvisor` (domínio puro, value object/serviço sem estado) com os limites como constantes.
- Read-model `Insight` (evidência+recomendação+guardrail). Teste **unitário** com fixtures exatas dos
  dois cenários (subsídio alto/baixo × volume) + proveniência; teste **e2e** dirigido por evento.
- Sem migração além da `V17` (DL-0034). Sem contrato externo novo além do GET de insights.

## Como reverter

Reversão **barata**: ajustar `MIN_VOLUME`/`BURN_THRESHOLD` ou trocar a fórmula de `estimatedGain` é
mudar a constante/método no `PromoFxAdvisor` e os fixtures dos testes — sem migração nem mudança de
contrato. Graduar para parâmetro governado entra com a SPEC-0014.
