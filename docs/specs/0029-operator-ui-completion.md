# 0029 - Telas de operação (quitação do gap de UI — Fase 16)

Status: Approved (16a, 16b e 16c implementadas)
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
| **16b** | **0.25.0** | **AfterSales** (chamados/SLA/máquina de estados/resolução), **Sourcing** (proveniência de ofertas + nível de integração), **Exchange-completo** (mesa de câmbio: market-rate, posição por reserva, exposição viva + PromoFx), **Cancelamento** (política por escopo: janelas/merchant trap/quem arca) | Operations (`ROLE_OPERATIONS`) |
| **16c** | **0.26.0** | **Intelligence/DSS** (painel de insights: evidência/recomendação/guardrail + registro da decisão humana), **CommercialPolicy** (parâmetros governados, regras/diretivas, precedência Diretiva>Promoção>Contrato>Política>Padrão), **Marketing** (consentimento LGPD, segmentos, campanhas, atribuição, apagamento), **Portfolio** (marcas, contratos de representação, metas × realizado) | Intelligence/Marketing/Portfolio → `ROLE_OPERATIONS`; CommercialPolicy → `ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN` |
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
- **16b:** os endpoints de AfterSales (`/api/aftersales/**`), Sourcing (`/api/sourcing/**`), a mesa de
  câmbio (`/api/exchange/{exposure,positions,market-rates,reports}`) e a política de cancelamento
  (`/api/products/*/cancellation-policy`) exigem hoje apenas **autenticação** (SecurityConfig: sem
  matcher de papel específico — caem em `authenticated()`). A nav destas telas é marcada
  `roles: ['ROLE_OPERATIONS']` só para **reduzir ruído de menu** (são telas de operação do ciclo
  comercial); o backend segue sendo a autoridade, então acesso direto à rota continua permitido a
  qualquer autenticado e uma futura restrição de papel administrativo (cancelamento, quando
  Identity/SPEC-0024 amadurecer) aparecerá como 403 no `<app-screen-state>`, sem mudança de tela.

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

## Business Rules (16b — o ciclo comercial)

```txt
BR5  AfterSales — a tela lista chamados (GET /api/aftersales/cases) com filtros type/status/bookingId/
     breached; abre um chamado (POST /cases) referenciando uma reserva; conduz a máquina de estados
     (POST /{id}/assign|progress|wait|close) e resolve (POST /{id}/resolve). O flag `breached` é um
     alerta de SLA ortogonal que NUNCA bloqueia o fluxo (SPEC-0018 BR4/DL-0053). Uma resolução
     REFUND_APPROVED dispara um Payout REFUND e CANCEL_APPROVED dispara o cancelamento na Booking
     (BR2/BR3) — a UI só aciona; AfterSales não calcula penalidade nem posta financeiro. Uma transição
     inválida volta pelo código estável (aftersales.case.transition.invalid), sem inventar rótulo.
BR6  Sourcing — a tela registra a proveniência de uma oferta (POST /api/sourcing/offers: productText,
     basePrice, origin, integrationLevel, externalRef) e lê uma oferta por id (GET /offers/{id}),
     mostrando a origem do mundo híbrido (PORTAL_API/EXTERNAL_SITE/THIRD_PARTY_CATALOG/RAW_DEMAND) e o
     nível de integração (NONE/INBOUND/BIDIRECTIONAL — SPEC-0009 BR1). Não há endpoint de listagem, então
     a tela lê uma oferta por id (não inventa um GET de lista).
BR7  Exchange (mesa de câmbio) — a tela COMPLEMENTA a de taxa congelada (SPEC-0003): mostra a exposição
     viva do livro (GET /api/exchange/exposure: subsídio acumulado + drift mark-to-market + alerta de
     drift, BR6/BR9), registra/lê a taxa de mercado e seu histórico (POST/GET /market-rates — via manual
     de contingência, DL-0025), lê a posição de uma reserva com sua decomposição subsídio × drift (GET
     /positions/{bookingId}) e o relatório PromoFx de um período (GET /reports/promo-fx?period=YYYY-MM).
     São read-models/projeções; a tela de taxa congelada e seus testes seguem intactos.
BR8  Cancelamento — a tela lê a política de um escopo produto/fornecedor (GET /api/products/{ref}/
     cancellation-policy) e a administra (PUT), expondo o merchant trap (ALL_SALES_FINAL ⇒ não
     reembolsável ao fornecedor — SPEC-0010 BR3/BR5), quem arca a penalidade (AGENCY/ACME/SUPPLIER),
     as janelas (hoursBefore × penaltyPct — BR2) e a taxa de no-show. Uma janela malformada volta pelo
     código estável (cancellation.policy.invalid). A autorização é do backend (hoje authenticated;
     papel administrativo quando Identity/SPEC-0024 amadurecer).
```

