# Plano — Fase 8h: Assets (Patrimônio Interno) · SPEC-0021

> Registro **enxuto** do patrimônio interno da Acme (equipamentos, licenças, outros bens) com ciclo
> de vida ACTIVE→RETIRED, vínculos por valor (documento no Compliance, lançamento no Finance) e o
> alerta de licença a vencer. **Não** é gestão de ativos plena (sem depreciação) — DL-0065. Convenção:
> prosa em pt-BR, código em inglês. Release alvo: **0.16.0**.

## Decisões já registradas (decision-log)

| DL | Decisão | Conf. | Rev. |
|---|---|---|---|
| DL-0064 | Assets é contexto separado (Q2: dois contextos); 18º módulo Modulith; registro enxuto | Alta | Moderada |
| DL-0065 | Sem depreciação/gestão plena (Out of Scope); registro + seam comprar-vs-construir | Alta | Moderada |
| DL-0066 | Alerta de licença a vencer: job de relógio controlado, 30d, idempotente por `expiry_signaled_at` | Média | Barata |
| DL-0067 | Assets é folha: publica `AssetRegistered`/`AssetLicenseExpiring`; sem consumidores fiados | Média | Barata |
| DL-0068 | Baixa auditada inline; ACTIVE→RETIRED terminal; re-baixar → 409 | Média | Barata |

## Fronteiras de arquitetura (inegociáveis)

- Novo módulo `com.fksoft.domain.assets` com `package-info.java` anotado `@ApplicationModule`
  (18º módulo de negócio). **Folha**: não depende de fachada/internal de outro módulo de negócio;
  usa só os kernels `money` e `error`. Grafo Modulith **acíclico** (verify verde).
- Domínio puro: agregado, value objects, exceções, eventos, repositório (interface Spring Data),
  fachada `AssetService`. Sem dependência de `application`/`infra` (ArchUnit).
- Delivery: `AssetsController` em `application.api` + DTOs em `application.api.dto` (entity-free).
- Infra: `AssetLicenseExpiryScheduler` em `infra.jobs` (só fornece relógio/horizonte ao domínio).
- Sem FK cross-contexto: `documentId` e `financeEntryId` são **valores** (uuid), não FKs.
- `DomainException` com `code == chave i18n`; mensagens pt-BR + fallback en-US. Sem exceção crua de
  banco vazando (índice único → erro de negócio traduzido).
- Migração `V26__create_assets.sql` idempotente; nunca editar migração aplicada.
- OpenAPI atualizada; observabilidade (evento de negócio logado, correlation id; sem dado pessoal —
  patrimônio não tem PII).

## Modelo de domínio

```
Asset (aggregate root, tabela assets)
  id: UUID (PK)
  type: AssetType { EQUIPMENT, SOFTWARE_LICENSE, OTHER }
  identifier: String (descrição/identificação, obrigatório)
  status: AssetStatus { ACTIVE, RETIRED }  (nasce ACTIVE)
  acquisitionDate: LocalDate (obrigatório)
  acquisitionCost: Money (amount numeric(18,2) + currency)  (obrigatório)
  expiresAt: LocalDate (null; OBRIGATÓRIO p/ SOFTWARE_LICENSE — BR1)
  supplierRef: String (null)
  documentId: UUID (null; Compliance, valor — BR2)
  financeEntryId: UUID (null; Finance, valor — BR2)
  expirySignaledAt: Instant (null; idempotência do alerta — DL-0066)
  retiredAt / retiredBy / retirementReason  (null até a baixa — BR4/DL-0068)
  createdAt/updatedAt/createdBy/updatedBy, version (@Version)

  Métodos de domínio:
    register(...)            valida BR1 (expiresAt p/ licença); nasce ACTIVE
    retire(reason, now, by)  guard terminal: já RETIRED → AssetAlreadyRetiredException
    signalExpiringIfDue(now, horizonDays)  marca expirySignaledAt e devolve true (idempotente)
    isLicenseExpiringWithin(asOf, days)    leitura p/ a listagem ?expiringWithinDays
    toView()
```

Value/view records (módulo `assets`): `AssetType`, `AssetStatus`, `AssetView`,
`RegisterAssetCommand`. Exceções: `AssetNotFoundException` (404), `AssetInvalidException` (400,
dados obrigatórios), `LicenseExpiryRequiredException` (400 `assets.license.expiry-required`),
`AssetAlreadyRetiredException` (409). Eventos: `AssetRegistered {assetId, type, occurredAt}`,
`AssetLicenseExpiring {assetId, expiresAt, occurredAt}`.

## API (REST)

| Método | Rota | Resultado |
|---|---|---|
| POST | `/api/assets` | 201 + `AssetView` (status ACTIVE) |
| GET | `/api/assets/{id}` | 200 `AssetView` / 404 `assets.asset.not-found` |
| GET | `/api/assets?type=&status=&expiringWithinDays=` | 200 lista `AssetView` (filtros combináveis) |
| POST | `/api/assets/{id}/retire` `{reason}` | 200 `AssetView` (RETIRED) / 409 já baixado |
| POST | `/api/assets/flag-expiring` | 200 `{flagged}` (gatilho do sweep; espelha Portfolio) |

