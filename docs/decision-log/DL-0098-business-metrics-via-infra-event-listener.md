# DL-0098 — Métricas de negócio sobre eventos JÁ publicados, por um listener em INFRA (domain não conhece Micrometer)

- **Fase:** 11 (Observabilidade & monitoramento)
- **Spec(s):** SPEC-0027 (BR6, BR7)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A fase pede **métricas de negócio significativas** (contadores/temporizadores) "sobre os eventos de
negócio já logados nas fases anteriores — não inventar comportamento novo, instrumentar o que existe".
Faltava decidir **onde** instrumentar sem violar as camadas (ADR 0012 — `domain` não pode depender de
`infra`/Micrometer) e o Modulith, e **quais** eventos.

## Decisão

1. **Onde:** um componente **em infra** — `com.fksoft.infra.observability.BusinessMetrics` — que recebe
   o `MeterRegistry` por injeção e **consome eventos de domínio já publicados** via
   `@EventListener`/`@TransactionalEventListener(phase = AFTER_COMMIT)`, incrementando `Counter`s. É o
   mesmo padrão já validado pelo `PlatformAuditListener` (consome eventos exportados), só que do lado
   de **infra** (que pode depender do domain — ADR 0010/0012) e do Micrometer. O **domain permanece
   puro**: nenhum import de `io.micrometer`/`actuate` no domain (provado por nova regra ArchUnit).
2. **O quê (eventos já existentes):** contadores `acme_*` nomeados por convenção Micrometer:
   - `acme.bookings.confirmed` ← `BookingConfirmed`
   - `acme.bookings.cancelled` ← `BookingCancelled`
   - `acme.quotes.composed` ← `QuoteComposed`
   - `acme.quotes.overridden` ← `PriceOverridden`
   - `acme.billing.invoices.issued` ← `CommissionInvoiceIssued`
   - `acme.finance.periods.closed` ← `PeriodClosed`
   - `acme.identity.logins` ← `UserAuthenticated`
   Todos são eventos **já publicados** pelos serviços de domínio das fases 1–8; nenhum evento/log novo
   é criado. A lista é **aditiva** (pode crescer sem mudar contrato).
3. **Passivo e seguro (BR7):** o listener só conta; roda **após o commit** (não interfere na transação
   de negócio); uma falha de métrica é capturada e logada, **nunca** propaga para a operação. As tags
   carregam só identificadores de baixa cardinalidade (ex.: status), nunca PII.

## Justificativa

- **Tarefa + Regra Zero:** "instrumentar o que existe, não inventar". Consumir eventos já publicados é
  exatamente isso; o domain não muda.
- **ADR 0012 (camadas) + Modulith:** instrumentar **na fronteira infra** mantém a regra ArchUnit
  `domain ↛ infra` verde e não acopla módulos de negócio entre si (o listener é infra, não um módulo
  de domínio). Espelha o `PlatformAuditListener` (consumidor de eventos), que o projeto já usa.
- **Micrometer/Prometheus (oficial):** `Counter` por fato de negócio + nomes com convenção de pontos
  (`acme.bookings.confirmed`) que o registry Prometheus traduz para `acme_bookings_confirmed_total`.
  `@TransactionalEventListener(AFTER_COMMIT)` garante que só conta fatos efetivados.

## Alternativas descartadas

- **`@Timed`/`Counter` dentro dos serviços de domínio:** acoplaria o `domain` ao Micrometer →
  **viola ADR 0012** (e a regra ArchUnit nova falharia). Descartado.
- **AOP/`@Timed` nos controllers (application):** mede chamada HTTP, não o **fato de negócio**
  confirmado (um POST pode 4xx sem confirmar nada). O HTTP já é coberto por
  `http_server_requests_*` do Actuator. Eventos pós-commit medem o fato real.
- **Criar eventos novos só para métrica:** contra a tarefa ("não inventar"). Usa-se o que já existe.
- **Métricas no `application` (delivery):** poderia, mas a observabilidade é "concern" de infra
  (modules-and-apis.md: `infra.<concern>`); `infra.observability` já abriga o correlation id.

## Impacto

- **Specs:** SPEC-0027 BR6/BR7, AC7/AC8/AC10.
- **Arquivos:** `infra/observability/BusinessMetrics.java` (novo listener + counters);
  `architecture/ArchitectureTest.java` (nova regra: `..domain..` não depende de `io.micrometer..` nem
  `org.springframework.boot.actuate..`). `pom.xml` (dep `micrometer-registry-prometheus`).
- **Migração/Contrato:** nenhum. Métricas são aditivas (não são contrato de API versionado).
- **Testes:** `BusinessMetricsIntegrationTest` (uma operação incrementa o counter exposto em
  `/actuator/prometheus`); regra ArchUnit de camadas.

## Como reverter

Remover o `BusinessMetrics` listener (as métricas técnicas do Actuator continuam). O domain não muda
(nunca conheceu o Micrometer). **Barata.**
