# DL-0070 — Banco de horas: saldo mensal (extras/faltas) + janela de compensação CLT configurável (default 6 meses)

- **Fase:** 8i
- **Spec(s):** SPEC-0022 (BR3)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0022 deixa **em aberto** a *Política de banco de horas* (limites, compensação, acordo
coletivo) — "depende de regra trabalhista/negocial". BR3 só fixa que `saldo do período = horas
trabalhadas − jornada contratada` e que "acumula extras/faltas conforme política (parâmetro
governado/legislação — a confirmar)". Falta decidir: granularidade do saldo, sinal (positivo =
extra; negativo = falta), e a janela/limites legais.

## Decisão

1. **Saldo por período mensal**, em **minutos** (inteiro), com sinal:
   `balanceMinutes = workedMinutes − contractedMinutes`. Positivo = **extras**; negativo =
   **faltas/débito** (a CLT admite banco de horas negativo). Exposto na API como `HH:mm` com sinal
   (`+00:20`, `-01:10`), como nos *Input/Output Examples* da spec.
2. **Janela de compensação** como **parâmetro configurável** (`people.timebank.compensation-window-months`),
   **default 6 meses** — o prazo legal do **acordo individual escrito** (CLT art. 59, §5º, pós
   Reforma Trabalhista, Lei 13.467/2017). É informativo/observável nesta fatia (o v1 **não**
   liquida automaticamente extras em folga nem paga +50%: isso é folha, Out of Scope).
3. **Sem trava de jornada** nesta fatia: o limite de 2h extras/dia e 10h diárias (CLT art. 59) é
   **sinalizado** como divergência quando o snapshot indicar (DL-0071), **não** bloqueia — coerente
   com o padrão "alerta, não bloqueia" do projeto (AfterSales SLA, Portfolio).

## Justificativa

- **ROADMAP "Recomendações":** não há linha específica para a política de banco de horas; portanto
  cai na regra 2 (pesquisar fonte oficial) e 3 (valor defensável + Confiança=Baixa).
- **CLT art. 59 (com a Reforma Trabalhista):** acordo **individual escrito** → compensação em até
  **6 meses**; acordo **coletivo** → até **12 meses**; compensação no **mesmo mês** por acordo
  verbal. O default de 6 meses é o mais conservador entre os formais e o mais comum em PME sem
  acordo coletivo. Limite diário de **2h extras** / **10h** de jornada.
- **Regra Zero / "comprar vs. construir":** o módulo é genérico; entregar o **saldo mensal** + a
  janela configurável é o mínimo útil ao RH. Liquidação/pagamento de extras é folha (comprar).

## Alternativas descartadas

- **Janela fixa de 12 meses (acordo coletivo):** menos conservador; assume acordo coletivo que a
  empresa pode não ter.
- **Compensação automática no mês + pagamento de +50% do não compensado:** é **cálculo de folha**
  (Out of Scope); exigiria regras de adicional/feriado/noturno que o módulo não tem.
- **Saldo em horas decimais:** perde fidelidade ao espelho de ponto (que é em minutos) e ao exemplo
  da spec (`HH:mm`).

## Impacto

- **Specs:** SPEC-0022 — move a Open Question "Política de banco de horas" para "ASSUMIDO (ver
  DL-0070)".
- **Arquivos:** `JourneyCalculator` (saldo em minutos com sinal); `TimeBankView`
  (`balance` formatado `±HH:mm`); propriedade `people.timebank.compensation-window-months` (default 6).
- **Migração:** `journeys.balance_minutes int not null`.
- **Contratos:** `GET /employees/{id}/timebank?period=` → `{ period, workedHours, contractedHours,
  balance, discrepancies }`.

## Como reverter

A janela é só configuração (`application.yml`) — barata. Trocar para banco de horas com liquidação
automática/pagamento de adicional exige um sistema de **folha** (comprar/integrar) ou uma nova spec;
o `JourneyCalculator` e as tabelas permanecem (o saldo já é a base). Reversão **Moderada** (entra
módulo/integração de folha por cima do que já existe), não Cara.
