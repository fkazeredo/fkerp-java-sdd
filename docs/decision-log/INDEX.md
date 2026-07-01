# Decision Log — Índice

Registro append-only das decisões tomadas em modo autônomo (uma por arquivo
`DL-NNNN-*.md`). Cada decisão foi registrada **antes** do código que depende dela,
conforme `docs/RUN-PHASE.md`.

## ⚠️ Atenção (Reversibilidade=Cara ou Confiança=Baixa)

| DL | Título | Confiança | Reversibilidade | Por que destacada |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | Manter pacote base `com.fksoft` | Alta | **Cara** | Renomear o pacote raiz após código nascer gera diff amplo |
| [DL-0009](DL-0009-quoting-formula-de-preco.md) | Quoting: preço = base BRL + markup (default 0) | Média | **Cara** | Fórmula de preço move a tese econômica; refator amplo se mudar |
| [DL-0017](DL-0017-inbound-account-not-found-rejects.md) | Inbound: Account inexistente **rejeita** (422) | **Baixa** | Moderada | Decisão de negócio em aberto na SPEC-0009; só o dono fecha |
| [DL-0024](DL-0024-charges-are-distinct-facts-never-netted.md) | Encargos são fatos distintos que **nunca** se compensam (armadilha do merchant) | Alta | **Cara** | Tese econômica da Fase 4; sair do "sem netting" exige nova spec |
| [DL-0029](DL-0029-rep-type-target-rep-p-export-upload.md) | Tipo de REP (Q6): mirar **REP-P** e modelar AFD como upload da exportação oficial | **Baixa** | **Cara** | **Q6 — qual REP o cliente usa é incógnita de negócio**; muda formato/captura do artefato legal |
| [DL-0033](DL-0033-crawl-period-and-multi-source.md) | Periodicidade do crawl (diário, período corrente) e lista de `sourceRef` configurável | **Baixa** | Barata | Periodicidade/filiais reais são decisão de RH ainda não dada (só configuração) |
| [DL-0044](DL-0044-billing-tax-regime-simples-and-swappable-strategy.md) | Billing: regime **Simples Nacional** (default) + estratégia trocável de ISS/retenções (Q7) | **Baixa** | **Cara** | **Q7 — regime tributário/quem emite é incógnita de negócio (só o contador fecha)**; move a tese tributária e o impacto fiscal de notas já emitidas é externo e caro |
| [DL-0048](DL-0048-payout-payment-gateway-acl-async-webhook.md) | Payout: gateway de pagamento como porta + **mock rastreável com webhook assíncrono** (ADR 0006) | **Baixa** | Moderada | Meio de pagamento real é Open Question da SPEC-0017; só o dono fecha (o mock prova o contrato) |
| [DL-0049](DL-0049-payout-foreign-settlement-rate-and-brl-baixa.md) | Payout: liquidação do fornecedor com `settlementRate` (USD) + baixa em **BRL** | **Baixa** | **Cara** | Fluxo de câmbio real (remessa vs BRL) é Open Question; a tese de câmbio é compartilhada por Payout/Reconciliation/Exchange |
| [DL-0058](DL-0058-marketing-lgpd-erasure-preserves-revocation-and-metrics-as-logs.md) | Marketing: exclusão LGPD apaga PII mas preserva tombstone de revogação (anonimizado) | **Baixa** | **Cara** | **Alcance do apagamento × dever de prova/supressão só o DPO/jurídico fecha**; expurgo é destrutivo (PII não volta) |
| [DL-0062](DL-0062-portfolio-brand-sale-attribution-intake-and-realized-projection.md) | Portfolio: realizado por marca via **intake próprio** (reserva→marca) + projeção de eventos, sem alterar o evento do Booking | **Baixa** | Moderada | **Qual campo identifica a marca na venda é incógnita de negócio** (só o dono fecha); intake explícito + seam rastreável |
| [DL-0074](DL-0074-platform-certificate-encryption-at-rest.md) | Custódia do e-CNPJ: criptografia at-rest **AES-256-GCM** (envelope), chave fora do banco; só metadados expostos | **Baixa** | **Cara** | **Onde custodiar (KMS×HSM×secret manager) e A1×A3 é decisão de infra/segurança do dono**; troca de cofre exige re-cifrar/migrar segredo real |
| [DL-0079](DL-0079-identity-inhouse-jwt-resource-server-idp-boundary.md) | Identity: auth real **in-house** (Spring Security + JWT HS256) no 8k; **OIDC externo vivo fica para a Fase 13** — **RESOLVIDO na Fase 13 (ver DL-0103/0104/0105)** | **Baixa** | **Cara** | **Comprar/qual IdP é decisão do dono (Open Question)**; trocar emissor in-house por IdP externo vivo (Fase 13) é refator amplo (JWKS/rotação/login/gestão de usuários), ainda que a porta `UserContextProvider` e o modelo de papéis sejam preservados |
| [DL-0103](DL-0103-identity-dev-idp-keycloak-realm-clients-roles.md) | Identity (Fase 13): **IdP de dev = Keycloak** — **SUBSTITUÍDA na Fase 17 (ver DL-0110 / ADR-0018): Keycloak removido, AS self-hosted embutido** | **Baixa** | **Cara** | **Qual IdP em produção é decisão do dono**; a Fase 17 trocou o Keycloak pelo Spring Authorization Server embutido — a troca é por config (o contrato OIDC é padrão) |
| [DL-0112](DL-0112-reintroduce-local-user-store-v32.md) | Identity (Fase 17): store local de usuários **reintroduzido** (V32, BCrypt) para o AS self-hosted autenticar | Alta | Moderada | O ERP volta a custodiar hash de senha (consequência de remover o IdP externo); mitigado por hash-only + seed forte só em dev/E2E |

> _Nota Fase 8e:_ DL-0052/0053/0054 são **Confiança=Média / Reversibilidade=Barata–Moderada** —
> não entram neste destaque. O "quais custos contam" do custo de servir (DL-0053) e os prazos de
> SLA (DL-0052) seguem confirmáveis com o dono, mas a reversão é barata (parâmetro governado em
> runtime / value object local).

> DL-0017 (Fase 3), **DL-0029/DL-0033** (Fase 6), **DL-0044** (Fase 8c), **DL-0048/DL-0049** (Fase 8d)
> e **DL-0058** (Fase 8f) são as de **Confiança=Baixa** (Open Questions de negócio em aberto).
> **DL-0029 (Q6), DL-0044 (Q7), DL-0049 e DL-0058** são as mais sensíveis: Confiança=Baixa **e**
> Reversibilidade=Cara — incógnitas de negócio que só o cliente/contador/DPO fecha (tipo de REP;
> regime tributário; fluxo de câmbio da liquidação; alcance do apagamento LGPD).
> DL-0009/DL-0017, DL-0018, **DL-0024**, **DL-0029**, **DL-0044**, **DL-0049** e **DL-0058** são as de
> reversão não-barata.

