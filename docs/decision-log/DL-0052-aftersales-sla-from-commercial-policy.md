# DL-0052 — AfterSales: SLA como parâmetro governado, resolvido pela CommercialPolicy

- **Fase:** 8e (AfterSales — SPEC-0018)
- **Spec(s):** SPEC-0018 (BR1 prazos de SLA derivam do type/política — parâmetro governado;
  BR4 SLA due/breached). SPEC-0014 (motor de precedência — CommercialPolicy).
- **ADR relacionado:** 0011/0012; DL-0037 (`ParameterRule` + motor de precedência),
  DL-0039 (seed SYSTEM_DEFAULT só das chaves usadas), DL-0040 (consumo Open-Host do resolve).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0018 deixa a **Política de SLA por tipo de caso** como Open Question ("parâmetro
governado a definir — CommercialPolicy"). Faltava (a) os **valores** dos prazos; (b) **como**
o AfterSales obtém esses prazos sem hardcode; (c) a **unidade/tipo** do parâmetro.

## Decisão

1. **Prazos (defaults):** 1ª resposta **24h**, resolução **72h**, cancelamento/reembolso **48h**
   — exatamente a recomendação do ROADMAP ("Recomendações para as Open Questions", linha 317).
2. **Resolução via CommercialPolicy (SPEC-0014), sem hardcode.** O `AfterSalesService` resolve
   três chaves governadas pelo `CommercialPolicyService.resolve(key, scope)`:
   - `AFTERSALES_SLA_FIRST_RESPONSE` = `24` (horas até a 1ª resposta);
   - `AFTERSALES_SLA_RESOLUTION` = `72` (horas até resolver, default por tipo);
   - `AFTERSALES_SLA_REFUND` = `48` (horas para casos de cancelamento/reembolso).
   Tipo do parâmetro = **NUMBER** (horas, decimal). Escopo da consulta = `ParameterScope.global()`
   no v1 (o motor já permite especializar por conta/produto/canal sem refatorar — Q5/DL-0039).
3. **Seed SYSTEM_DEFAULT** das três chaves no `parameter_rules` (mesma tabela da SPEC-0014, V18),
   via nova migração **V23** com UUIDs fixos (idempotente), exatamente como o seed de DL-0039.
   Assim `resolve` nunca volta vazio para essas chaves (BR4 da SPEC-0014).
4. **Qual chave governa qual prazo:** o `dueAt` de resolução usa `AFTERSALES_SLA_REFUND` para
   `CANCELLATION_REQUEST`/`REFUND_REQUEST` (48h) e `AFTERSALES_SLA_RESOLUTION` para os demais
   (72h). O `firstResponseDueAt` usa sempre `AFTERSALES_SLA_FIRST_RESPONSE` (24h).

## Justificativa

- O ROADMAP recomenda exatamente esses prazos e que sejam parâmetro governado; o supervisor
  reforça "GOVERNED PARAMETERS — reuse the CommercialPolicy precedence engine".
- Reusar o motor da SPEC-0014 evita um segundo mecanismo de configuração (Rule Zero) e dá de
  graça precedência/escopo/auditoria/efetividade — uma **Diretiva** pode sobrepor o SLA sem
  deploy (provado por teste em 8e-2).
- `NUMBER` (horas) é a representação mais simples e legível; `Duration.ofHours(asDecimal)` no
  consumidor. Evita inventar um tipo `DURATION` no motor.

## Alternativas descartadas

- **Hardcode dos prazos no domínio AfterSales.** Descartada: o supervisor e a spec pedem
  parâmetro governado; hardcode impede o diretor de repactuar SLA por marca/fornecedor.
- **Novo módulo/tabela de SLA própria do AfterSales.** Descartada: duplicaria o motor de
  parâmetros governados (overengineering); a CommercialPolicy já é o dono desse tipo de regra.
- **Tipo `MONEY`/`PERCENT`.** Descartada: SLA é tempo; `NUMBER` (horas) é o ajuste natural.

## Impacto

- **Specs:** SPEC-0018 — Open Question "Política de SLA por tipo" → **Business Rules** como
  "ASSUMIDO (ver DL-0052)".
- **Arquivos:** `AfterSalesService` (resolve as 3 chaves), chaves em `ParameterKey` se úteis;
  migração `V23` (seed). Sem mudança no motor da SPEC-0014 (consumo Open-Host).
- **Migrações:** `V23` adiciona 3 linhas SYSTEM_DEFAULT em `parameter_rules` (idempotente).
- **Contratos:** nenhuma quebra; `dueAt` no response do caso.

## Como reverter

Barata: trocar os valores é um `UPDATE`/nova `ParameterRule` (runtime, sem deploy). Mudar a
representação (ex.: para minutos) é trocar a unidade no consumidor e no seed — refator local de
uma classe e uma migração aditiva.
