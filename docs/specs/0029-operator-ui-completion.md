# 0029 - Telas de operação (quitação do gap de UI — Fase 16)

Status: Approved (16a implementada)
Related DL: DL-0109 (gap de UI), DL-0094 (dashboard compõe endpoints), DL-0082 (papéis sensíveis)
Related ADR: 0015 (versionamento), SPEC-0026 (padrão de tela — shell/estados/nav/i18n)

> Convenções herdadas da **SPEC-0026** (a repaginação da Fase 10). Esta spec **não** reabre a 0026:
> ela **estende a cobertura** — cria uma tela de operação para cada módulo que hoje só existe como
> API (DL-0109). É **frontend-only** sobre APIs que já existem; nenhum contrato/schema é tocado.

## Goal

Pagar a dívida de UI diagnosticada em **DL-0109**: o backend tem 22 módulos / 37 controllers REST,
mas o frontend só tinha tela para ~5 (Accounts, Exchange parcial, Quoting, Booking, Reconciliation)
+ dashboard/login/health. O operador enxergava ~1/4 do ERP. A Fase 16 constrói as telas faltantes
**reusando o padrão estabelecido** (service + `API_BASE_URL` + `PageResponse`; `<app-screen-state>`
para carregando/vazio/erro/permissão; rota lazy sob o `Shell` com guards; `NavItem.roles?` para nav
por papel; i18n bilíngue pt-BR/en; Vitest + Playwright).

## Scope

**Em escopo:** as telas de operação (leitura e as ações já expostas pela API) dos módulos sem UI.
Cada tela cobre os quatro estados reais (carregando/vazio/erro/permissão) e os rótulos vêm do i18n.
A entrega é fatiada por domínio/papel, cada fatia um release MINOR (ADR 0015):

| Fatia | Release | Telas | Papel de nav sugerido |
|---|---|---|---|
| **16a** | **0.24.0** | **Finance** (razão AP/AR, balancete por moeda, abrir/fechar período), **Billing** (NF de comissão / ISS), **Payout** (repasses/liquidações/reembolsos), **Compliance** (cofre de documentos, requisitos, retenção) | Finance/Billing/Payout → `ROLE_FINANCE`; Compliance → visível a qualquer autenticado (leitura ampla) |
| 16b | 0.25.0 | AfterSales, Sourcing, Exchange-completo (market-rate/posições/exposição), Cancelamento | Operations |
| 16c | 0.26.0 | Intelligence/DSS, CommercialPolicy, Marketing, Portfolio | Director/Policy |
| 16d | 0.27.0 | People/RH, Ponto, Assets, Admin, Platform/TI, Identity/acesso | IT/Finance/Director |

**Fora de escopo (não-metas):**
- **Endpoints máquina-a-máquina não ganham tela**: webhooks de pagamento/NFS-e (`/api/webhooks/**`),
  a ACL de entrada (`/api/integration/**`) e o disparo de execução assíncrona por webhook — são
  contratos entre sistemas, não jornadas de operador. A execução de Payout/emissão de NF é acionada
  na tela pela ação síncrona já exposta (`POST .../execute`, `POST .../issue`).
- **Nenhuma mudança de backend/contrato/schema.** Se uma tela precisar de dado que nenhum endpoint
  fornece, compõe-se endpoints existentes no navegador (como o dashboard — DL-0094); só se for
  genuinamente impossível é que se adiciona um endpoint de leitura (mantendo `./mvnw verify` verde,
  OpenAPI + i18n, sem FK cross-context nem migração). Em **16a nenhum endpoint novo foi necessário**.
- **Upload/geração de documento pesado**: 16a lê o cofre e envia documento (multipart já exposto);
  fluxos avançados (assinatura, versionamento) ficam com o backend, sem tela nova.

## Visibilidade por papel (nav × autoridade)

O **backend é a única autoridade** de autorização (security.md). A nav só **esconde ruído de menu**:

- As leituras sob `/api/finance`, `/api/billing`, `/api/payouts`, `/api/compliance` exigem apenas
  **autenticação** (SecurityConfig: só `authenticated()` para GET). As **ações sensíveis** exigem
  papel: `POST /api/finance/periods/*/close` e `POST /api/billing/invoices/*/issue` → **ROLE_FINANCE**.
- Portanto a nav de **Finance/Billing/Payout** é marcada `roles: ['ROLE_FINANCE']` (são telas de
  operação financeira; esconder do resto reduz ruído). **Compliance** fica **sem `roles`** (visível a
  qualquer autenticado): a leitura do cofre e do close-check é ampla, útil a operações/diretoria.
- Se um usuário sem ROLE_FINANCE acessar a rota diretamente e o backend recusar uma ação com 403, o
  `<app-screen-state>` já renderiza o **estado de permissão** (`access.denied`/`error.forbidden`),
  não um erro genérico.

