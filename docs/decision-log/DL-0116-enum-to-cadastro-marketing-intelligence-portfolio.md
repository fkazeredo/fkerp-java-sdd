# DL-0116 — enum→cadastro fatia 18b: Marketing / Intelligence / Portfolio (reuso do padrão DL-0115)

- **Fase:** 18b (conversão dos enums de referência de Marketing/Intelligence/Portfolio)
- **Spec(s):** SPEC-0031 (cadastro); SPEC-0019 (Marketing), SPEC-0013 (Intelligence), SPEC-0020 (Portfolio)
- **ADR relacionado:** ADR-0019 (padrão enum→cadastro)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A fatia 18a criou o módulo `cadastro` e o padrão enum→cadastro (DL-0115), convertendo os grupos
Admin/Assets/Billing. Faltava converter os grupos de referência de **Marketing, Intelligence e
Portfolio**, reusando exatamente o mesmo mecanismo, sem quebrar o contrato de fio e preservando a
lógica de domínio que **ramifica** por valores específicos (o veredito do advisor, o `GoalMetric` que
dirige a projeção realizada).

## Decisão

Reusa integralmente o padrão de DL-0115. Enums convertidos nesta fatia:

| Módulo       | CadastroType             | Codes (semeados em V34)                          | Ramificação preservada |
|--------------|--------------------------|--------------------------------------------------|------------------------|
| marketing    | `CONSENT_PURPOSE`        | NEWSLETTER                                        | `MarketingCodes.NEWSLETTER` (base de envio, DL-0059); `KNOWN_PURPOSES` na varredura da anonimização LGPD (DL-0058) |
| marketing    | `MARKETING_SUBJECT_TYPE` | ACCOUNT, AGENT                                    | — (valor persistido/chave de consulta) |
| intelligence | `INSIGHT_SUBJECT_KIND`   | AGENCY, ROUTE, PRODUCT, SUPPLIER                  | `IntelligenceCodes.AGENCY` (o único produzido em v1) |
| intelligence | `INSIGHT_TYPE`           | PROMO_FX_ADVISOR, OVERRIDE_NUDGE                  | `IntelligenceCodes.PROMO_FX_ADVISOR` (chave do upsert do insight) |
| intelligence | `INSIGHT_VERDICT`        | CONVERTE, QUEIMA_MARGEM                           | `IntelligenceCodes.CONVERTE/QUEIMA_MARGEM` (regra da guardrail no `isValid`; texto do narrator) |
| portfolio    | `GOAL_METRIC`            | VOLUME, REVENUE                                   | `GoalMetricCodes.VOLUME/REVENUE` (VOLUME←BookingConfirmed, REVENUE←SpreadRealized; forma do alvo e projeção — DL-0062) |

1. **Representação persistida:** cada campo `@Enumerated(STRING)` vira **`String code`** (mesma
   coluna, mesmo valor = nome do antigo constante). Contrato JSON **idêntico** (era string de enum,
   continua string).
2. **DTOs/views/eventos:** os campos viram `String`. Requests de escrita usam `@NotBlank String`
   (Marketing consent purpose/subject-type; Portfolio goal metric).
3. **Validação (só onde há escrita a partir do fio):** Marketing valida `CONSENT_PURPOSE` e
   `MARKETING_SUBJECT_TYPE` no `grantConsent`; Portfolio valida `GOAL_METRIC` no `defineGoal`, via a
   porta pública `CadastroValidator` (código inválido/inativo → `CadastroCodeInvalidException`, 422).
   Os três tipos de Intelligence (`INSIGHT_*`) são **produzidos pelo sistema** (o DSS os cunha a
   partir de eventos consumidos; nunca chegam como payload de criação) — não há validação de escrita,
   mas eles são cadastros para que os rótulos sejam editáveis e as telas mostrem o label.
4. **Direção da dependência (grafo acíclico):** `marketing`/`portfolio` → `cadastro` (porta). O
   `cadastro` continua folha; Modulith acíclico.
5. **Lógica que ramifica (preservada por constantes):** classes `*Codes` no próprio módulo guardam só
   o comportamento cablado — `MarketingCodes`, `IntelligenceCodes`, `GoalMetricCodes`. O `switch` do
   `isValid` (antes exaustivo sobre o enum `Verdict`) vira `switch` sobre as constantes de code com
   `default → false` (veredito desconhecido é inválido, fallback sem persistir).
6. **Rótulo nas telas (retro-fix do seam de 18a):** um serviço/pipe de lookup no frontend
   (`CadastroLabelService`/`CadastroLabelPipe`) busca `GET /api/cadastro/items?type=…`, cacheia o mapa
   code→label por tipo e renderiza o rótulo (fallback para o code até carregar). Aplicado nas telas
   Marketing/Intelligence/Portfolio (escopo exigido); a mesma peça pode ser estendida às demais telas.
7. **Migração V34** semeia os valores atuais como itens (`code`=nome do enum, `label` pt-BR),
   idempotente (`ON CONFLICT DO NOTHING`).

## Justificativa

- **Invariante do dono:** "o valor persistido vira `code` validado com `code`=nome do enum ⇒ JSON de
  contrato inalterado". Mantido byte-a-byte (provado por testes de round-trip).
- **Regra Zero:** um único mecanismo (registry + porta) cobre também estes grupos; as constantes
  `*Codes` existem só onde há ramificação real.
- **Confiança=Alta:** mecânico e testável (round-trip idêntico; rejeição de código inválido/inativo;
  ramificação preservada — veredito da guardrail, projeção REVENUE/VOLUME).

## Alternativas descartadas

- **Validar também os `INSIGHT_*` na escrita.** Descartada: eles não têm escrita a partir do fio (são
  produzidos pelo DSS); validar não agrega e criaria acoplamento desnecessário.
- **Converter a ramificação (veredito/metric) para dado sem constantes.** Descartada: perderia o
  comportamento determinístico (guardrail por veredito; projeção por métrica). Mesmo compromisso de
  DL-0115: cadastro = conjunto+rótulos; constantes = comportamento cablado.

## Impacto

- **Specs:** SPEC-0031 (tabela de tipos 18b marcada como entregue).
- **Arquivos:** enums removidos + `*Codes` novos em `marketing`/`intelligence`/`portfolio`; entidades/
  DTOs/views/eventos/repos/serviços retipados para `String`; `CadastroType` +6 valores;
  `MarketingController`/`IntelligenceController` params `String`. Frontend: `core/cadastro`
  (service+pipe), telas Marketing/Intelligence/Portfolio, i18n `marketing.purpose`.
- **Migração:** **V34** semeia os 6 tipos (13 itens), idempotente.
- **Contratos:** **sem mudança de fio** — campos convertidos continuam `string`; sem novos endpoints.

## Como reverter

Retipar os campos/DTOs de volta aos enums, remover as constantes `*Codes` e a validação, e apagar as
linhas do seed em V34 (migração de baixa). Moderada: os valores no banco são idênticos aos nomes dos
enums, então não há backfill — só refator de tipos.