> _Nota Fase 8f (Marketing — SPEC-0019):_ DL-0055/0056/0057/0059 são **Confiança=Média–Alta /
> Reversibilidade=Moderada** (porta de newsletter trocável; consent log; intake de atribuição;
> critério jsonb validado) — reversões localizadas. **DL-0058** é a única do destaque (Baixa/Cara):
> o apagamento LGPD é destrutivo e seu alcance exato é decisão de DPO/jurídico.

> _Nota Fase 8g (Portfolio — SPEC-0020):_ DL-0060 (dois contextos, Alta/Moderada), DL-0061 (vender
> sem contrato vigente apenas alerta, Média/Barata) e DL-0063 (alerta de expiração por relógio
> controlado, Média/Barata) são reversões baratas/localizadas. **DL-0062** é a do destaque
> (Confiança=Baixa): **qual campo identifica a marca na venda** é incógnita de negócio — só o dono
> fecha. A reversão é Moderada (trocar a fonte do casamento no listener quando a marca for nativa na
> venda), não Cara: o intake próprio + seam rastreável (espelha DL-0057) protege o contrato de metas.

> _Nota Fase 8h (Assets — SPEC-0021):_ DL-0064 (dois contextos, Q2 fechada — Alta/Moderada), DL-0065
> (sem depreciação/gestão plena, registro + seam comprar-vs-construir — Alta/Moderada), DL-0066
> (alerta de licença a vencer por relógio controlado, 30d, idempotente — Média/Barata), DL-0067
> (Assets é folha: publica eventos, não fia consumidores Finance/Intelligence — Média/Barata) e
> DL-0068 (baixa auditada inline, RETIRED terminal — Média/Barata). **Nenhuma** entra no destaque
> (Baixa/Cara): a Q2 foi resolvida pela recomendação do arquiteto (dois contextos) e o resto segue
> padrões já validados do projeto (relógio controlado, módulo-folha). As reversões são
> baratas/moderadas e localizadas no módulo `assets`.

> _Nota Fase 8i (People — jornada/banco de horas — SPEC-0022):_ DL-0069 (jornada/banco como serviço
> de domínio puro sobre o snapshot operacional, sem reescrever o crawler — Média/Moderada), DL-0071
> (divergência sinaliza, nunca corrige — Média/Barata) e DL-0072 (holerite no Compliance/PAYROLL por
> valor — Alta/Barata) são reversões baratas/localizadas no módulo `people`. **DL-0070** é a do
> destaque (**Confiança=Baixa**): a **política de banco de horas** (janela de compensação, limites,
> acordo coletivo) é decisão **trabalhista/negocial** que só o RH/jurídico da empresa fecha — o v1
> adota o saldo mensal + janela CLT configurável (default 6 meses do acordo individual escrito,
> art. 59). A reversão é **Moderada** (entra um sistema de folha por cima), não Cara: o
> `JourneyCalculator` e as tabelas já são a base.

> _Nota Fase 8k (Identity — SPEC-0024):_ DL-0080 (novo módulo `identity` 21º + usuário local mínimo,
> Alta/Moderada), DL-0081 (gradua o stub mantendo a porta `UserContextProvider`; stub permissivo atrás
> de profile `dev`/`test`; produção usa o JWT — Alta/Barata), DL-0082 (modelo papel→permissão + mapa das
> ações sensíveis — Média/Moderada) e DL-0083 (auditoria de acesso reusa o `system_audit` do Platform —
> Alta/Moderada) são reversões baratas/moderadas e localizadas. **DL-0079** é a do destaque
> (**Confiança=Baixa / Reversibilidade=Cara**): **comprar/qual IdP é Open Question do dono**. O 8k entrega
> o **modelo de auth real + papéis/permissões + auditoria** com emissor **JWT in-house** (Spring Security,
> Resource Server do próprio emissor), e a **Fase 13** consolida o **OIDC externo vivo** (JWKS/rotação,
> escopos finos). A porta `UserContextProvider` e o modelo de papéis sobrevivem à troca; o emissor/
> verificador/login não — por isso a reversão é Cara.

> _Nota Fase 8j (Platform — SPEC-0023):_ DL-0073 (novo módulo `platform`, Alta/Moderada), DL-0075
> (registro de jobs + advisory lock, Alta/Moderada), DL-0076 (catálogo/ligação de schedulers,
> Média/Moderada), DL-0077 (auditoria append-only por listener, Alta/Moderada) e DL-0078
> (`CertificateSigner` graduado, Média/Moderada) são reversões moderadas/localizadas. **DL-0074** é a do
> destaque (**Confiança=Baixa / Reversibilidade=Cara**): **onde custodiar o e-CNPJ** (KMS de nuvem × HSM
> × secret manager on-prem) e **A1×A3** é Open Question de infra/segurança do dono. O v1 adota o degrau
> mais defensável — **envelope AES-256-GCM com chave mestra por ambiente, material cifrado no banco e só
> metadados expostos**, atrás de uma porta `SecretCipher`/`CertificateSigner` trocável. Trocar para
> KMS/HSM real muda só o adaptador, mas exige **re-cifrar/migrar segredo real** — por isso a reversão é
> Cara, ainda que o domínio fique intacto.

> _Nota Fase 8l (Admin — SPEC-0025):_ DL-0084 (módulo próprio + registro enxuto, procurement = comprar,
> Alta/Moderada), DL-0085 (mapa `kind`→`EntryType`→documento, aditivo, Média/Barata), DL-0086 (integração
> Finance/Compliance por fachada/porta, idempotente, acíclico, Alta/Moderada), DL-0087 (alerta de contrato
> a vencer por relógio controlado, Média/Barata) e DL-0088 (escritas exigem ROLE_FINANCE + auditoria,
> Média/Barata). **Nenhuma** entra no destaque (Baixa/Cara): a Open Question de procurement foi fechada
> pela própria spec (comprar se exigido — fronteira), e o resto segue padrões já validados do projeto
> (genérico enxuto como Assets/Finance; relógio controlado; gate HTTP + `system_audit`). As reversões são
> baratas/moderadas e localizadas no módulo `admin` (o mapa `kind`→`EntryType` é função pura + seed; o
> papel é um matcher). O 8x encerra aqui.

> _Nota Fase 9 (Limpeza estrutural — ADR 0016):_ DL-0089 (encapsulação pós-achatamento via marcador
> `@ModuleInternal` + regra ArchUnit, mantendo o Spring Modulith para ciclos/grafo) é **Alta/Moderada**
> — **não** entra no destaque. É refactor estrutural sem mudança de contrato; a reversão é mecânica
> (regenerar `internal/`, mover de volta, restaurar predicados) e protegida pelos testes + gates.

