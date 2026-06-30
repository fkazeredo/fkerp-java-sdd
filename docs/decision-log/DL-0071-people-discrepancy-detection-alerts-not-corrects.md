# DL-0071 — Divergências de jornada: detecção sinaliza (alerta), nunca corrige sozinho; fila para tratamento humano

- **Fase:** 8i
- **Spec(s):** SPEC-0022 (BR4)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

BR4 manda gerar `JourneyDiscrepancy` para "marcação ímpar/faltante, jornada incoerente" para
**tratamento humano** — sem dizer **quais** classes de divergência o v1 detecta, nem o ciclo de
vida do alerta (estado/fila).

## Decisão

1. **Classes de divergência (enum `DiscrepancyKind`) detectadas no v1:**
   - `ODD_PUNCH` — nº de marcações **ímpar** no período (entrada sem saída correspondente);
   - `MISSING_PUNCH` — marcações **abaixo** do esperado para o período (marcação faltante);
   - `INCOHERENT_JOURNAL` — jornada incoerente: `workedMinutes <= 0` com marcações presentes, ou
     trabalho que excede um teto operacional sanity (ex.: > 24h/dia × dias do período).
2. **Alerta, nunca correção (BR4):** detectar **cria** uma linha `journey_discrepancies` em estado
   `OPEN` e **publica** `JourneyDiscrepancy` (evento in-process, consumido por RH/governança). O
   sistema **não** ajusta marcações nem o saldo — segue o padrão "alerta, não bloqueia" já validado
   (AfterSales SLA — DL-0053, Portfolio expiry — DL-0063).
3. **Fila para tratamento:** `GET /api/people/discrepancies?period=&status=` lista as divergências;
   estado `OPEN|RESOLVED` (resolução é registro manual — quem/quando — sem recálculo automático).
   Idempotência: reprocessar o mesmo `(employee, period)` não duplica divergências da mesma classe.

## Justificativa

- **SPEC-0022 BR4** é explícita: "NÃO corrige sozinho". Detecção determinística + alerta é o
  comportamento correto para dado de ponto (cuja correção é ato humano/jurídico).
- **Marcação ímpar/faltante** são exatamente os exemplos citados pela spec e os erros clássicos de
  espelho de ponto.
- **Padrão do projeto:** alerta idempotente por relógio/recontagem, fila de tratamento — reuso do
  que já existe, sem infra nova (Regra Zero).

## Alternativas descartadas

- **Auto-correção/auto-fechamento de marcação ímpar:** proibido por BR4 e perigoso juridicamente.
- **Bloquear o fechamento da jornada quando há divergência:** a spec quer alerta, não veto; veto de
  fechamento é do Compliance (financeiro), não do RH operacional.
- **Catálogo aberto de divergências (texto livre):** perde determinismo/teste; enum fechado é
  minimização e testável.

## Impacto

- **Specs:** SPEC-0022 — BR4 vira "ASSUMIDO (ver DL-0071)".
- **Arquivos:** enum `DiscrepancyKind`; entidade `JourneyDiscrepancy`; evento `JourneyDiscrepancy`
  (record); detecção no `JourneyCalculator`/`PeopleService`.
- **Migração:** `journey_discrepancies(... kind varchar, status varchar, created_at ...)`.
- **Métricas:** `journey_discrepancies_total`.

## Como reverter

Acrescentar classes de divergência é aditivo (novo valor de enum + regra de detecção). Mudar de
"alerta" para "veto" exigiria reabrir BR4 (decisão de negócio) — mas o estado/fila já suportam.
Reversão **Barata** e localizada no módulo `people`.
