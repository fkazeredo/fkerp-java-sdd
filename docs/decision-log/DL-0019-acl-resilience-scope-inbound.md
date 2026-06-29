# DL-0019 — Escopo de resiliência da ACL de entrada: classificação de falha + observabilidade (sem circuit breaker)

- **Fase:** 3 (Primeira integração real — ACL)
- **Spec(s):** SPEC-0009 (BR5 "classificar falhas"; Observability; cabeçalho "Resiliência … timeout,
  retry, circuit breaker, classificação de falha, ACL — segue `messaging-and-integrations.md`")
- **ADR relacionado:** 0002 (single-instance), 0010 (infra centralizada)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A `messaging-and-integrations.md` lista timeout, retry, **circuit breaker** e fallback como cuidados de
integração. A SPEC-0009 herda essa lista no cabeçalho. Falta decidir **quais** se aplicam à **primeira
ACL**, que é um **webhook de ENTRADA** (o site externo nos chama; nós **não** chamamos para fora nesta
fatia).

## Decisão

- A ACL desta fatia é **inbound (driving adapter)**: traduz um payload externo já recebido e cria um
  Quote. **Não há chamada de saída** a um sistema externo nesta fatia ⇒ **não há timeout/retry/circuit
  breaker a aplicar** (não há dependência externa que possa degradar a aplicação).
- O que **se aplica e é implementado**:
  - **Classificação de falha** (BR5) num enum de domínio
    `IntegrationFailureClass {SIGNATURE_INVALID, PAYLOAD_INVALID, ACCOUNT_NOT_FOUND, DUPLICATE,
    UNKNOWN_ERROR}` — nunca um fallback que finja preço.
  - **Idempotência** por `externalQuotationId` (BR4) via `UNIQUE` em `inbound_quotations`.
  - **Observabilidade** (BR Observability): log de integração com `externalQuotationId`, classe de
    falha, latência e correlation id, **sem dado pessoal** (documento mascarado); métricas
    `inbound_quotations_total` e `integration_failures_total{class}` via Micrometer; health do conector
    exposto por uma capacidade de `Platform` (read-model simples sobre `inbound_quotations`).
- **Sem dependência nova** (resilience4j): não há circuito a abrir. Fica registrado que a primeira ACL
  **de saída** (Locadora Internacional / GDS, SPEC futura; ou o crawler de ponto, SPEC-0012) é quem
  introduz timeout/retry/circuit breaker — lá há chamada externa real.

## Justificativa

- **Regra Zero**: circuit breaker/timeout protegem **chamadas de saída** a dependências instáveis; um
  webhook de entrada não chama ninguém — adicioná-los seria cerimônia sem problema real a resolver.
- A `messaging-and-integrations.md` exige fallback que **não** finja resultado e **classificação** de
  falha — ambos atendidos. A idempotência usa **constraint de banco** antes de "infra de idempotência
  complexa", como manda a própria doc.
- O **mock rastreável** do site externo (`simulation-and-mocking.md`): como o site real está fora de
  escopo, o teste assina o corpo com o mesmo segredo (DL-0016) e prova a tradução ACL + ramo INTEGRATED
  ponta a ponta, referenciando SPEC-0009.

## Alternativas descartadas

- **Introduzir resilience4j (circuit breaker/retry) já aqui.** Descartada: não há chamada de saída;
  dependência e configuração sem alvo (overengineering).
- **Fila/outbox para o inbound.** Descartada: o processamento é síncrono, transacional e idempotente por
  constraint; fila seria infra especulativa para um volume e criticidade que não a justificam (a doc
  manda usar constraint/estado antes de fila).

## Impacto

- `domain.sourcing`: enum `IntegrationFailureClass`; exceções de integração classificadas.
- `infra.integration`: verificador de assinatura, métricas Micrometer, logging de integração.
- `domain.sourcing` / `Platform` (capacidade): health do conector (`connectorHealth`) lendo
  `inbound_quotations`.
- **Sem** mudança de `pom.xml` (nenhuma dependência de resiliência adicionada).

## Como reverter

Quando entrar a primeira ACL **de saída**, adiciona-se timeout/retry/circuit breaker **naquele
adaptador** (e, se útil, a dependência resilience4j). Não afeta esta ACL de entrada. Reversão barata e
aditiva.
