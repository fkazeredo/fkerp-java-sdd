# Plano — Fase 5: Câmbio com exposição + relatórios (SPEC-0011)

> Modo autônomo (RUN-PHASE, FASE-ALVO=5). Estende o módulo `exchange` (SPEC-0003) com a **taxa de
> mercado**, a decomposição **subsídio × drift** e a **posição agregada do livro** (`ExchangeExposure`),
> entregando os primeiros relatórios de câmbio (`PromoFxResult`, `LiveExposure`). Reutiliza a
> proveniência já congelada por Quoting (SPEC-0005) e o `fxGainLoss` por-caso de Reconciliation
> (SPEC-0007) — não duplica a matemática por caso.

## Objetivo

Tornar **mensurável** o efeito do câmbio congelado: separar o gap em **subsídio (custo de promoção
consciente)** e **drift (risco de mercado)**, manter a posição agregada do livro e expor relatórios
descritivos. Vive **dentro do módulo `exchange`** (Rule Zero — sem módulo especulativo): o `exchange`
é o único dono das duas pontas (taxa servida × taxa real da liquidação), então a decomposição pertence
a ele (OVERVIEW 7.2; SPEC-0011).

## Decisões registradas antes do código (decision-log)

| DL | Lacuna (Open Question da SPEC-0011) | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| DL-0025 | Fonte oficial da taxa de mercado (qual feed/provedor) | Porta `MarketRateProvider` + registro **manual** de contingência como caminho v1; provedor real é adapter futuro (ACL). PTAX/intradiário fica para configurar quando o feed real existir. | Média | Moderada |
| DL-0026 | Escopo do congelamento (global × por agência/produto) | **Global por par de moeda** no v1 (alinha SPEC-0003 e a recomendação do ROADMAP/OVERVIEW 7.3). | Média | Moderada |
| DL-0027 | Limite de alerta de drift (parâmetro governado) | **\|drift\| > 2% da exposição estrangeira aberta do livro** (default governado do ROADMAP "Recomendações"); avaliado sobre o agregado, alerta — não bloqueia. | Média | Barata |
| DL-0028 | Gatilho de abertura da `FxPosition` | Abrir ao consumir `BookingConfirmed` (mesma fonte de verdade de Reconciliation), lendo a proveniência congelada do `QuoteSnapshot` (`basePrice` = custo em moeda estrangeira, `pinnedRate`); `marketAtFreeze` = MarketRate vigente no instante da confirmação. | Alta | Moderada |

## Fatias (ordem de dependência)

### Slice 1 — Taxa de mercado (`MarketRate`) + porta + registro manual
- **Entrega:** série temporal append-only `market_rates(pair, rate scale 6 >0, observedAt, source)`;
  porta `MarketRateProvider.marketRateAt(pair, instant)` (Open-Host interno, devolve a observação mais
  recente ≤ instant — BR1); `POST /api/exchange/market-rates` (registro manual de contingência → 201);
  erro `exchange.market-rate.not-found` → 404; `exchange.rate.invalid` → 400 (taxa ≤ 0, reusa).
