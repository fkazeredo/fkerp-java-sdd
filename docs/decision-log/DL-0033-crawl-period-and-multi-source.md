# DL-0033 — Periodicidade do crawl (diário, período corrente `YYYY-MM`) e múltiplos REPs/filiais por `sourceRef` (lista configurável, default um)

- **Fase:** 6 (Crawler de ponto)
- **Spec(s):** SPEC-0012 (Open Questions: "Periodicidade do crawl e janela de coleta — confirmar com o RH";
  "Múltiplos REPs/filiais (vários `sourceRef`) — suportado pelo modelo; confirmar a lista real"; BR5
  idempotência por `(sourceRef, periodRef)`)
- **ADR relacionado:** 0002 (single-instance)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0012 deixa em aberto **com que frequência** o crawl roda, **qual janela/período** coleta, e a **lista real
de REPs/filiais** (`sourceRef`). São dados que só o RH/cliente confirma; afetam só configuração, não o modelo.

## Decisão

- **Periodicidade:** o job `point-clock-crawl` roda **diariamente** (agendado em `infra/jobs`, intervalo
  configurável por propriedade, default 24h), coletando o **período corrente** (`periodRef = YYYY-MM` do mês
  vigente, no `Clock` injetado). Disparo **manual** adicional por `POST /api/integration/point/crawl` (papel
  operacional/TI), como a spec prevê.
- **Janela:** a coleta sobrescreve/atualiza o snapshot do período corrente; a **idempotência por
  `(sourceRef, periodRef)`** (BR5) garante que recoletar o mesmo período **não duplica** (UNIQUE no banco +
  pré-checagem; o re-run atualiza marcações/itens, não cria linha nova).
- **Múltiplos REPs/filiais:** a **lista de `sourceRef` é configurável** (propriedade
  `point-clock.sources`, default um único `REP-DEFAULT`); o job itera a lista e coleta cada `sourceRef`. O modelo
  (chave composta `(sourceRef, periodRef)`) já suporta N filiais sem mudança de schema.

## Justificativa

- **Valor mais defensável** (ordem 3 do `RUN-PHASE`, dado que é do negócio): diário cobre o uso de RH (ver
  jornada/faltas no dia a dia) sem martelar o portal; período corrente é o recorte natural do espelho mensal;
  uma fonte default evita inventar filiais que não existem, e a lista configurável não trava nada.
- **Tudo é configuração**, não modelo: mudar para horário, semanal, ou N filiais é trocar propriedade — daí
  Reversibilidade=Barata. Confiança=Baixa porque a periodicidade/lista real é decisão de RH ainda não dada.
- **Idempotência por período** (BR5) torna a frequência segura: rodar mais vezes não corrompe nem duplica.

## Alternativas descartadas

- **Coletar todos os meses/histórico a cada run.** Descartada: desnecessário e caro; o operacional interessa no
  período corrente, e o legal (AFD) tem caminho próprio (upload).
- **Hardcodar uma lista de filiais.** Descartada: inventaria dado de negócio (proibido); a lista fica em
  configuração com default mínimo.
- **Sem job agendado (só manual).** Descartada: a spec pede job agendado com histórico (BR7); o manual é extra.

## Impacto

- **Arquivos:** `PointClockCrawlScheduler` (`infra/jobs`, intervalo + lista de `sourceRef` por propriedade),
  `PointClockCrawler` itera as fontes; `periodRef` derivado do `Clock`.
- **Config:** `point-clock.sources` (default `REP-DEFAULT`), `point-clock.crawl.interval-ms`,
  `point-clock.crawl.initial-delay-ms`.
- **Migrações:** `point_snapshots` já tem UNIQUE `(source_ref, period_ref)` (V16) — suporta N filiais.

## Como reverter

Reversão **barata**: ajustar propriedades (intervalo, lista de `sourceRef`) quando o RH confirmar; nenhum
schema ou contrato muda.