## Acceptance Criteria (16b)

```txt
AC10 Existem telas AfterSales, Sourcing, Exchange (mesa de câmbio) e Cancelamento acessíveis pela nav
     sob o Shell, rota lazy com authGuard (e canDeactivate nos formulários editáveis). Os itens de nav
     são marcados roles: ['ROLE_OPERATIONS'] (esconder ruído de menu; o backend é a autoridade).
AC11 Cada tela usa <app-screen-state> em toda seção de dados, com os quatro estados; todos os rótulos
     vêm do i18n (pt-BR + en), sem texto hardcoded.
AC12 AfterSales: filtra chamados, abre um, conduz a máquina de estados e resolve; uma transição inválida
     mostra o erro pelo código estável; o alerta de SLA é exibido sem bloquear.
AC13 Sourcing: registra uma oferta e consulta uma por id mostrando origem e nível de integração.
AC14 Exchange (mesa): mostra exposição viva com o alerta de drift, registra/lista taxas de mercado, lê
     a posição de uma reserva e o relatório PromoFx de um período; a tela de taxa congelada segue verde.
AC15 Cancelamento: consulta e administra a política de um escopo, gerencia janelas de penalidade e
     mostra o merchant trap/quem arca; um PUT inválido mostra o código estável.
AC16 Vitest por tela cobrindo loading→success, empty (onde há lista) e error/permission; specs de
     serviço (HttpTestingController) para os wrappers HTTP; gate de cobertura verde (pisos Fase-12).
AC17 Uma jornada Playwright: login OPERATIONS → AfterSales (lista/estado vazio) e Cancelamento
     (consulta de política); um usuário sem o papel administrativo/ROLE_FINANCE recebe 403 numa ação
     financeira (autoridade no backend). Autorada; execução do stack E2E depende de rede de artefatos.
AC18 Nenhuma mudança de backend além do bump de versão (pom + OpenAPI) para 0.25.0; ./mvnw verify
     permanece verde; nenhuma migração, nenhum contrato alterado.
```

## Business Rules (16c — inteligência & crescimento)

```txt
BR9   Intelligence/DSS — a tela lista insights (GET /api/intelligence/insights) com filtros type/
      subjectRef/status, ordenados por ganho estimado; abre um insight mostrando a evidência (subsídio
      acumulado, gap realizado, volume atraído e proveniência), a recomendação (veredito CONVERTE/
      QUEIMA_MARGEM, ação, ganho/risco) e o guardrail cruzado (alerta, nunca bloqueia — BR3); e registra
      a decisão humana (POST /{id}/decision: ACCEPTED/REJECTED/DISMISSED + nota). Registrar a decisão só
      registra — NUNCA aciona ação (BR2/8.3). Uma decisão fora do enum volta pelo código estável
      (intelligence.decision.invalid), sem inventar rótulo.
BR10  CommercialPolicy — a tela resolve um parâmetro governado por escopo (GET /api/commercial-policy/
      resolve: key + accountId/productRef/channel) mostrando o valor vencedor e a proveniência (camada
      que venceu, quem/quando); lista as regras para auditoria (GET /rules, filtros key/layer) exibindo a
      precedência fixa Diretiva > Promoção > Contrato > Política > Padrão (BR2); define uma regra POLICY/
      PROMOTION/CONTRACT (POST /rules — curador/diretor: ROLE_POLICY_ADMIN/ROLE_DIRECTOR) e emite uma
      diretiva (POST /directives — topo da precedência; justificativa obrigatória; ROLE_DIRECTOR — BR5).
      O backend é a autoridade: um chamador sem o papel recebe 403, renderizado como estado de permissão.
BR11  Marketing — a tela lê o estado de consentimento de um titular e o histórico append-only (GET
      /api/marketing/consents), concede (POST /consents) e revoga (DELETE /consents/{id}); define um
      segmento e prevê o alcance (POST /segments, GET /segments/{id}/preview); cria uma campanha e a
      dispara (POST /campaigns, POST /campaigns/{id}/send — o disparo filtra por consentimento, BR2, e
      reporta alvos/suprimidos/enfileirados); registra/lista atribuição campanha→reserva (POST/GET
      /attribution); e executa o apagamento LGPD (POST /erasure — remove PII, mantém a tombstone de
      revogação, BR6). Um envio nunca ocorre sem consentimento GRANTED (BR2).
BR12  Portfolio — a tela lista/registra/desativa marcas representadas (GET/POST/DELETE /api/portfolio/
      brands); lista os contratos de representação de uma marca e checa a cobertura numa data (GET
      /brands/{ref}/contracts, /contract-coverage — um alerta, nunca bloqueia a venda, BR2); registra um
      contrato (POST /contracts); define uma meta VOLUME/REVENUE (POST /goals) e lê o progresso meta ×
      realizado × atingimento (GET /brands/{id}/goals/{period}/progress). Amounts na moeda original.
```

