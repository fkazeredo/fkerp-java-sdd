# DL-0063 — Portfolio: `RepresentationExpiring` por **job de relógio controlado** (alerta de governança), não bloqueio

- **Fase:** 8g (Portfolio — SPEC-0020)
- **Spec(s):** SPEC-0020 (BR5 "Mudança de status/contrato MUST ser auditada; expiração de contrato
  MUST publicar `RepresentationExpiring` (alerta de governança)"; Events `RepresentationExpiring
  {brandRef, validUntil, occurredAt}`; Acceptance "Contrato a vencer gera alerta de governança").
- **ADR relacionado:** messaging-and-integrations.md (jobs importantes: idempotência, instante
  controlado, histórico); **DL-0053** (mesmo padrão de job de relógio controlado do AfterSales);
  observability.md (evento de negócio logado).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A BR5 manda **publicar `RepresentationExpiring`** quando um contrato expira/está a vencer, mas a spec
não define **como** o alerta é disparado (gatilho de tempo), nem **com que antecedência** ("a vencer"),
nem se a expiração **bloqueia** algo.

## Decisão

1. **Job de relógio controlado** (mesmo padrão do AfterSales DL-0053): um método de aplicação
   `flagExpiringContracts(now)` recebe o **instante como parâmetro** (testável, sem depender do relógio
   real) e publica `RepresentationExpiring {brandRef, validUntil, occurredAt}` para cada contrato cujo
   `validUntil` cai dentro da **janela de antecedência** e que ainda não foi sinalizado.
2. **Antecedência padrão = 30 dias** (parâmetro local, value default). "A vencer" = `validUntil` entre
   `now` e `now + 30 dias`; "vencido" (já passou) também sinaliza uma vez.
3. **Idempotente**: cada contrato sinaliza **no máximo uma vez** (flag `expiringSignaledAt` no
   contrato); re-rodar o job não republica. É **alerta**, não bloqueio (coerente com DL-0061: governar
   com rastro, não travar).
4. **Auditoria (BR5)**: registrar/expirar contrato e mudar status de marca gravam `created/updatedAt` +
   ator e logam evento de negócio (brandRef, correlation id) — sem PII (marca/fornecedor não é dado
   pessoal).

## Justificativa

- **ROADMAP "Recomendações"** não cobre a antecedência → adotamos o **padrão interno já validado**
  (DL-0053, AfterSales): job com **instante injetado** + flag idempotente. Consistência de padrão
  (core-principles.md) e testabilidade (relógio controlado, como Booking/AfterSales).
- **30 dias** é o valor mais defensável para "contrato a vencer" em gestão de contratos comerciais
  (antecedência típica para renegociação); é um value default **trivial de mudar** e, se o dono pedir,
  pode virar parâmetro governado da CommercialPolicy depois (como o SLA do AfterSales) — não vale criar
  esse acoplamento agora (Regra Zero).
- Alerta (não bloqueio) segue a tese "fronteira do dinheiro governada, não travada" e a própria BR2/
  DL-0061 (vender sem contrato vigente apenas alerta).

## Alternativas descartadas

- **`@Scheduled` com `Instant.now()` interno.** Descartada: não-testável de forma determinística;
  contraria o padrão de relógio controlado já adotado (DL-0053). O wiring de agendamento real é
  trivial sobre o método (`flagExpiringContracts(clock.instant())`).
- **Antecedência via parâmetro governado (CommercialPolicy) já no v1.** Descartada: acoplaria Portfolio
  a CommercialPolicy por um único número que ninguém pediu para tunar em runtime ainda (Regra Zero).
  Fica como evolução aditiva (mesmo caminho do SLA do AfterSales, DL-0052).
- **Expiração bloqueia a venda da marca.** Descartada: contraria DL-0061 (alerta, não bloqueio) e a
  tese de governar com rastro.

## Impacto

- **Specs:** SPEC-0020 BR5 concretizada como "ASSUMIDO (ver DL-0063): job de relógio controlado,
  antecedência 30d, idempotente, alerta".
- **Arquivos:** `PortfolioService.flagExpiringContracts(now)`; flag `expiringSignaledAt` no
  `RepresentationContract`; evento `RepresentationExpiring`.
- **Migração:** coluna `expiring_signaled_at` em `representation_contracts` (V25).
- **Contratos:** evento `RepresentationExpiring` (consumidor: governança/DSS); opcionalmente um
  endpoint admin para disparar o job manualmente no v1.
- **Modulith:** sem nova dependência (evento de saída próprio do Portfolio).

## Como reverter

Barata: mudar a antecedência é trocar uma constante; trocar por parâmetro governado é reusar o motor
da CommercialPolicy (como AfterSales); ligar o `@Scheduled` real é uma anotação sobre o método. Nada
disso mexe no schema de marcas/metas.
