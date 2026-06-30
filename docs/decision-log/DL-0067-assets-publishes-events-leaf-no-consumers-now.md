# DL-0067 — Assets publica eventos como módulo-folha; consumidores Finance/Intelligence não são fiados nesta fatia

- **Fase:** 8h (Assets)
- **Spec(s):** SPEC-0021 (Events: `AssetRegistered` consumido por finance/intelligence; `AssetLicenseExpiring` consumido por governança/TI)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A seção *Events* da SPEC-0021 lista, para `AssetRegistered`, os consumidores **`finance` (custo)** e
**`intelligence` (custo fixo/infra)**, e, para `AssetLicenseExpiring`, **governança/TI**. A questão:
esta fatia deve **fiar (wire) esses consumidores** (listeners em finance/intelligence que postam
custo a partir de `AssetRegistered`) ou apenas **publicar** os eventos e deixar a costura para quem
precisar?

## Decisão

**Assets é módulo-folha: apenas PUBLICA os eventos in-process; nenhum consumidor é fiado nesta
fatia.** `AssetRegistered` e `AssetLicenseExpiring` são publicados como eventos de domínio
in-process (Spring `ApplicationEventPublisher`), com a forma definida na spec. **Não** se cria
listener em `finance` nem em `intelligence` agora.

Justificativa central: **lançar custo automático de um ativo no Finance é regra de negócio que a spec
NÃO define** (qual conta/parte, AP ou despesa, em que período, base legal do documento) — e a
SPEC-0021 *Out of Scope* exclui depreciação/contabilidade plena (DL-0065). Fiar um listener que poste
custo seria **inventar regra de negócio** (proibido pela invariante 3 do `CLAUDE.md`). O evento fica
disponível como **seam**: quando o dono definir a política de custo de patrimônio, um listener é
adicionado no módulo consumidor sem tocar em Assets.

## Justificativa

- **`CLAUDE.md` invariante 3 / Regra Zero:** não inventar regra de negócio nem código especulativo;
  o "custo no Finance" do patrimônio precisa de política que a spec não traz.
- **SPEC-0021 Scope:** o vínculo ao Finance é por **id (valor)** — `financeEntryId` no registro —
  para o caso em que o lançamento **já existe**; isso satisfaz BR2 sem precisar de listener.
- **Grafo acíclico (Spring Modulith):** Assets consome **nenhuma** fachada/intern de outro módulo;
  publica eventos que outros podem consumir. Mantém Assets como folha e o grafo acíclico (espelha o
  padrão Billing/Portfolio: DL-0047/DL-0062 — produtores-folha).
- **`messaging-and-integrations.md`:** eventos in-process são o mecanismo de reação assíncrona entre
  módulos; publicar sem consumidor obrigatório é válido (o consumidor reage quando existir).

## Alternativas descartadas

- **Fiar um listener no Finance que poste custo de `AssetRegistered`:** exigiria decidir conta/parte/
  direção/period — regra de negócio inexistente na spec; inventaria comportamento.
- **Não publicar os eventos (só persistir):** contraria a seção *Events* da spec (os eventos são
  contrato); perde o seam de observabilidade/DSS.
- **Criar listener no Intelligence (custo fixo/infra):** o DSS é consumidor-folha que **aconselha**;
  modelar o insight de custo fixo de infraestrutura é escopo da SPEC-0013/futuro, não desta fatia.

## Impacto

- **Specs:** SPEC-0021 — *Business Rules* registra "eventos publicados; consumo por Finance/
  Intelligence é seam futuro (ASSUMIDO ver DL-0067)".
- **Arquivos:** `AssetRegistered`, `AssetLicenseExpiring` (records de evento no módulo `assets`).
  Nenhum arquivo novo em `finance`/`intelligence`.
- **Contratos/Modulith:** Assets não aparece em `allowedDependencies` de outros módulos; o
  `package-info` de Assets não declara dependência de fachadas alheias (folha).

## Como reverter

Quando a política de custo de patrimônio existir: adicionar um listener idempotente no módulo
consumidor (ex.: `finance.internal.AssetEventsListener` consumindo `AssetRegistered`), sem alterar
Assets. Refactoring **barato** e aditivo (espelha como o Finance passou a consumir `SupplierSettled`
na DL-0051).
