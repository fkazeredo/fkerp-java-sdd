# Caderno de testes — Fatia 11-4 · Métricas de negócio (listener em infra)

## Escopo

SPEC-0027 **AC7** (uma operação de negócio incrementa o contador exposto), **AC8** (tag comum
`application`), **AC10** (domain não depende de Micrometer/Actuator — ArchUnit). BR6 (instrumenta só
eventos já publicados, em infra), BR7 (passivo, nunca falha a operação). DL-0098.

## O que foi entregue

- `infra/observability/BusinessMetrics.java` — consumidor de eventos **em infra** que transforma
  eventos de domínio **já publicados** em `Counter`s Micrometer:
  - `acme.bookings.confirmed` ← `BookingConfirmed`
  - `acme.bookings.cancelled` ← `BookingCancelled`
  - `acme.quotes.composed` ← `QuoteComposed`
  - `acme.quotes.overridden` ← `PriceOverridden`
  - `acme.billing.invoices.issued` ← `CommissionInvoiceIssued`
  - `acme.finance.periods.closed` ← `PeriodClosed`
  - `acme.identity.logins` ← `UserAuthenticated`
  Mesmo padrão do `PlatformAuditListener` (`@EventListener`). Falha de métrica é capturada/loga,
  nunca propaga (BR7). Contadores pré-registrados no construtor (aparecem na raspagem desde o início).
- `ArchitectureTest.DOMAIN_MUST_NOT_DEPEND_ON_METRICS_OR_ACTUATOR` — nova regra ArchUnit (17ª):
  `..domain..` não depende de `io.micrometer..` nem `org.springframework.boot.actuate..`.

## Casos de teste

### Integração (Testcontainers + segurança real + `@AutoConfigureObservability`)

`BusinessMetricsIntegrationTest`:
- **aLoginIncrementsTheBusinessCounterExposedInPrometheus** — um login real publica
  `UserAuthenticated`; a raspagem `/actuator/prometheus` (token ROLE_IT) contém
  `acme_identity_logins_total` com valor ≥ 1 e a tag `application="acme-travel-erp"`. →
  **AC7, AC8, BR6, BR7**.

### Arquitetura (ArchUnit)

- **DOMAIN_MUST_NOT_DEPEND_ON_METRICS_OR_ACTUATOR** (nova) — prova que o domínio não importa
  Micrometer/Actuator. Suíte `ArchitectureTest` agora com **17 regras**; o `ArchitectureRulesHaveTeethTest`
  (planted-violation) segue verde. → **AC10, BR6**.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. **477 testes** (475 + 2 novos), 0 falhas. ArchUnit
17 regras verdes; Spotless limpo; Checkstyle 0 violações.

```
Tests run: 477, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Cobertura / o que NÃO está coberto

- O teste exercita o evento **`UserAuthenticated`** (gatilho mais barato e determinístico). Os demais
  contadores seguem o **mesmo mecanismo** (`@EventListener` → `Counter.increment()`), sobre eventos já
  cobertos pelos testes de cada módulo nas fases anteriores; a lista é **aditiva** (novos contadores
  não mudam contrato).
- A visualização do painel "Business Events" no Grafana é operacional (fatia 11-3).

## Como reproduzir

```bash
cd backend
./mvnw -o test -Dtest='BusinessMetricsIntegrationTest,ArchitectureTest,ArchitectureRulesHaveTeethTest'
./mvnw clean verify
```
