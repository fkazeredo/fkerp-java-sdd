# DL-0008 — Manter o nome `Exchange` para o módulo de câmbio (Q1)

- **Fase:** 1 (Núcleo comercial manual)
- **Spec(s):** SPEC-0003 (e SPEC-0011 futura)
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A Parte 13 do redesenho (Q1) deixa em aberto o **nome do contexto de câmbio**: `Exchange`,
`Currency`, `Treasury`? A SPEC-0003 assumiu `Exchange` "a confirmar com o dono".

## Decisão

**Manter `Exchange`** como nome do módulo (`com.fksoft.domain.exchange`), conforme a
recomendação do arquiteto em `docs/ROADMAP.md` ("Recomendações para as Open Questions").

## Justificativa

- É o termo da **linguagem ubíqua** do redesenho e cobre tanto a **taxa** quanto a futura
  **posição de risco** (SPEC-0011, exposição/subsídio×drift) — um dono só.
- `Currency` é estreito demais (sugere só catálogo de moedas); `Treasury` sugere **caixa/tesouraria**,
  fora do escopo deste contexto.
- Segue a recomendação explícita do ROADMAP (modo autônomo, `RUN-PHASE.md`: adotar a recomendação
  quando houver).

## Alternativas descartadas

- **`Currency`** — descartado: nome estreito; não acomoda a posição de risco da Fase 5.
- **`Treasury`** — descartado: evoca gestão de caixa/tesouraria, escopo diferente.

## Impacto

- Pacote `com.fksoft.domain.exchange` e endpoints `/api/exchange/...` já nascem com este nome.

## Como reverter

Renomear o pacote/módulo e o prefixo de rota. Refactor mecânico de tamanho moderado (afeta
imports, rotas e a futura SPEC-0011).
