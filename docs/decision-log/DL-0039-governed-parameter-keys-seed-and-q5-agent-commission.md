# DL-0039 — Conjunto de `parameterKey` governados, seed SYSTEM_DEFAULT (BR4) e Q5 (comissão do agente como parâmetro por escopo)

- **Fase:** 8a (CommercialPolicy)
- **Spec(s):** SPEC-0014 (BR4 sempre há SYSTEM_DEFAULT; Persistence "seed dos SYSTEM_DEFAULT";
  Open Questions **Q5** e "conjunto final de parameterKey"); SPEC-0007 (tolerância), SPEC-0011
  (limite de drift), SPEC-0005 (markup)
- **ADR relacionado:** 0011, 0012
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0014 diz que o **conjunto final de `parameterKey`** depende de decisões de negócio e que o
seed cobre "os já usados". A **Q5** pergunta o **escopo da comissão do agente** (SPEC-0004/0014). É
preciso decidir **quais chaves** entram com `SYSTEM_DEFAULT` agora (BR4) e **como Q5 é modelada** sem
estourar o escopo (Commissioning/SPEC-0004 não é desta fatia).

## Decisão

1. **Seed dos `SYSTEM_DEFAULT` (BR4)** — só as chaves **já usadas** pelo sistema hoje, com os
   defaults recomendados no ROADMAP ("Parâmetros governados — defaults recomendados"):
   - `MARKUP_PCT` = `0` (PERCENT) — DL-0009 (markup default 0; a margem primária é o spread).
   - `FX_DRIFT_LIMIT` = `0.02` (PERCENT) — DL-0027 (alerta de drift |drift| > 2%).
   - `RECON_DISCREPANCY_TOL` = `1.00` (MONEY BRL) — DL-0011 (tolerância = max(R$1,00; 0,5% do spread);
     o componente **absoluto** R$1,00 entra como default governado; o relativo continua no
     Reconciliation, que pode passar a consultar este parâmetro numa fatia futura sua).
   Todas em **escopo global** (`scope_account_id/product_ref/channel` nulos), camada `SYSTEM_DEFAULT`,
   `defined_by = 'system-seed'`, `valid_from` = data de baseline. **Resolução nunca fica vazia** para
   essas chaves (BR4).
2. **Q5 (comissão do agente) — modelar como parâmetro governado por escopo, default global**, exatamente
   a Recomendação do ROADMAP (Q5): a chave `AGENT_COMMISSION_PCT` é **reservada** como governável pelo
   **mesmo** motor (escopo agência > produto > global), reusando esta engine de precedência. **NÃO**
   implemento o consumo no Commissioning nesta fatia (é SPEC-0004/0014 futura, fora de escopo,
   `simulation-and-mocking.md`): **não** semeio `AGENT_COMMISSION_PCT` agora (não é "já usada" e seria
   inventar comportamento), mas deixo o motor **pronto** para recebê-la sem mudança de modelo — é só uma
   nova linha quando a fatia dona chegar. A Open Question Q5 **continua aberta na SPEC-0004**; aqui só
   se registra que o mecanismo de governança a comporta.
3. **Open-Host (BR6):** drift (SPEC-0011) e tolerância (SPEC-0007) ficam **expostos** via `resolve`
   para quem quiser migrar de constante para parâmetro governado — sem que esta fatia altere aqueles
   módulos (eles seguem com suas constantes até a fatia que escolher consumir).

## Justificativa

- **BR4** exige SYSTEM_DEFAULT para toda key **usada**; semear só o que existe evita inventar chaves
  especulativas (`simulation-and-mocking.md`: não crescer framework para consumidor inexistente).
- **Q5 = Recomendação do ROADMAP**: "Parâmetro governado por escopo (agência > produto > global),
  reusando o motor de precedência da SPEC-0014; default global". O modelo de escopo de DL-0037
  comporta isso **sem mudança** — exatamente o "reaproveita o que já existe" da recomendação.
- **Confiança=Média:** os valores default (0; 2%; R$1,00) são apetite do diretor/contador (carregam a
  marca dos DLs de origem); reversão barata (trocar o seed/linha).

## Alternativas descartadas

- **Semear todas as chaves do catálogo futuro (SLA, ISS, etc.).** Descartada: chaves de specs ainda
  não construídas (0016/0018…) — inventar default seria fora de escopo e sem consumidor (Rule Zero /
  simulation-and-mocking).
- **Implementar `AGENT_COMMISSION_PCT` no Commissioning agora.** Descartada: SPEC-0004/0014 é fatia
  própria; aqui só se garante que a engine a comporta (Q5 segue aberta na sua spec).
- **Mover a tolerância de conciliação inteira para cá.** Descartada nesta fatia: só o componente
  **absoluto** vira default governado; a fórmula composta continua no Reconciliation até ele optar por
  consumir o parâmetro.

## Impacto

- Seed em **V18** (3 linhas SYSTEM_DEFAULT). `ParameterKey` como value object validado (formato
  `A-Z_`); o motor recusa `resolve` de key sem SYSTEM_DEFAULT → `policy.parameter.unknown` (404).
- Nenhuma mudança em Commissioning/Reconciliation/Exchange nesta fatia (Open-Host só **expõe**).
- A spec 0014 move Q5 (parte "o motor comporta") e Q8 para Business Rules como ASSUMIDO (ver DL); a
  parte de Q5 que é decisão de Commissioning permanece aberta na SPEC-0004.

## Como reverter

Reversão **barata**: o seed é linhas numa migração idempotente. Trocar um default = nova migração de
`UPDATE`/insert (nunca editar a `V18` aplicada). Adicionar `AGENT_COMMISSION_PCT` quando a fatia dona
chegar é uma linha de seed + o consumo no Commissioning — sem tocar a engine.
