# DL-0127 — Emulação de integração: adaptador HTTP real + emulador (dev e testes) + breaker reutilizável

- **Fase:** 19e (Refactoring de maturidade — emulação das integrações)
- **Spec(s):** SPEC-0016 (NFS-e; BR3/BR7); `architecture/messaging-and-integrations.md` (resiliência)
- **ADR relacionado:** DL-0046 (porta NfseGateway + mock rastreável); DL-0031 (breaker in-process)
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

Os adaptadores de integração eram **mocks in-process** (Java puro): `SimulatedMunicipalNfseService`,
`MockPaymentGateway`, `SimulatedNewsletterSender`, `SimulatedPointClockSource`. Eles provam a
tradução da ACL, mas **nunca exercitam HTTP real** — timeout, retry e circuit breaker de saída
ficavam sem cobertura de verdade. O dono pediu integrações emuladas funcionando **em dev também**,
não só nos testes.

## Decisão

Graduar um **vertical slice representativo** — o outbound de **NFS-e municipal** — para um
adaptador HTTP real, e documentar o padrão + o seam para os demais:

1. **`HttpMunicipalNfseService implements NfseGateway`**: `RestClient` com connect/read timeout,
   **retry** limitado dos transientes (TIMEOUT/UNAVAILABLE; REJECTED é terminal), guardado por um
   **`OutboundCircuitBreaker`**. JSON (de)serializado pelo `ObjectMapper` compartilhado via
   `.exchange(...)` (controle total de status/corpo, independente do conversor default do
   RestClient). Classifica falha (BR7): timeout→TIMEOUT, 5xx→UNAVAILABLE, 422→REJECTED; nunca um
   "emitido" falso. O DTO externo (`MunicipalNfseHttpMessages`) fica em `infra` (ArchUnit).
2. **Seleção por config** (`billing.nfse.adapter`): default **`simulated`** (o mock atual segue o
   default — nada quebra); `http` liga o adaptador real. Ambos `@ConditionalOnProperty`.
3. **`OutboundCircuitBreaker`** (`infra.integration`): breaker reutilizável (generaliza o padrão
   in-process do crawler, DL-0031) com clock controlado — sem resilience4j (Regra Zero).
4. **Emulador em DEV e em testes** (pedido do dono):
   - **Testes:** um `HttpServer` do JDK (sem dependência nova) stub-a o município e exercita
     sucesso/rejeição/timeout/breaker (`MunicipalNfseHttpAdapterTest`).
   - **Dev:** serviço **WireMock** no `docker-compose` sob profile `emulators` (`infra/wiremock/`),
     opt-in (`docker compose --profile emulators up`); fault injection por `municipalityCode`
     (`REJECT`/`TIMEOUT`/sucesso). O app aponta com `BILLING_NFSE_ADAPTER=http` +
     `BILLING_NFSE_BASE_URL`.

## Justificativa

- Timeout/retry/breaker **só têm sentido sobre I/O real**; um mock in-process não os testa. O
  adaptador HTTP + emulador fecham isso de verdade.
- Escolher **1 integração representativa** (outbound, com porta/ACL já existente) entrega o padrão
  completo sem inflar a fatia; os outros 4 mocks graduam pelo mesmo molde (seam documentado).
- WireMock é o emulador HTTP padrão de mercado; sob profile mantém o `up` default leve.
- Manter `simulated` como default preserva os testes existentes de Billing e o fluxo de demo.

## Alternativas descartadas

- **Graduar as 5 integrações agora:** custo desproporcional; o padrão é o mesmo — documentado.
- **Testcontainers do WireMock nos testes:** o `HttpServer` do JDK já cobre o teste sem
  dependência/rede; WireMock fica para o dev (compose).
- **resilience4j:** dependência para o que um breaker de ~90 linhas resolve (Regra Zero).
- **Trocar o default para `http`:** quebraria dev/test sem o emulador no ar; opt-in é o certo.

## Impacto

- **Arquivos:** `OutboundCircuitBreaker`, `HttpMunicipalNfseService`, `MunicipalNfseHttpMessages`
  (infra); `SimulatedMunicipalNfseService` vira `@ConditionalOnProperty` default; config
  `billing.nfse.*`; `docker-compose.yml` (serviço `nfse-emulator` no profile `emulators`) +
  `infra/wiremock/` (mappings + README). Teste `MunicipalNfseHttpAdapterTest`.
- **Contratos:** nenhum `/api` muda; a seleção do adaptador é interna.

## Como reverter

Barata: `billing.nfse.adapter=simulated` (default) ignora tudo; remover o adaptador HTTP + o
serviço do compose não afeta o domínio. O breaker reutilizável fica útil para as próximas
graduações.