- **Migração:** `V14__create_market_rates.sql`.
- **Testes:** unit (CurrencyPair + validação de taxa); integração (registro manual → consulta "mercado
  agora"; par sem observação → 404).

### Slice 2 — `FxPosition` (abertura, subsídio, drift, fechamento)
- **Entrega:** agregado `FxPosition {bookingId, foreignAmount, currency, pinnedRate, marketAtFreeze,
  subsidyBrl, settlementRate?, realizedDriftBrl?, totalGapBrl?, status OPEN|CLOSED}`.
  - **Abertura (BR2/BR3):** consumir `BookingConfirmed`; ler `QuoteSnapshot`; `marketAtFreeze` =
    MarketRate vigente; `subsidy = (marketAtFreeze − pinnedRate) × foreignAmount` (escala 2 HALF_UP).
    Publica `RateSubsidyAccrued`. Idempotente por `bookingId` (UNIQUE). Só abre quando há custo em moeda
    estrangeira (basePrice em moeda ≠ BRL) e há MarketRate (senão registra log e não abre — sem inventar).
  - **Drift OPEN (BR4):** `drift = (marketNow − marketAtFreeze) × foreignAmount` (mark-to-market).
  - **Fechamento (BR5):** consumir `FxPositionClosed`? Não — registrar via liquidação: consome o
    `SpreadRealized`/settlement de Reconciliation? Decisão DL-0028: o fechamento usa o
    `supplierSettlementRate` que já existe em Reconciliation. Para não duplicar, o `exchange` escuta um
    evento de liquidação. **Reuso:** Reconciliation passa a publicar a taxa de liquidação num evento que o
    exchange consome (ou o endpoint de settlement chama a porta). Detalhe travado na implementação,
    mantendo a regra "não duplicar o per-case".
  - `realizedDrift = (settlementRate − marketAtFreeze) × foreignAmount`;
    `totalGap = subsidy + realizedDrift == (settlementRate − pinnedRate) × foreignAmount`.
    Publica `FxPositionClosed`.
- **Migração:** `V15__create_fx_positions.sql`.
- **Eventos:** `RateSubsidyAccrued`, `BookPositionDrifted`, `FxPositionClosed`.
- **Testes:** unit/domain do exemplo 7.2 (subsídio 150, drift 150, gap 300, sinais, HALF_UP); subsídio
  negativo (venda acima do mercado); integração (confirma venda → posição aberta com subsídio; liquidação
  fecha com gap). **Regressão:** `totalGap` da posição == `fxGainLoss` (sinal) de Reconciliation.

### Slice 3 — Relatórios: `LiveExposure` (agregado + alerta) e `PromoFxResult(período)`
- **Entrega (read-models/projeções — persistence.md, não força pelo agregado):**
  - `GET /api/exchange/exposure` → `LiveExposure {asOf, openPositions, accruedSubsidy,
    markToMarketDrift, totalExposure, driftAlert}`. Soma `subsidy + driftAtual` das posições OPEN (BR6);
    alerta quando `|markToMarketDrift| > 2% × (Σ |foreignAmount × marketAtFreeze|)` (DL-0027). Publica
    `BookPositionDrifted` quando cruza.
  - `GET /api/exchange/reports/promo-fx?period=YYYY-MM` → `PromoFxResult {period, subsidy, drift,
    totalGap}` agregando as posições do período.
  - `GET /api/exchange/positions/{bookingId}` → posição e sua decomposição → 200 | 404.
- **Testes:** integração com múltiplas posições (agregado soma; alerta cruza no limite com relógio/feed
  controlado); `promo-fx?period=` separa subsídio × drift × gap.

## Convenções obrigatórias
- Módulo `com.fksoft.domain.exchange` (público + `internal`), `@ApplicationModule` já existe.
- Toda `DomainException` registrada em `HttpErrorMapping` (+ teste de completude).
- Flyway a partir de **V14**; nunca editar migração aplicada.
- `Money` scale 2 HALF_UP; taxas scale 6; datas UTC; relógio controlado nos testes.
- i18n pt-BR + fallback en; Testcontainers (Postgres real) nas integrações.
- Colaboração cross-módulo só via fachada/porta pública; Spring Modulith `verify()` verde.
- Observabilidade: logar accrual de subsídio, cruzamento de limite e fechamento como eventos de negócio
  (bookingId, valores, correlation id). Métricas como business-event logging (padrão atual do projeto).

## Definition of Done da fase
- `cd backend && ./mvnw verify` verde (ArchUnit + Spring Modulith + Spotless + Checkstyle).
- Migrações `V14`/`V15` aplicadas e validadas (Postgres real).
- Exemplo 7.2 provado: subsídio 150 na abertura, drift 150 e gap 300 na liquidação a 5,70.
- `GET /exposure` soma posições abertas e alerta no limite; `promo-fx?period=` separa subsídio × drift × gap.
- Specs Open Questions movidas para Business Rules "ASSUMIDO (ver DL-NNNN)".
- Merge `--no-ff` em `develop`; release `0.6.0` (tag, main+develop); release note.
- Caderno de testes em `docs/test-report/` (um por fatia + INDEX).