## Estados de tela (obrigatórios em toda seção de dados)

Reuso de `<app-screen-state [state] [errorCode] [emptyKey] (retry)>` (SPEC-0026 BR8):
- **loading** enquanto a chamada não resolve;
- **empty** quando a lista/consulta volta vazia (DB efêmero do E2E sobe vazio — DL-0101);
- **error** com o código estável do backend e botão *tentar novamente*;
- **permission** quando o código é `access.denied`/`error.forbidden` (403).

## Business Rules (16a — as telas desta fatia)

```txt
BR1  Finance — a tela lista lançamentos AP/AR (GET /api/finance/entries) com filtros direção/status/
     período/parte; mostra o balancete por moeda de um período (GET /periods/{yyyymm}/trial-balance)
     e o estado do período (GET /periods/{yyyymm}); abre um lançamento (POST /entries) e fecha o
     período (POST /periods/{yyyymm}/close). Amounts SEMPRE na moeda original (DL-0013): nunca somar
     moedas diferentes; o balancete é por moeda. O fechamento pode falhar com finance.period.
     cannot-close (pendências do Compliance) — o erro é mostrado pelo código, sem inventar rótulo.
BR2  Billing — a tela lê uma NF de comissão por id (GET /api/billing/invoices/{id}) e permite criar
     um rascunho (POST /invoices), emitir (POST /invoices/{id}/issue — ISS/retenções/nº/cód. de
     verificação/documento) e cancelar (POST /invoices/{id}/cancel, motivo). A base é a comissão (a
     base tributável), nunca o pacote bruto (SPEC-0016 BR1). Emitir exige ROLE_FINANCE.
BR3  Payout — a tela lista repasses/liquidações/reembolsos (GET /api/payouts) com filtros kind/status/
     payee; abre um por id com suas parcelas (GET /{id}); cria (POST /) repasse de comissão,
     liquidação (com taxa quando em moeda estrangeira) ou reembolso (com originRef); executa (POST
     /{id}/execute) com hint de resultado opcional (dev/test). Uma falha do provedor vira FAILED
     explícito, nunca falso EXECUTED (SPEC-0017 BR2).
BR4  Compliance — a tela lista o resultado do close-check de um período (GET /api/compliance/
     close-check?period=YYYY-MM): se pode fechar e, senão, as pendências (entrada + o que falta);
     lê um documento do cofre por id (GET /documents/{id}) mostrando tipo/hash/emissão/retenção/
     dado pessoal; e envia um documento novo (POST /documents multipart) com tipo/emissão/formato
     assinado/dado pessoal. O fileRef nunca é exposto (é handle interno). Retenção legal é calculada
     no ingest (5 anos fiscal/folha/ponto; 10 anos contratos).
```

## Acceptance Criteria (16a)

```txt
AC1  Existe uma tela Finance, Billing, Payout e Compliance acessível pela nav sob o Shell, rota lazy
     com authGuard; Finance/Billing/Payout aparecem só para ROLE_FINANCE; Compliance para qualquer
     autenticado.
AC2  Cada tela usa <app-screen-state> em toda seção de dados, com os quatro estados; todos os rótulos
     vêm do i18n (pt-BR + en), sem texto hardcoded.
AC3  Finance: filtra lançamentos, consulta um período e seu balancete por moeda, cria lançamento e
     dispara o fechamento; um fechamento vetado mostra o erro pelo código estável.
AC4  Billing: consulta uma NF por id e mostra base/ISS/retenções/status/número; cria rascunho, emite
     e cancela pela tela.
AC5  Payout: filtra a lista, abre um payout com parcelas, cria e executa; o resultado do provedor
     (SUCCEEDED/FAILED) reflete no status sem falso positivo.
AC6  Compliance: consulta o close-check de um período (pode fechar / pendências), lê um documento por
     id e envia um documento; o hash/retenção aparecem, o fileRef não.
AC7  Vitest por tela cobrindo loading→success, empty e error/permission; gate de cobertura verde.
AC8  Uma jornada Playwright: login FINANCE → Finance → ledger/estado vazio; e um usuário não-FINANCE
     recebe 403 na ação financeira (autoridade no backend).
AC9  Nenhuma mudança de backend além do bump de versão (pom + OpenAPI) para 0.24.0; ./mvnw verify
     permanece verde; nenhuma migração, nenhum contrato alterado.
```

## Non-Goals (recap)

- Telas para endpoints M2M (webhooks/ACL) — nunca.
- Reabrir a SPEC-0026 — esta spec é aditiva.
- Contabilidade plena, geração fiscal avançada, orquestração de pagamento — o backend já é o dono; a
  UI só aciona o que a API expõe.

## Open Questions

- Nenhuma para 16a (as APIs, os papéis e o padrão de tela já existem — DL-0109). As fatias 16b–16d
  detalharão suas telas quando forem implementadas, cada uma podendo refinar esta spec.