> _Nota Fase 10 (UX & Frontend profissional — SPEC-0026):_ DL-0090 (stack PrimeNG 21 Aura + Tailwind v4,
> Alta/Moderada — **gradua DL-0003**), DL-0091 (tema claro/escuro, Alta/Barata), DL-0093 (paleta de
> comandos + atalhos próprios, Alta/Barata) e DL-0094 (KPIs do dashboard calculados no cliente, sem
> backend novo, Alta/Barata) são reversões baratas/moderadas e **frontend-only** — não tocam contrato,
> schema nem os 468 testes do backend. **DL-0092** (silent refresh) é a única de **Confiança=Média**:
> o backend do 8k **não tem refresh token**, então "silent refresh" nesta fase é **revalidação** da
> sessão via `GET /me` (existente), não emissão de novo token — a interpretação mais defensável sem
> inventar contrato (Regra 3); a **Fase 13 (OIDC)** graduará para refresh real. **Nenhuma** DL da
> Fase 10 é Confiança=Baixa nem Reversibilidade=Cara: o stack-alvo já estava no ADR 0008 e o ROADMAP
> recomenda explicitamente o conjunto adotado.

> _Nota Fase 11 (Observabilidade & monitoramento — SPEC-0027):_ DL-0095 (exposição do Actuator:
> `health`/`info`/`version` públicos, `prometheus`/`metrics` atrás de **ROLE_IT**; `env`/`beans`/
> `heapdump` não expostos — Média/Barata), DL-0096 (logs JSON pelo **logging estruturado nativo** do
> Spring Boot + higiene de não-logar segredo/PII, sem encoder custom — Alta/Barata), DL-0097
> (`GET /api/version` por **build-info + git** com degradação graciosa — Alta/Barata) e DL-0098
> (métricas de **negócio sobre eventos JÁ publicados**, por listener em **infra**; o `domain` não
> conhece Micrometer — ADR 0012, regra ArchUnit nova — Alta/Barata). **Nenhuma** entra no destaque
> (Baixa/Cara): a tarefa traz a stack pronta do fkerp-poc e as Recomendações/fontes oficiais
> (Spring Boot Actuator/Micrometer/Prometheus/Grafana) fecham as escolhas; instrumenta-se **só o que
> existe** (Regra Zero), sem novo comportamento de negócio, schema ou contrato versionado. As
> reversões são localizadas em config/`SecurityConfig`/um listener de infra.

> _Nota Fase 12 (Qualidade & E2E — SPEC-0028):_ DL-0099 (JaCoCo backend ≥80%, gate no `verify`),
> DL-0100 (Vitest/v8 com thresholds no `angular.json`), DL-0101 (stack E2E isolado/efêmero, 4201/8081)
> e DL-0102 (Playwright + caminhos tristes + job de E2E no CI) são **todas Alta/Barata** — **nenhuma**
> entra no destaque (Baixa/Cara). É tooling de teste/CI/cobertura: a tarefa traz a abordagem pronta do
> fkerp-poc e as fontes oficiais (JaCoCo/Vitest/Playwright/Angular) fecham as escolhas; os limiares são
> **pisos de não-regressão** medidos (89% back, 70/72/54/60 front), não barras cosméticas; o isolamento
> é **por construção** (projeto/rede/porta/DB efêmero distintos — dev DB provado intacto). Nenhum
> comportamento de negócio, schema ou contrato muda (Regra Zero); as reversões são uma linha de config
> ou a remoção de um arquivo isolado.

> _Nota Fase 13 (Identity/AuthZ profissional — gradua SPEC-0024):_ a fase **resolve as duas dívidas
> diferidas** do 8k/Fase 10: **DL-0079** (IdP externo vivo) e **DL-0092** (silent-refresh real) — ambas
> marcadas **RESOLVIDO** nos seus arquivos. O ERP deixou de ser Resource Server do próprio emissor HS256
> e passou a validar JWTs de um **Keycloak** vivo por **JWKS/RS256 com rotação** (DL-0104), com login
> **OIDC code+PKCE** e **silent-refresh por refresh token** no frontend (DL-0106). **DL-0103** (Keycloak
> como dev IdP) é a do destaque (**Baixa/Cara**): *qual IdP em produção* segue decisão do dono — Keycloak
> é o degrau de dev/E2E mais defensável e o realm export documenta o que um Entra/Cognito espelharia.
> **DL-0104** (Resource Server por JWKS + `realm_access.roles`→papéis, Média/Moderada), **DL-0105**
> (caminho de teste com JWKS local + remoção de `POST /login`, Média/Moderada — **breaking destacado em
> 0.23.0**), **DL-0106** (frontend OIDC + silent-refresh real, Média/Moderada) e **DL-0107** (catálogo
> papel→permissão permanece local; store de usuários aposentado, V31, Alta/Moderada) são localizadas. A
> porta `UserContextProvider` e o modelo de papéis (DL-0082) **sobrevivem** à troca — só muda a *fonte*
> do token (IdP externo).

> _Nota Fase 17 (remover Keycloak → AS self-hosted; re-gradua SPEC-0024):_ a decisão do dono foi
> **remover 100% do Keycloak** e servir OIDC pelo próprio Spring via **Spring Authorization Server
> embutido no app** (ADR-0018). **DL-0110** (AS embutido, três `SecurityFilterChain`s, claim
> `realm_access.roles` preservado — Média/Moderada) **substitui a DL-0103** e reaponta a DL-0104. Os
> demais reapontam: **DL-0111** (client SPA público PKCE registrado no AS — Média/Barata) espelha o
> client do realm; **DL-0112** (store local de usuários **reintroduzido** — V32/BCrypt — Alta/Moderada)
> desfaz o drop da DL-0107/V31 porque o AS precisa autenticar localmente (o ERP volta a custodiar hash);
> **DL-0113** (frontend: `issuer` → próprio app + silent-refresh por **iframe** — Média/Moderada)
> reaponta a DL-0106 (o SAS não emite refresh token a client público); **DL-0114** (Keycloak removido do
> compose dev/E2E, `infra/keycloak/`, `KEYCLOAK_*` — Alta/Moderada) encerra a infra da DL-0103. O claim
> de papéis foi **preservado** (`realm_access.roles`), então o Resource Server e os 444 testes **não
> mudaram** — só a *origem* do token (o próprio app). Breaking (Keycloak sai) destacado no **0.28.0**.

## Todas as decisões

