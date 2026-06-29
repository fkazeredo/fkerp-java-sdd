# DL-0025 — Fonte da taxa de mercado: porta `MarketRateProvider` + registro manual de contingência

- **Fase:** 5 (Câmbio com exposição + relatórios)
- **Spec(s):** SPEC-0011 (Scope: "ingestão da taxa de mercado por par … via porta `MarketRateProvider`
  + registro manual de contingência"; BR1; Open Questions: "Fonte oficial da taxa de mercado")
- **ADR relacionado:** 0010 (camada de infraestrutura), 0001 (monólito modular)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0011 deixa em aberto **qual provedor/feed** fornece a taxa de mercado e **qual fechamento**
(PTAX vs intradiário). O negócio (diretor/financeiro) é quem fecha isso com a corretora/fonte oficial;
não é dado que o software possa inventar.

## Decisão

- Modelar a entrada da taxa de mercado por **uma porta de domínio `MarketRateProvider`** (hexagonal):
  o domínio depende da abstração "qual é a taxa de mercado vigente para o par em um instante", não de um
  provedor concreto.
- No v1, o caminho **operacional** é o **registro manual de contingência**: `POST /api/exchange/market-rates`
  grava uma observação `MarketRate{pair, rate, observedAt, source=MANUAL}`. Um feed externo real (PTAX,
  GDS, corretora) entra **depois** como um **adapter** que implementa a porta (ACL: o DTO do provedor não
  vaza para o domínio), em job com idempotência/locking — **sem dívida** e sem refator do domínio.
- A escolha PTAX × intradiário é **configuração do adapter futuro**, não do modelo: a série temporal
  `market_rates` aceita qualquer origem e a regra "mercado agora = observação mais recente ≤ now" (BR1)
  independe da fonte.

## Justificativa

- SPEC-0011 Scope nomeia exatamente a porta `MarketRateProvider` + registro manual — esta decisão é a
  tradução fiel, não uma invenção.
- `simulation-and-mocking.md` / Rule Zero: adiar a integração externa com uma **costura rastreável**
  (porta + registro manual) é o padrão do projeto (igual ao `ExchangeRateProvider` da SPEC-0003 e à ACL
  da SPEC-0009), e evita acoplar o domínio a um feed que o negócio ainda não confirmou.
- Mantém a Fase 5 entregável e testável (feed fake nos testes) sem travar na escolha de provedor.

## Alternativas descartadas

- **Cravar um provedor concreto agora (ex.: PTAX do BCB).** Descartada: o negócio não confirmou a fonte;
  acoplaria o domínio a um contrato externo e a uma cadência (D+1 do PTAX × intradiário) ainda indefinida.
- **Sem registro manual, só feed.** Descartada: deixaria a fase sem caminho operacional/testável e sem
  contingência quando o feed cair (a spec pede explicitamente o registro manual).

## Impacto

- Porta `MarketRateProvider` (público do módulo `exchange`); entidade `MarketRate` + repositório
  (`internal`); `MarketRateService`; `POST /api/exchange/market-rates`; migração `V14__create_market_rates.sql`;
  erro `exchange.market-rate.not-found` (404) + i18n.

## Como reverter

Reversão **moderada**: ligar um feed real é **adicionar** um adapter que implementa a porta (sem mexer no
domínio nem na API manual, que permanece como contingência). Trocar a semântica de "mercado agora" exigiria
nova regra na spec.
