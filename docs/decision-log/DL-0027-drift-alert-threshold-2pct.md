# DL-0027 — Limite de alerta de drift: |drift| > 2% da exposição estrangeira aberta do livro

- **Fase:** 5 (Câmbio com exposição + relatórios)
- **Spec(s):** SPEC-0011 (BR4 "Quando o drift cruza o limite configurado, publica `BookPositionDrifted`
  (alerta — NÃO bloqueia)"; Validation Rules "limite de drift (BR4) é parâmetro governado (SPEC-0014)";
  Open Questions: "Limite de drift (valor de alerta) — parâmetro governado a definir")
- **ADR relacionado:** 0011 (exceções/erros), 0012 (camadas)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0011 deixa o **valor do limite de alerta de drift** como parâmetro governado a definir (SPEC-0014).
Sem um default defensável, o relatório `LiveExposure` não consegue sinalizar "drift perigoso".

## Decisão

- Default governado: **alerta quando `|markToMarketDrift| > 2%` da exposição estrangeira aberta do livro**,
  onde a base é `Σ |foreignAmount × marketAtFreeze|` das posições OPEN (o valor de mercado da exposição no
  congelamento). É **relativo** (apetite de risco proporcional ao tamanho do livro), conforme a
  "Recomendação para as Open Questions" do ROADMAP.
- O alerta é avaliado sobre o **agregado** (`ExchangeExposure`/`LiveExposure`), **não bloqueia** (BR4):
  marca `driftAlert=true` no read-model e publica `BookPositionDrifted{asOf, markToMarketDrift, threshold}`.
- O ROADMAP admite alternativa de **teto absoluto em BRL** "conforme apetite de risco do diretor": fica
  como configuração futura (SPEC-0014) — o percentual é o default v1, exposto como constante governada no
  serviço (um único ponto a graduar para parâmetro quando a SPEC-0014 existir, como já fez Reconciliation
  com a tolerância em DL-0011).

## Justificativa

- Recomendação explícita do ROADMAP "Recomendações para as Open Questions" → "Limite de alerta de drift
  cambial (SPEC-0011): |drift| > 2% da exposição estrangeira aberta do livro (ou um teto absoluto em BRL,
  conforme apetite de risco do diretor)".
- Limite **relativo** evita falso-alarme em livro pequeno e silêncio em livro grande; é a forma usual de
  expressar apetite de risco de mercado (Value-at-Risk proporcional à exposição).
- Espelha o padrão já adotado em DL-0011 (tolerância de conciliação como constante governada, futura
  SPEC-0014) — consistência de governança.

## Alternativas descartadas

- **Teto absoluto fixo em BRL agora.** Descartada como default: depende do apetite de risco específico do
  diretor (dado de negócio); fica disponível como configuração futura, não como default cego.
- **Limite por posição individual.** Descartada: a spec fala de **posição do livro** (agregado); alertar
  por posição geraria ruído e perderia a visão de carteira que é o ponto do `LiveExposure`.

## Impacto

- Constante `DRIFT_ALERT_PCT = 0.02` no serviço de exposição; cálculo do alerta no read-model
  `LiveExposure`; evento `BookPositionDrifted`. Teste com relógio + feed de mercado controlados provando o
  cruzamento exatamente no limite.

## Como reverter

Reversão **barata**: trocar o valor do default (ou alternar para teto absoluto) é mudar uma constante e o
teste do limite; nenhuma migração nem mudança de contrato. Graduar para parâmetro governado real entra com
a SPEC-0014.