| DL | Fase | Título | Conf. | Rev. |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | 0 | Manter pacote base `com.fksoft` | Alta | Cara |
| [DL-0002](DL-0002-stack-versoes-backend.md) | 0 | Versões do stack backend (Spring Boot 3.5.16, Modulith 1.4.12, Java 21) — **SUPERADA na Fase 14: Spring Boot 4.0.7 (ver ADR 0017 / DL-0108)** | Alta | Moderada |
| [DL-0003](DL-0003-stack-frontend-fase-0.md) | 0 | Stack frontend Fase 0 (Angular 22 + ngx-translate; PrimeNG/Tailwind adiados) — **GRADUADO na Fase 10 (ver DL-0090)** | Alta | Barata |
| [DL-0004](DL-0004-maven-wrapper-bootstrap.md) | 0 | Bootstrap do Maven Wrapper sem Maven no sistema | Alta | Barata |
| [DL-0005](DL-0005-adr-0014-ausente-adiar-fase-1.md) | 0 | ADR 0014 ausente: ~~adiar~~ → **criado** a pedido do dono (ver ADR 0014) | Alta | Barata |
| [DL-0006](DL-0006-modulith-detection-strategy.md) | 0 | Spring Modulith detection-strategy=explicitly-annotated | Alta | Barata |
| [DL-0007](DL-0007-accounts-cadastros-opcionais.md) | 1 | Accounts: CADASTUR/IATA opcionais no v1 (nenhum cadastro obrigatório) | Média | Barata |
| [DL-0008](DL-0008-exchange-nome-do-modulo.md) | 1 | Manter o nome `Exchange` para o módulo de câmbio (Q1) | Alta | Moderada |
| [DL-0009](DL-0009-quoting-formula-de-preco.md) | 1 | Quoting: preço = base BRL + markup (default 0); base comissionável em BRL | Média | **Cara** |
| [DL-0010](DL-0010-booking-quote-multiplicidade.md) | 1 | Booking: Quote→Booking não 1:1 no v1 (localizador é a trava) | Média | Moderada |
| [DL-0011](DL-0011-reconciliation-tolerancia-discrepancia.md) | 1 | Reconciliation: tolerância = max(R$1,00; 0,5% do spread esperado) | Média | Barata |
| [DL-0012](DL-0012-compliance-requirements-catalog.md) | 2 | Compliance: catálogo `entryType × DocumentRequirement` (seed da tabela 7.7, com fase) | Média | Barata |
| [DL-0013](DL-0013-finance-multimoeda-no-razao.md) | 2 | Finance: razão em moeda original (sem conversão); período agrega por moeda | Média | Moderada |
| [DL-0014](DL-0014-finance-comprar-vs-construir.md) | 2 | Finance: construir o seam mínimo (AP/AR+período) agora; contabilidade plena = comprar depois | Alta | Moderada |
| [DL-0015](DL-0015-compliance-filestorage-port.md) | 2 | Compliance: porta `FileStorage` + adaptador filesystem; hash SHA-256 | Alta | Barata |
| [DL-0016](DL-0016-inbound-webhook-signature-hmac.md) | 3 | Webhook de entrada: assinatura HMAC-SHA256 com segredo compartilhado (`X-Signature`) | Média | Moderada |
| [DL-0017](DL-0017-inbound-account-not-found-rejects.md) | 3 | Inbound: Account inexistente **rejeita** (422); não cria provisória nem enfileira | Baixa | Moderada |
| [DL-0018](DL-0018-integrated-quote-modeling.md) | 3 | Quote INTEGRATED reusa o agregado; colunas de composição MANUAL viram nulas | Alta | Moderada |
| [DL-0019](DL-0019-acl-resilience-scope-inbound.md) | 3 | ACL de entrada: classificação de falha + observabilidade (sem circuit breaker — não há chamada de saída) | Alta | Barata |
| [DL-0020](DL-0020-cancellation-lives-in-booking-module.md) | 4 | Cancelamento rico vive no módulo `booking` (sem módulo `cancellation`/`policy` novo) | Alta | Moderada |
| [DL-0021](DL-0021-merchant-of-record-attribute-default-affiliate.md) | 4 | Merchant of record é atributo por marca/contrato; default afiliado (costBearer=SUPPLIER) | Alta | Moderada |
| [DL-0022](DL-0022-penalty-currency-no-conversion.md) | 4 | Multa/encargos na moeda original (sem conversão cambial nesta fase) | Média | Barata |
| [DL-0023](DL-0023-no-show-waiver-proof-flag.md) | 4 | No-show: dispensa por voo cancelado via flag de prova rastreável (conformidade = Compliance) | Média | Barata |
| [DL-0024](DL-0024-charges-are-distinct-facts-never-netted.md) | 4 | Encargos são fatos distintos que **nunca** se compensam (armadilha do merchant) | Alta | **Cara** |
| [DL-0025](DL-0025-market-rate-source-port-and-manual.md) | 5 | Taxa de mercado: porta `MarketRateProvider` + registro manual de contingência (v1) | Média | Moderada |
| [DL-0026](DL-0026-freeze-scope-global-per-pair.md) | 5 | Escopo do congelamento: global por par de moeda (v1) | Média | Moderada |
| [DL-0027](DL-0027-drift-alert-threshold-2pct.md) | 5 | Limite de alerta de drift = \|drift\| > 2% da exposição estrangeira aberta do livro | Média | Barata |
| [DL-0028](DL-0028-fxposition-open-on-booking-confirmed.md) | 5 | `FxPosition` abre em `BookingConfirmed`; fecha reusando a liquidação de Reconciliation (sem duplicar o per-case) | Alta | Moderada |
| [DL-0029](DL-0029-rep-type-target-rep-p-export-upload.md) | 6 | Tipo de REP (Q6): mirar REP-P; AFD/AEJ como upload da exportação oficial (serve REP-C via USB) | **Baixa** | **Cara** |
| [DL-0030](DL-0030-people-module-owns-snapshot-history.md) | 6 | Novo módulo `people` é dono do snapshot/idempotência/histórico; crawler técnico em `infra/integration`; sem módulo `platform` vazio | Alta | Moderada |
| [DL-0031](DL-0031-circuit-breaker-queue-retry-in-process.md) | 6 | Disjuntor + fila de retry + dead-letter in-process (sem resilience4j); origem do REP atrás de porta com mock injetor de falhas | Alta | Moderada |
| [DL-0032](DL-0032-afd-signature-integrity-check.md) | 6 | Verificação de assinatura/integridade do AFD na ingestão (envelope CAdES/PKCS#7 + hash); ICP-Brasil completo fica p/ Platform (SPEC-0023) | Média | Moderada |
| [DL-0033](DL-0033-crawl-period-and-multi-source.md) | 6 | Periodicidade diária, período corrente `YYYY-MM`; lista de `sourceRef` configurável (default um) | **Baixa** | Barata |
| [DL-0034](DL-0034-promofx-subject-is-agency-event-derived.md) | 7 | PromoFxAdvisor: sujeito = agência (derivado de evento, não rota); intelligence é consumidor-folha | Média | Moderada |
| [DL-0035](DL-0035-promofx-verdict-thresholds-and-deterministic-advisor.md) | 7 | PromoFxAdvisor: advisor determinístico; limites CONVERTE × QUEIMA_MARGEM (MIN_VOLUME=5, BURN=R$1.000) + ganho estimado | Média | Barata |
| [DL-0036](DL-0036-overridenudge-gated-flag-and-llm-port-seam.md) | 7 | OverrideNudge desligado por feature flag (Q4); seam de porta LLM desenhado mas **não** wired; recomputação on-event | Média | Barata |
| [DL-0037](DL-0037-parameter-rule-model-and-precedence-engine.md) | 8a | `ParameterRule` (colunas de escopo, não jsonb) + motor de precedência: camada→especificidade→`validFrom`/`createdAt`/`id` (desempate determinístico) | Alta | Moderada |
| [DL-0038](DL-0038-runtime-self-service-authz-and-directive-audit.md) | 8a | Q8: diretor/admin editam parâmetros e diretivas em runtime (self-service auditável); fluxos não. Diretiva = papel diretor + justificativa + evento (403 sem papel) | Média | Barata |
| [DL-0039](DL-0039-governed-parameter-keys-seed-and-q5-agent-commission.md) | 8a | Seed SYSTEM_DEFAULT só das chaves já usadas (MARKUP_PCT=0, FX_DRIFT_LIMIT=2%, RECON_DISCREPANCY_TOL=R$1,00); Q5 (comissão do agente) comportada pelo motor por escopo, sem implementar Commissioning | Média | Barata |
| [DL-0040](DL-0040-markup-provider-graduation-keeps-contract.md) | 8a | Gradua `MarkupProvider`: motor real preservando contrato (`currentMarkup()` + sobrecarga com escopo); source = camada vencedora; sem regra → SYSTEM_DEFAULT (back-compat) | Alta | Moderada |
| [DL-0041](DL-0041-finance-event-driven-ap-ar-posting.md) | 8b | Finance posta AP/AR automático ao consumir `CancellationCharged`/`NoShowCharged`/`MerchantObligationIncurred` (booking), idempotente por UNIQUE `(source_ref, charge_kind)` + state-check; comissão/SupplierSettlement diferidos (sem produtor) | Média | Moderada |
| [DL-0042](DL-0042-finance-buy-vs-build-full-gl-reaffirmed.md) | 8b | Finance: reafirma comprar-vs-construir — entrega "full" é livro-caixa (AP/AR+período+evento+balancete), **não** GL pleno (plano de contas/partidas dobradas/DRE/SPED = comprar/integrar) | Alta | Moderada |
| [DL-0043](DL-0043-finance-trial-balance-per-currency.md) | 8b | Finance: `GET /periods/{yyyymm}/trial-balance` por moeda e por status (net operacional = AR−AP), sem plano de contas; endpoint novo aditivo (não muda `GET /periods`) | Alta | Barata |
| [DL-0044](DL-0044-billing-tax-regime-simples-and-swappable-strategy.md) | 8c | Billing: regime **Simples Nacional** (default) + emitente = Acme; ISS/retenções parametrizados por regime+município atrás de `TaxRegimeStrategy` trocável (Q7) | **Baixa** | **Cara** |
| [DL-0045](DL-0045-billing-module-and-taxable-base-is-commission.md) | 8c | Billing: novo módulo `domain.billing` (13º); `CommissionInvoice`; **base tributável = comissão** (nunca o pacote); referência ao lançamento por id+porta, sem FK | Alta | Moderada |
| [DL-0046](DL-0046-nfse-municipal-acl-port-and-traceable-mock.md) | 8c | Billing: NFS-e municipal como porta `NfseGateway` + adaptador ACL com **mock rastreável** em `infra.integration.nfse`; assinatura e-CNPJ via porta `CertificateSigner` (stub → Platform/SPEC-0023); falha classificada (TIMEOUT/UNAVAILABLE→502, REJECTED→422) | Média | Moderada |
| [DL-0047](DL-0047-billing-issue-idempotency-finance-event-and-compliance-archive.md) | 8c | Billing: emissão idempotente por comissão (UNIQUE parcial); arquiva NFS-e no Compliance via orquestrador `infra`; Finance lança o tributo consumindo `CommissionInvoiceIssued` (idempotente); `billing` é módulo **folha** (grafo acíclico) | Média | Moderada |
| [DL-0048](DL-0048-payout-payment-gateway-acl-async-webhook.md) | 8d | Payout: porta `PaymentGateway` + adaptador **mock rastreável com webhook assíncrono** (ADR 0006) em `infra.integration.payment`; idempotente por `(payoutId, installmentSeq, providerRef)`; falha classificada (sem "pago" falso); DTO do provedor não vaza (ArchUnit) | **Baixa** | Moderada |
| [DL-0049](DL-0049-payout-foreign-settlement-rate-and-brl-baixa.md) | 8d | Payout: liquidação do fornecedor modela `amount` (USD) + `settlementRate` (escala 6, >0) + `settledBrl` (baixa em BRL = amount × rate, HALF_UP); remessa internacional real adiada (mesmo gateway) | **Baixa** | **Cara** |
| [DL-0050](DL-0050-payout-installments-no-interest-and-exact-cent-distribution.md) | 8d | Payout: parcelamento **v1 sem juros**; Σ parcelas == total exato (resto de centavos na 1ª parcela); cada parcela executa/comprova; Payout só `EXECUTED` quando todas executam; "sem plano" = 1 parcela implícita | Média | Moderada |
| [DL-0051](DL-0051-payout-supplier-settled-consumed-by-finance-leaf-acyclic.md) | 8d | Payout folha: `SupplierSettled` consumido pelo **Finance** (listener idempotente, posta uma vez); Reconciliation/Exchange seguem fechando FX pela liquidação própria (costura ao evento adiada, sem ciclo); REFUND não cancela a obrigação do fornecedor (DL-0024) | Média | Moderada |
| [DL-0052](DL-0052-aftersales-sla-from-commercial-policy.md) | 8e | AfterSales: SLA = parâmetro governado resolvido pela CommercialPolicy (chaves `AFTERSALES_SLA_FIRST_RESPONSE`=24h/`_RESOLUTION`=72h/`_REFUND`=48h, NUMBER horas; seed SYSTEM_DEFAULT V23); Diretiva pode sobrepor sem deploy | Média | Barata |
| [DL-0053](DL-0053-aftersales-sla-breach-job-and-cost-to-serve.md) | 8e | AfterSales: breach por job de **relógio controlado** (`markBreaches(now)`, instante como parâmetro, padrão do Booking); breach é **flag/alerta** (não bloqueia, idempotente); custo de servir = `CostToServe` (Money BRL acumulável: handling+refund+reaberturas) | Média | Barata |
| [DL-0054](DL-0054-aftersales-orchestrates-cancel-and-refund-via-facades.md) | 8e | AfterSales orquestra via **fachadas** (`PayoutService.create` REFUND com `originRef`=caseId; `BookingService.cancel`), **idempotente** por `linkedPayoutId` (não cria 2 Payouts); BR6 — não muda reserva nem lança financeiro; armadilha do merchant intacta; grafo **acíclico** | Média | Moderada |
| [DL-0055](DL-0055-marketing-newsletter-acl-and-single-opt-in.md) | 8f | Marketing: porta `NewsletterSender` (ACL) + **mock rastreável**; consentimento **single opt-in** no v1 (modelo já comporta double opt-in sem refator) | Média | Moderada |
| [DL-0056](DL-0056-marketing-consent-append-history-current-state.md) | 8f | Marketing: `Consent` **append-only**; estado atual = última linha por `(titular, finalidade)`; revogação é nova linha; índice por `(subject, purpose, created_at DESC)` | Alta | Moderada |
| [DL-0057](DL-0057-marketing-attribution-intake-and-campaign-converted.md) | 8f | Marketing: atribuição por **intake próprio** (`code→booking`, UNIQUE) + confirmação na `BookingConfirmed` → publica `CampaignConverted`; **não** altera o evento do Booking; grafo acíclico | Média | Moderada |
| [DL-0058](DL-0058-marketing-lgpd-erasure-preserves-revocation-and-metrics-as-logs.md) | 8f | Marketing: exclusão LGPD remove PII de marketing mas **preserva tombstone de revogação** (anonimizado) p/ supressão futura; `attributions`/métricas sem PII permanecem | **Baixa** | **Cara** |
| [DL-0059](DL-0059-marketing-segment-criteria-json-and-crm-buy-vs-build.md) | 8f | Marketing: `Segment` com `criteria_json` **validado** (catálogo fechado, minimização BR3); fronteira **"não é CRM"** (CRM pleno = comprar, este módulo = consentimento/atribuição) | Média | Moderada |
| [DL-0060](DL-0060-portfolio-separate-context-from-assets.md) | 8g | Portfolio é contexto **separado** de Assets (Q2: dois contextos, não um); 17º módulo Modulith | Alta | Moderada |
| [DL-0061](DL-0061-portfolio-sell-without-active-contract-alerts-not-blocks.md) | 8g | Portfolio: vender marca **sem contrato vigente** apenas **alerta** (v1), não bloqueia; cobertura de contrato exposta como leitura | Média | Barata |
| [DL-0062](DL-0062-portfolio-brand-sale-attribution-intake-and-realized-projection.md) | 8g | Portfolio: realizado por marca via **intake próprio** (`booking→brandRef`, UNIQUE) + projeção idempotente de `BookingConfirmed` (VOLUME) e `SpreadRealized` (REVENUE); **não** altera o evento da venda | **Baixa** | Moderada |
| [DL-0063](DL-0063-portfolio-representation-expiring-controlled-clock-alert.md) | 8g | Portfolio: `RepresentationExpiring` por **job de relógio controlado** (antecedência 30d, idempotente, alerta — não bloqueio) | Média | Barata |
| [DL-0064](DL-0064-assets-separate-context-lean-registry.md) | 8h | Assets é contexto **separado** de Portfolio (Q2: dois contextos); registro enxuto de patrimônio; 18º módulo Modulith | Alta | Moderada |
| [DL-0065](DL-0065-assets-no-depreciation-buy-vs-build-seam.md) | 8h | Assets **sem depreciação/gestão plena** (Out of Scope): registro + seam comprar-vs-construir | Alta | Moderada |
| [DL-0066](DL-0066-assets-license-expiry-controlled-clock-job-30d.md) | 8h | Assets: alerta de licença a vencer por **job de relógio controlado** (30d, idempotente por `expiry_signaled_at`); `?expiringWithinDays=N` ad-hoc | Média | Barata |
| [DL-0067](DL-0067-assets-publishes-events-leaf-no-consumers-now.md) | 8h | Assets é **folha**: publica `AssetRegistered`/`AssetLicenseExpiring` in-process; **não** fia consumidores Finance/Intelligence (custo automático = regra inexistente) | Média | Barata |
| [DL-0068](DL-0068-assets-retire-audit-inline-and-status-machine.md) | 8h | Assets: baixa (RETIRED) **auditada inline** (retired_at/by/reason); ACTIVE→RETIRED terminal; re-baixar → 409 | Média | Barata |
| [DL-0069](DL-0069-people-journey-as-pure-service-over-operational-snapshot.md) | 8i | People: jornada/banco como serviço de domínio puro sobre o snapshot operacional (não reescreve o crawler); `snapshotRef` por valor | Média | Moderada |
| [DL-0070](DL-0070-people-timebank-monthly-balance-and-clt-compensation-window.md) | 8i | People: banco de horas = saldo mensal (extras/faltas, sinal) + janela de compensação CLT configurável (default 6 meses, art. 59) | **Baixa** | Moderada |
| [DL-0071](DL-0071-people-discrepancy-detection-alerts-not-corrects.md) | 8i | People: divergência (ímpar/faltante/incoerente) sinaliza alerta + fila, **nunca corrige** (BR4); idempotente | Média | Barata |
| [DL-0072](DL-0072-people-payslip-archived-in-compliance-by-value.md) | 8i | People: holerite no Compliance (PAYROLL, retenção 5a, `hasPersonalData`) via orquestrador infra; `documentId` por valor | Alta | Barata |
| [DL-0073](DL-0073-platform-new-domain-module.md) | 8j | Novo módulo `domain.platform` (20º Modulith): contexto Platform real (custódia/jobs/auditoria); gradua o seam adiado da DL-0030 | Alta | Moderada |
| [DL-0074](DL-0074-platform-certificate-encryption-at-rest.md) | 8j | Custódia e-CNPJ: criptografia at-rest AES-256-GCM (envelope), chave fora do banco; metadados em claro, material cifrado, nunca em log/evento/DTO | **Baixa** | **Cara** |
| [DL-0075](DL-0075-platform-job-registry-and-postgres-advisory-lock.md) | 8j | Governança de jobs: registro `ScheduledJob`/`JobRun` + idempotência por `(job_name, window)` + advisory lock no Postgres (sem ShedLock/Quartz) | Alta | Moderada |
| [DL-0076](DL-0076-platform-initial-job-catalog-and-scheduler-wiring.md) | 8j | Catálogo inicial = jobs já ativados (crawler/SLA/licença/representação/retenção/certificado); schedulers existentes registram `JobRun` via porta, lógica fica no dono | Média | Moderada |
| [DL-0077](DL-0077-platform-system-audit-append-only-via-event-listener.md) | 8j | Auditoria de sistema append-only via listener de eventos in-process + fachada `record(...)`; só metadados mascarados, sem segredo | Alta | Moderada |
| [DL-0078](DL-0078-platform-certificate-signer-graduated-from-billing-stub.md) | 8j | `CertificateSigner` graduado para o Platform; stub do Billing **delega** à custódia (mantém a porta do Billing; back-compat) | Média | Moderada |
| [DL-0079](DL-0079-identity-inhouse-jwt-resource-server-idp-boundary.md) | 8k | Identity: auth real in-house (Spring Security + JWT HS256) no 8k; OIDC externo vivo na Fase 13; porta `UserContextProvider` é o seam — **RESOLVIDO na Fase 13** | **Baixa** | **Cara** |
| [DL-0080](DL-0080-identity-new-domain-module-and-local-user-store.md) | 8k | Novo módulo `domain.identity` (21º) + tabela local mínima de usuários/papéis (V29); auditoria reusa `system_audit` | Alta | Moderada |
| [DL-0081](DL-0081-identity-graduate-stub-behind-profile-keep-tests-green.md) | 8k | Gradua o stub: porta intacta; `JwtUserContextProvider` em prod/default; stub permissivo atrás de profile `dev`/`test`; `TestSecurityConfig` mantém os 434 testes verdes com a segurança montada (não removida) | Alta | Barata |
| [DL-0082](DL-0082-identity-role-permission-model-and-sensitive-action-mapping.md) | 8k | Modelo papel→permissão (catálogo fechado) + mapa das ações sensíveis (DIRECTIVE→DIRECTOR, NF→FINANCE, job→IT); enforcement HTTP (Spring Security) + reafirma a checagem de domínio (DL-0038) | Média | Moderada |
| [DL-0083](DL-0083-identity-access-audit-reuses-platform-system-audit.md) | 8k | Auditoria de acesso reusa `system_audit` (Platform/8j): `AUTH_LOGIN`/`ACCESS_DENIED`; `GET /access-audit` é leitura focada; sem tabela nova (Regra Zero) | Alta | Moderada |
| [DL-0084](DL-0084-admin-new-module-lean-registry-no-procurement.md) | 8l | Admin é módulo Modulith próprio (22º), registro enxuto; procurement completo = comprar (fronteira) | Alta | Moderada |
| [DL-0085](DL-0085-admin-expense-kind-to-entrytype-and-document-map.md) | 8l | Mapa despesa `kind`→`EntryType`→documento (UTILITY→UTILITY_EXPENSE/UTILITY_BILL; autônomo→AUTONOMOUS_SERVICE/RPA; PJ→SERVICE/NFSE; OTHER→OTHER_EXPENSE/—); `EntryType` +SERVICE/+OTHER_EXPENSE (aditivo); seed Compliance aditivo (sem editar V8) | Média | Barata |
| [DL-0086](DL-0086-admin-finance-compliance-integration-via-facades-acyclic.md) | 8l | Admin posta lançamento por **chamada síncrona** à fachada `FinanceService.register`; idempotência por UNIQUE `(supplier, period, kind)`; documentos exigidos via nova porta de leitura `DocumentRequirementDirectory` (Compliance); módulo folha, grafo acíclico | Alta | Moderada |
| [DL-0087](DL-0087-admin-contract-expiring-controlled-clock-alert.md) | 8l | `AdminContractExpiring` por job de **relógio controlado** (horizonte 30d, idempotente por `expiry_signaled_at`, alerta — não bloqueia); padrão Portfolio/Assets | Média | Barata |
| [DL-0088](DL-0088-admin-sensitive-endpoints-gated-by-role-finance-and-audited.md) | 8l | Escritas do Admin exigem **ROLE_FINANCE** (negação 403 + auditoria); alteração de fornecedor/contrato/despesa auditada via `system_audit` (`ADMIN_CHANGE`, metadados only) | Média | Barata |
| [DL-0089](DL-0089-flatten-internal-encapsulation-by-module-internal-marker.md) | 9 | Pós-achatamento do `internal`: encapsulação por marcador de tipo **`@ModuleInternal`** + regra ArchUnit (nenhum outro módulo depende de tipo `@ModuleInternal`; exceção `infra` e o próprio módulo); predicados Intelligence/Portfolio/Platform trocam `.internal` pelo marcador; Modulith mantido p/ ciclos/grafo | Alta | Moderada |
| [DL-0090](DL-0090-frontend-primeng21-aura-tailwind4-graduates-dl0003.md) | 10 | Stack de UI: **PrimeNG 21 (Aura via `@primeuix/themes`) + Tailwind v4 (camadas CSS) + @angular/cdk + primeicons**; gradua DL-0003 | Alta | Moderada |
| [DL-0091](DL-0091-theme-toggle-darkmode-selector-and-tokens.md) | 10 | Tema claro/escuro: `ThemeService` + seletor `.app-dark` (Aura) + tokens `--app-*`; persiste em `localStorage`, default = `prefers-color-scheme` | Alta | Barata |
| [DL-0092](DL-0092-silent-refresh-via-me-validation-no-refresh-token.md) | 10 | Silent refresh = revalidação silenciosa via `GET /api/identity/me` (boot + perto da expiração); **sem refresh token** (o backend não oferece; refresh real fica p/ Fase 13/OIDC) — **RESOLVIDO na Fase 13 (ver DL-0106)** | Média | Moderada |
| [DL-0093](DL-0093-command-palette-and-keyboard-shortcuts.md) | 10 | Paleta `Ctrl/Cmd+K` própria (Dialog+CDK, sem lib) + `CommandRegistry`/`ShortcutService` central; atalhos ignoram campos editáveis; `?` lista atalhos da mesma fonte | Alta | Barata |
| [DL-0094](DL-0094-dashboard-kpis-client-side-from-existing-endpoints.md) | 10 | Dashboard KPIs **calculados no cliente** dos endpoints de lista existentes (accounts/bookings/reconciliation/exchange); **sem endpoint/migração novos** (preferir frontend-only) | Alta | Barata |
| [DL-0095](DL-0095-actuator-exposure-and-security-role-it.md) | 11 | Actuator: expor só `health`/`info`/`prometheus`/`metrics`; `health`/`info`/`/api/version` públicos; `prometheus`/`metrics` atrás de **ROLE_IT**; `env`/`beans`/`heapdump` não expostos | Média | Barata |
| [DL-0096](DL-0096-json-logging-native-spring-boot-and-masking.md) | 11 | Logs JSON pelo **logging estruturado nativo** do Spring Boot (`ecs`, ligado no container), correlation id via MDC; mascaramento = higiene de não-logar segredo/PII (sem encoder custom) | Alta | Barata |
| [DL-0097](DL-0097-api-version-from-build-info-and-git.md) | 11 | `GET /api/version` = `{version, gitCommit, buildTime}` por **build-info** (Spring Boot) + `git-commit-id-maven-plugin`, beans opcionais com **degradação graciosa**; público | Alta | Barata |
| [DL-0098](DL-0098-business-metrics-via-infra-event-listener.md) | 11 | Métricas de **negócio** sobre eventos **já publicados**, por listener `BusinessMetrics` em **infra** (`@TransactionalEventListener` AFTER_COMMIT); `domain` não importa Micrometer (ArchUnit) | Alta | Barata |
| [DL-0099](DL-0099-jacoco-backend-coverage-threshold.md) | 12 | Cobertura backend como portão: JaCoCo INSTRUCTION ≥ **80%** no `verify` (medido 89%); piso de não-regressão, não 100% | Alta | Barata |
| [DL-0100](DL-0100-vitest-frontend-coverage-threshold.md) | 12 | Cobertura frontend como portão: Vitest/v8 com `coverageThresholds` no `angular.json` (statements/lines 65, functions 48, branches 55); `ng test` é o gate | Alta | Barata |
| [DL-0101](DL-0101-e2e-isolated-ephemeral-stack.md) | 12 | Isolamento do stack E2E: `compose.e2e.yaml` com Postgres **efêmero/tmpfs** (sem volume), portas 4201/8081, perfil `dev` p/ seed; dev DB provado intacto | Alta | Barata |
| [DL-0102](DL-0102-playwright-journeys-sadpaths-ci.md) | 12 | Playwright (chromium/headless/`E2E_BASE_URL`) + jornadas críticas e caminhos tristes (401/403/vazio/não-salvos) + job de E2E no CI (`if: always()`) | Alta | Barata |
| [DL-0103](DL-0103-identity-dev-idp-keycloak-realm-clients-roles.md) | 13 | Identity: **IdP de dev = Keycloak** (realm `acme` importado + client SPA público PKCE + papéis base + usuários seed); resolve a Open Question "qual IdP" para dev/E2E | **Baixa** | **Cara** |
| [DL-0104](DL-0104-identity-resource-server-jwks-realm-roles-mapping.md) | 13 | Identity: backend é Resource Server validando JWT do IdP por **JWKS (RS256/rotação)**; `realm_access.roles`→autoridades + `SCOPE_*` expostos; `UserContextProvider` intacto | Média | Moderada |
| [DL-0105](DL-0105-identity-test-jwks-local-keypair-and-login-contract-removal.md) | 13 | Identity: testes mintam **RS256 com JWKS local de teste** (sem IdP na internet); `POST /api/identity/login` in-house **removido** (login move ao IdP — breaking em 0.23.0) | Média | Moderada |
| [DL-0106](DL-0106-identity-frontend-oidc-code-pkce-real-silent-refresh.md) | 13 | Identity (frontend): login **OIDC Authorization Code + PKCE** (`angular-oauth2-oidc`) + **silent-refresh real** (refresh token); gradua DL-0092 | Média | Moderada |
| [DL-0107](DL-0107-identity-role-catalogue-retained-local-user-store-retired.md) | 13 | Identity: catálogo papel→permissão **permanece local** (fonte do enforcement, BR5); store local de **usuários** aposentado (V31, usuários vivem no IdP) | Alta | Moderada |
| [DL-0108](DL-0108-upgrade-spring-boot-4-classic-starter-jackson2-bridge.md) | 14 | Upgrade para **Spring Boot 4.0.7** + Modulith 2.0.7 + springdoc 3.0.3 (Java 21 mantido); ponte **`spring-boot-starter-classic`** mantém Jackson 2 na produção (migração p/ Jackson 3 = débito rastreado); correções de teste (TestRestTemplate relocado, `@AutoConfigureMetrics`, `JsonNode`→Jackson 3, 422→`UNPROCESSABLE_CONTENT`) sem afrouxar portão; **substitui DL-0002** (ver ADR 0017) | Alta | Moderada |
| [DL-0109](DL-0109-ui-gap-telas-adiadas-nunca-construidas.md) | 16 | **Gap de UI:** backend completo (37 controllers) mas frontend só cobre ~5 módulos — telas adiadas "→ Fase 10" nunca construídas (SPEC-0026 se escopou como repaginar as 5 existentes). Registrado a pedido do dono; quitado na **Fase 16** (telas de operação, SPEC-0029, 4 releases MINOR frontend-only) + gate de "usável" na DoD go-forward | Alta | Barata |
| [DL-0110](DL-0110-self-hosted-authorization-server-embedded-and-roles-claim.md) | 17 | Identity: **Spring Authorization Server embutido** no app (três `SecurityFilterChain`s); access token com claim **`realm_access.roles`** preservado → Resource Server e testes inalterados; **SUBSTITUI a DL-0103** e reaponta a DL-0104 (ADR-0018) | Média | Moderada |
| [DL-0111](DL-0111-registered-spa-client-public-pkce.md) | 17 | Identity: client SPA público **`acme-erp-web`** (code+PKCE, `NONE`, sem consent) registrado no AS self-hosted; espelha o client do realm Keycloak | Média | Barata |
| [DL-0112](DL-0112-reintroduce-local-user-store-v32.md) | 17 | Identity: store local de usuários **reintroduzido** (V32, BCrypt, `UserDetailsService`, seeder dev/E2E) para o AS autenticar; catálogo papel→permissão intacto; **reaponta a DL-0107** | Alta | Moderada |
| [DL-0113](DL-0113-frontend-issuer-self-hosted-silent-refresh-iframe.md) | 17 | Identity (frontend): `issuer` → **próprio app**; silent-refresh por **iframe silencioso** (SAS não emite refresh token a client público); **reaponta a DL-0106** | Média | Moderada |
| [DL-0114](DL-0114-keycloak-fully-removed-compose-infra-env.md) | 17 | Infra: **Keycloak removido 100%** (serviço no compose dev/E2E, `infra/keycloak/`, `KEYCLOAK_*`/`OIDC_*` reapontadas); encerra a infra da DL-0103 | Alta | Moderada |
| [DL-0115](DL-0115-enum-to-cadastro-code-value-and-validator-port.md) | 18a | Padrão **enum→cadastro**: o valor persistido vira `code` (String) **= nome do enum** (JSON inalterado), validado pela porta `CadastroValidator`; ramificação preservada por constantes `*Codes`; escrita gated por `ROLE_POLICY_ADMIN` | Alta | Moderada |