## Acceptance Criteria (16c)

```txt
AC19 Existem telas Intelligence, CommercialPolicy, Marketing e Portfolio acessíveis pela nav sob o
     Shell, rota lazy com authGuard (e canDeactivate nos formulários editáveis). Intelligence/Marketing/
     Portfolio são marcadas roles: [ROLE_OPERATIONS]; CommercialPolicy roles: [ROLE_DIRECTOR,
     ROLE_POLICY_ADMIN] (esconder ruído de menu; o backend é a autoridade).
AC20 Cada tela usa <app-screen-state> em toda seção de dados, com os quatro estados; todos os rótulos
     vêm do i18n (pt-BR + en), sem texto hardcoded.
AC21 Intelligence: filtra insights, abre um mostrando evidência/recomendação/guardrail e registra a
     decisão humana; uma decisão inválida mostra o código estável; registrar não aciona nenhuma ação.
AC22 CommercialPolicy: resolve um parâmetro com proveniência, lista regras com a precedência, define
     uma regra e emite uma diretiva; a falta de papel vira 403 (estado de permissão), sem rótulo inventado.
AC23 Marketing: consulta consentimento + histórico, concede/revoga, define segmento + preview, cria e
     dispara campanha (com suprimidos por consentimento), registra/lista atribuição e executa o apagamento.
AC24 Portfolio: lista/registra/desativa marcas, lista contratos + cobertura, registra contrato, define
     meta e lê o progresso meta × realizado × atingimento.
AC25 Vitest por tela cobrindo loading→success, empty (onde há lista) e error/permission; specs de serviço
     (HttpTestingController) para os wrappers HTTP; gate de cobertura verde (pisos Fase-12).
AC26 Uma jornada Playwright: login DIRECTOR → Intelligence (lista/estado vazio) e CommercialPolicy
     (resolução + precedência); a autoridade é provada na API — um token sem ROLE_DIRECTOR recebe 403 no
     endpoint de diretiva, o diretor é autorizado. Autorada; execução do stack E2E depende de rede de artefatos.
AC27 Nenhuma mudança de backend além do bump de versão (pom + OpenAPI) para 0.26.0; ./mvnw verify
     permanece verde; nenhuma migração, nenhum contrato alterado.
```

## Non-Goals (recap)

- Telas para endpoints M2M (webhooks/ACL) — nunca.
- Reabrir a SPEC-0026 — esta spec é aditiva.
- Contabilidade plena, geração fiscal avançada, orquestração de pagamento — o backend já é o dono; a
  UI só aciona o que a API expõe.

## Open Questions

- Nenhuma para 16a/16b/16c (as APIs, os papéis e o padrão de tela já existem — DL-0109). A fatia 16d
  detalhará suas telas (People/RH, Ponto, Assets, Admin, Platform/TI, Identity) quando for implementada,
  podendo refinar esta spec.
