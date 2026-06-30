# DL-0066 — Alerta de licença a vencer: job de relógio controlado, antecedência 30 dias, idempotente

- **Fase:** 8h (Assets)
- **Spec(s):** SPEC-0021 (BR3, Persistence "Alerta de vencimento por job", Events `AssetLicenseExpiring`)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0021 diz que uma licença com `expiresAt` próximo **MUST** publicar `AssetLicenseExpiring`
(BR3, alerta — não bloqueia) e que o alerta é por **job** (idempotência/locking). Mas **não fixa**:
(a) a **antecedência** (quantos dias antes do vencimento o alerta dispara), (b) o **mecanismo** do job
(como garantir idempotência/locking e testabilidade determinística), nem (c) o **escopo** (a listagem
`?expiringWithinDays=` usa o mesmo horizonte do job?).

## Decisão

1. **Antecedência padrão = 30 dias.** Uma licença é "a vencer" quando `expiresAt` está dentro de
   `LICENSE_EXPIRY_WARNING_DAYS = 30` dias da data de avaliação (ou já passou). O exemplo da própria
   spec usa `GET /api/assets?expiringWithinDays=30`, então 30 é o horizonte default do alerta.
2. **Job de relógio controlado** (mesmo padrão de `BookingService.expirePendingBookings`,
   `AfterSalesService.markBreaches`, `PortfolioService.flagExpiringContracts`): o método de domínio
   `AssetService.flagExpiringLicenses(Instant now)` recebe o **instante como parâmetro** — o
   `@Scheduled` em `infra.jobs` (`AssetLicenseExpiryScheduler`) só fornece o relógio. Isso torna a
   regra **deterministicamente testável** sem mexer no relógio do sistema.
3. **Idempotência por marca de sinalização** (espelha DL-0063/Portfolio): a licença guarda
   `expiry_signaled_at` (timestamptz). O sweep só publica `AssetLicenseExpiring` para licenças
   ainda **não sinalizadas** cujo `expiresAt` está no horizonte; ao publicar, grava
   `expiry_signaled_at`. Um segundo sweep não republica → idempotente, sem locking distribuído
   (monólito de instância única — ADR 0002).
4. **A listagem `?expiringWithinDays=N`** usa o **N do request** (default 30), independente do job:
   é uma leitura ad-hoc ("o que vence nos próximos N dias"), enquanto o job usa o horizonte fixo de
   30 dias para o alerta. Só licenças (type=SOFTWARE_LICENSE) com `expiresAt` entram na listagem.

## Justificativa

- **ROADMAP "Recomendações" (alertas de antecedência):** o padrão do projeto para alertas de
  expiração é **30 dias** — a SPEC-0008 (retenção) usa `compliance.retention.horizon-days:30` e a
  Fase 8g (DL-0063, `RepresentationExpiring`) usa antecedência de 30 dias. 30 dias é o horizonte
  consistente do produto e bate com o exemplo da SPEC-0021.
- **Padrão de relógio controlado:** já estabelecido e testado em Booking/AfterSales/Portfolio —
  reusar evita inventar mecanismo novo (Regra Zero) e mantém os testes determinísticos
  (`architecture/testing.md`: regras sensíveis a tempo MUST ser testadas).
- **Idempotência por flag:** `messaging-and-integrations.md` exige jobs idempotentes; a marca
  `expiry_signaled_at` é o mesmo recurso já validado na DL-0063, sem trazer resilience4j/locks.

## Alternativas descartadas

- **`@Scheduled` lendo `Instant.now()` no domínio:** quebra a testabilidade determinística (teria
  de mockar o relógio do JVM); contraria o padrão do projeto.
- **Republicar o alerta a cada sweep (sem flag):** geraria ruído/duplicidade de eventos; viola a
  idempotência exigida pela spec.
- **Horizonte configurável já no v1 sem default claro:** adia decisão sem necessidade; mantemos 30
  como `@Value` com default (`assets.license.horizon-days:30`), confirmável sem refator.

## Impacto

- **Specs:** SPEC-0021 — BR3 detalhada em *Business Rules* ("ASSUMIDO (ver DL-0066)").
- **Arquivos:** `AssetService.flagExpiringLicenses(Instant)` + `listExpiring(int days)`;
  `AssetLicenseExpiryScheduler` em `infra.jobs`; `AssetLicenseExpiring` event.
- **Migração:** coluna `expiry_signaled_at timestamptz null` + índice parcial em `V26`.
- **Contratos:** `GET /api/assets?expiringWithinDays=N`; `POST /api/assets/flag-expiring` (gatilho
  manual do sweep, espelha `/contracts/flag-expiring` do Portfolio).

## Como reverter

Trocar a antecedência: ajustar `LICENSE_EXPIRY_WARNING_DAYS`/`assets.license.horizon-days`
(parâmetro). Trocar o mecanismo de idempotência: alterar a coluna `expiry_signaled_at`. Refactoring
**barato** e localizado no módulo `assets`.