Erros: `assets.asset.not-found` (404), `assets.license.expiry-required` (400),
`assets.asset.invalid` (400), `assets.asset.already-retired` (409).

## Persistência — `V26__create_assets.sql`

Tabela `assets` conforme spec + `expiry_signaled_at`, `retired_at`, `retired_by`,
`retirement_reason`. Índices: por `(type, status)` para a listagem; índice **parcial** em
`expires_at WHERE type='SOFTWARE_LICENSE' AND status='ACTIVE' AND expiry_signaled_at IS NULL` para o
sweep. Sem FK para outros contextos.

## Fatias (ordem de dependência)

### Fatia 8h-1 — Registro + ciclo de vida (BR1/BR2/BR4/BR5)
- **RED:** teste unitário de domínio (`expiresAt` obrigatório p/ licença; transição RETIRED; re-baixar
  falha) + teste de integração HTTP (`TestRestTemplate`): POST cria ACTIVE e publica `AssetRegistered`;
  POST licença sem `expiresAt` → 400 `assets.license.expiry-required`; GET inexistente → 404; retire →
  200 RETIRED; re-retire → 409; listagem por `type`/`status`. Regressão: Assets não tem rota comercial
  (não há endpoint de preço/venda — BR5).
- **GREEN:** módulo `assets` (agregado, VOs, exceções, repo, service, eventos), migração V26,
  controller + DTOs, i18n (pt-BR + fallback), OpenAPI, `package-info` `@ApplicationModule`.
- **GATES + DoD:** ArchUnit/Modulith/Spotless verdes; `./mvnw verify` verde; caderno de testes;
  commits Conventional. Merge `--no-ff` na integração; re-verify.

### Fatia 8h-2 — Alerta de licença a vencer (BR3/DL-0066)
- **RED:** integração — registrar licença com `expiresAt` em 10 dias; `flagExpiringLicenses(now)`
  publica `AssetLicenseExpiring` **uma vez** (idempotente no 2º sweep); licença distante (90 dias) não
  dispara; `GET ?expiringWithinDays=30` lista a licença a vencer e exclui as fora do horizonte e as
  não-licenças; o `AssetLicenseExpiryScheduler` está fiado.
- **GREEN:** `AssetService.flagExpiringLicenses(Instant)` + `listExpiring(int days)` +
  `signalExpiringIfDue`; `AssetLicenseExpiryScheduler` em `infra.jobs`; índice parcial já em V26.
- **GATES + DoD:** idem; caderno de testes; merge `--no-ff`.

## Testes (proporcionais)

- **Unit/domain:** `AssetTest` — BR1 (licença exige `expiresAt`), RETIRED terminal, `signalExpiring`
  idempotente, leitura `isLicenseExpiringWithin`.
- **Integração (Testcontainers/Postgres real):** `AssetApiIntegrationTest` (jornada REST + sad paths) e
  `AssetLicenseExpiryIntegrationTest` (sweep + `AssetRegistered`/`AssetLicenseExpiring` via
  `@RecordApplicationEvents`; listagem `expiringWithinDays`).
- **Arquitetura:** ArchUnit (domínio não depende de application/infra; sem setters em @Entity; sem
  *Impl) + Spring Modulith `verify()` (18º módulo, acíclico). Opcional: regra "Assets não comanda o
  fluxo comercial" espelhando a do Portfolio — adicionada se couber sem ruído.
- **Smoke:** `/api/system/health` já coberto pela fundação (não regride).

## Definition of Done (por fatia)

Critérios de aceite viram teste e passam; `./mvnw verify` verde (ArchUnit + Modulith); V26
idempotente; `DomainException` code==i18n; sem exceção de banco vazando; OpenAPI atualizada;
observabilidade (evento logado, correlation id); Spotless aplicado; Conventional Commits; caderno de
testes atualizado antes do merge.

## Documentação (fim da fase)

- `docs/test-report/8h-1.md`, `8h-2.md` + INDEX.
- `docs/release-notes/0.16.0.md` (pt-BR) + append em `docs/release-notes/CHANGELOG.en-US.md`.
- `docs/MANUAL.md` (pt-BR) + `docs/MANUAL.en-US.md` em sincronia (nova capacidade: cadastro de
  patrimônio, baixa, alerta de licença a vencer).
- Relatório final em `docs/relatorio-fase-8h.md`.

## Riscos / fora de escopo

- Custo automático no Finance e insight de custo fixo no DSS ficam como **seam** (eventos publicados,
  sem consumidor) — DL-0067. Depreciação/manutenção/estoque = comprar (DL-0065). Frontend Angular do
  Assets não está no escopo desta spec (entrega é registro/API; UX profissional é a Fase 10).
