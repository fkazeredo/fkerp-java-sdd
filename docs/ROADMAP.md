# ERP Acme Travel — Roadmap de Construção

> Como construir o ERP com o Claude Code: **fatias verticais, sempre começando pelos testes,
> cada uma um entregável de ponta a ponta**. Acompanha o `docs/TUTORIAL.md` (o passo a passo de
> cada sessão) e as specs em `docs/specs`. Convenção: prosa em pt-BR, código em inglês.

## Princípios deste roadmap (e por que ele é assim)

1. **Fatia vertical, não camada horizontal.** Cada fatia atravessa a stack inteira (migração →
   domínio → API → e, quando entrega valor de tela, Angular) e é *demonstrável e implantável* ao
   fim. Nada de "primeiro todas as entidades, depois todos os serviços".
2. **Teste primeiro.** O ponto de partida de cada fatia é o teste de aceite/integração escrito a
   partir dos exemplos e critérios de aceite da spec. Vermelho → verde → refatora.
3. **Specs sob demanda, não tudo de uma vez.** As regras do projeto **proíbem** despejar ~20 specs
   especulativas no começo: `workflow.md` (*New project creation*: "MUST NOT create a huge empty
   architecture with unused modules, fake bounded contexts or placeholder classes"),
   `core-principles.md` (*current business need over speculative future need*) e a Regra Zero
   (`CLAUDE.md`). Por isso: **a Fase 1 já vem 100% especificada** (SPEC-0001..0005); as demais são
   *cards* aqui e viram spec **quando a fatia chega**.
4. **Adiar costuras com mock rastreável.** Quando uma fatia esbarra em algo fora de escopo
   (motor de precedência de `CommercialPolicy`, faixas de override, escopo de comissão, exposição
   cambial), criamos um *stub explícito que referencia a spec futura* — nunca lógica falsa em
   produção (`simulation-and-mocking.md`).
5. **Manual primeiro.** O motor de sugestão roda só na origem `MANUAL` no v1; `INTEGRATED`
   (preço externo confiável) é adiado (redesenho Parte 4.3). O núcleo manual é o que tem **menos
   perguntas em aberto** — é o lugar seguro para começar.
6. **Monólito modular.** Empacotamento único, fronteiras duras por `ArchUnit` + Spring Modulith.
   Microsserviço só com motivo concreto (`core-principles.md`).

A decisão de conjunto inicial de módulos e ordem está registrada em `docs/adr/0014`.

---

## Mapa das fases

```
FASE 0  Fundação .................. SPEC-0001  (esqueleto que sobe, testa e tem CI; Event Storming)
FASE 1  Núcleo comercial manual ... SPEC-0002 Accounts
        (a "Fatia 1" do redesenho,  SPEC-0003 Exchange (taxa congelada / Open-Host)
         fatiada fina)             SPEC-0004 Commissioning (duas pontas, % fixo)
                                    SPEC-0005 Quoting (composição + sugestão + override)  ← keystone
                                    SPEC-0006 Booking (ciclo de vida + localizador manual)
                                    SPEC-0007 Reconciliation (casa as pontas + ganho/perda cambial)
FASE 2  Compliance mínimo ......... SPEC-0008 (cofre + anexo obrigatório + fechamento mensal + retenção)
FASE 3  Primeira integração real .. SPEC-0009 (site de cotação: ACL, ramo INTEGRATED)
FASE 4  Cancelamento + merchant ... SPEC-0010 (política como objeto + armadilha ALL_SALES_FINAL)
FASE 5  Câmbio com exposição ...... SPEC-0011 (mercado vs congelada: subsídio × drift + 1ºs relatórios)
FASE 6  Crawler de ponto .......... SPEC-0012 (snapshot p/ People + AFD/AEJ p/ Compliance, fila + disjuntor)
FASE 7  Intelligence (DSS) ........ SPEC-0013 (OverrideNudge + PromoFxAdvisor)
FASE 8+ Apoio e genéricos ......... Billing, Payout, AfterSales, Marketing, Portfolio, Assets ...
FASE 9  Limpeza estrutural ........ ADR+chore (remover pacotes internal do domain — herança Go)
FASE 10 UX & Frontend profissional  SPEC-0026 (PrimeNG Aura + Tailwind v4 + shell + command palette + tema)
FASE 11 Observabilidade ........... SPEC-0027 (Micrometer/Prometheus/Loki/Grafana + logs JSON + /api/version)
FASE 12 Qualidade & E2E ........... SPEC-0028 (Playwright isolado + coverage + sad paths)
FASE 13 Identity/AuthZ ............ gradua SPEC-0024 (OAuth2 Resource Server JWT, escopos->perfis)
FASE 14 Upgrade de stack .......... ADR (Spring Boot 3.5 -> 4.x; ngx-graph se necessário)
FASE 15 Documentação bilíngue ..... regra+chore (manual+docs pt-BR + en-US, em sincronia)
(9-15: trazidas do estudo do fkerp-poc — transversais; intercalar com 8c-8l)
```

Ordem de dependência da Fase 1 (depois da 0, as três primeiras são independentes; Quoting amarra tudo):

```
Slice 0 (esqueleto)
   ├── Slice 1  Accounts        (sem dependência de domínio)
   ├── Slice 2  Exchange        (sem dependência de domínio)
   └── Slice 3  Commissioning   (cálculo puro, sem dependência)
            └── Slice 4  Quoting  → consome Accounts + Exchange + Commissioning + CommercialPolicy(stub)
                     └── Slice 5  Booking
                              └── Slice 6  Reconciliation
```

---

## FASE 0 — Fundação

### Slice 0 — Esqueleto que anda + Event Storming · `SPEC-0001`
- **Entrega:** monólito modular (`com.fksoft`, 3 camadas), Postgres via `docker-compose`, Flyway
  baseline, `GlobalExceptionHandler`/`ApiErrorResponse`, `UserContextProvider` (stub de dev),
  i18n, correlation id, health liveness/readiness, **ArchUnit + Spring Modulith verde**, CI mínimo,
  e uma tela Angular que consome `/api/system/health`. Mais o doc de **Event Storming** da venda
  Portal de Experiências.
- **Por que primeiro:** prova a stack ponta a ponta e **trava as regras de arquitetura antes do
  primeiro código de negócio**. É a única fatia sem valor de negócio — é fundação.
- **Travas (perguntas):** pacote base `com.fksoft` (renomear é decisão sua). Nenhuma que bloqueie.

---

## FASE 1 — Núcleo comercial manual

> Objetivo da fase: **vender manualmente um carro em Orlando, com câmbio congelado, as duas
> comissões, o spread, override com rastro e conciliação — sem integrar nada** (redesenho, Fatia 1).

### Slice 1 — Accounts (Conta Comercial) · `SPEC-0002`
- **Entrega:** cadastrar/consultar/listar agência ou agente (CNPJ/MEI/CPF) com validação de
  dígitos, unicidade de documento, status. É a porta de entrada de toda operação comercial.
- **Não faz dinheiro** (Parte 6). Carteira, limite de crédito e validação externa de CADASTUR/IATA
  ficam fora.
- **Travas:** quais cadastros são obrigatórios por tipo de conta (em aberto).

### Slice 2 — Exchange · taxa congelada (Open-Host) · `SPEC-0003`
- **Entrega:** o diretor fixa uma **taxa única de venda** por par de moeda; o sistema serve a taxa
  vigente a quem compõe cotação (Open-Host). Histórico append-only.
- **Adiado para a Fase 5:** taxa de mercado, exposição/posição do livro, subsídio × drift.
- **Travas:** nome `Exchange` (assumido); escopo por agência/produto da taxa (redesenho 7.3 "a
  confirmar") — adiado.

### Slice 3 — Commissioning · duas pontas, % fixo · `SPEC-0004`
- **Entrega:** calcular comissão do fornecedor (a receber), comissão do agente (a pagar) e o
  **spread** derivado, com % fixos. Fatia **sem tabela** (cálculo puro + fachada + endpoint de
  preview) — bom exemplo de fatia stateless.
- **Adiado:** faixas de override retroativas (Q4), escopo da comissão do agente (Q5), exclusões de
  base por fornecedor, imposto (ISS) sobre a comissão.

### Slice 4 — Quoting · composição + sugestão + override · `SPEC-0005` · **keystone**
- **Entrega:** compor o **preço sugerido** de uma venda manual a partir de preço-base + câmbio
  congelado + comissão de duas pontas + markup; persistir `suggestedAmount` × `appliedAmount`;
  registrar `OverrideRecord {quem, quando, de→para, motivo}` quando o humano diverge. Tudo com
  **proveniência** (qual taxa, quais %, qual markup — congelados na composição).
- **É a fatia que materializa a tese:** "o sistema sempre calcula e sugere; o humano pode divergir,
  mas a divergência fica registrada contra a sugestão".
- **Mock rastreável:** o markup vem de um `MarkupProvider` stub de `CommercialPolicy` (o motor de
  precedência Diretiva > Promoção > Contrato > Política > Padrão é de uma spec futura).
- **Travas (vivas):** a **fórmula exata de preço** (markup sobre a base vs. tarifa repassada; moeda
  da base comissionável) precisa da sua confirmação; + Q4/Q5.

### Slice 5 — Booking · ciclo de vida + localizador manual · `SPEC-0006` *(card → spec quando chegar)*
- Reserva `QUOTED → ORDERED → PENDING(72h) → CONFIRMED → (CHANGED|CANCELLED|NO_SHOW) → COMPLETED`,
  localizador (inclusive externo digitado), e o disparo dos eventos de accrual de comissão na
  confirmação. Política de cancelamento entra como objeto simples (a versão rica é a Fase 4).

### Slice 6 — Reconciliation · casa as pontas · `SPEC-0007` *(card → spec quando chegar)*
- Cruza a pagar (moeda estrangeira, vence depois) × a receber (reais) × comissão esperada ×
  recebida × ganho/perda cambial. Fecha o ciclo econômico do núcleo.

---

## FASE 2 — Compliance mínimo

### Slice 7 — Cofre de documentos + anexo obrigatório + fechamento · `SPEC-0008` *(card)*
- `Document` (type, fileRef, hash, issuedAt, retentionUntil), `DocumentRequirement` por tipo de
  lançamento, e **trava de fechamento mensal**: lançamento em AP/AR sem o documento exigido **não
  fecha o mês**. Retenção legal por documento (Parte 7.7). Barato e protege a empresa cedo.

---

## FASE 3 — Primeira integração real (ACL)

### Slice 8 — Site de cotação · ramo INTEGRATED · `SPEC-0009` *(card)*
- Primeira ACL de verdade, com `Platform` monitorando. Prova o ramo `INTEGRATED`
  (`trustExternalPrice = true`, sem recompor) e ativa o gancho adormecido do redesenho.

---

## FASE 4 — Cancelamento como objeto + armadilha do merchant

### Slice 9 · `SPEC-0010` *(card)*
- `CancellationPolicy` (STANDARD | ALL_SALES_FINAL | CUSTOM, janelas de multa, costBearer),
  `NoShowPolicy`, e a **armadilha**: em `ALL_SALES_FINAL` o portal é cobrado mesmo reembolsando o
  cliente — duas obrigações que não se anulam.

## FASE 5 — Câmbio com exposição + relatórios

### Slice 10 · `SPEC-0011` *(card)*
- Decompõe o gap: **subsídio (intencional) × drift (risco)**, posição agregada do livro
  (`ExchangeExposure`), e os primeiros relatórios (`PromoFxResult`, `LiveExposure`).

## FASE 6 — Crawler de ponto

### Slice 11 · `SPEC-0012` *(card)*
- `PointClockCrawler` em `Integration`, orquestrado por `Platform`: snapshot operacional para
  `People`; **AFD/AEJ assinado** capturado da exportação oficial e guardado em `Compliance`
  (retenção 5 anos). Fila + disjuntor; nunca escreve no miolo, só publica evento/snapshot.
- **Trava:** tipo de REP (Q6) muda como o AFD é capturado.

## FASE 7 — Intelligence (DSS) prescritivo

### Slice 12 · `SPEC-0013` *(card)*
- Começa por **`OverrideNudge`** e **`PromoFxAdvisor`** (lucro direto). Read-model que escuta
  eventos de todos os contextos e **aconselha, nunca comanda** (guardrail alerta, humano decide).
  Saída sempre validada antes de afetar estado (`messaging-and-integrations.md`, seção AI).

## FASE 8+ — Apoio e genéricos
- `SPEC-0014` CommercialPolicy (parâmetros governados + precedência — gradua o stub de markup da 0005),
  `SPEC-0015` Finance (AP/AR + fechamento — **pré-requisito do Compliance**), `SPEC-0016` Billing (NF de
  comissão/ISS/retenções), `SPEC-0017` Payout (repasse/liquidação/reembolso), `SPEC-0018` AfterSales,
  `SPEC-0019` Marketing (LGPD/consentimento), `SPEC-0020` Portfolio, `SPEC-0021` Assets, `SPEC-0022`
  People (consome o snapshot do crawler), `SPEC-0023` Platform (certificado/jobs), `SPEC-0024` Identity
  (gradua o stub de auth), `SPEC-0025` Admin. Finance/Identity/Admin/Assets são **genéricos**: a spec
  entrega a fronteira + o seam + a decisão **comprar vs. construir** (não um sistema caseiro completo).

> **Sequenciamento Finance ↔ Compliance:** o `Compliance` (SPEC-0008, Fatia 2) **veta** o fechamento, mas
> o **razão AP/AR e a máquina de período** são do `Finance` (SPEC-0015). Logo, o **seam mínimo de AP/AR +
> período do Finance co-entrega com o Compliance na Fatia 2**, embora o Finance seja "genérico" e seu
> grosso (contabilidade plena) fique para comprar/integrar depois.

---

## FASE 9 — Limpeza estrutural: remover `internal` do domain · ADR + chore

> Fases 9–15 vêm do estudo do projeto irmão **fkerp-poc** (mais maduro/profissional). São
> **transversais** e podem ser intercaladas com as fases 8c–8l. Meta: **acabamento profissional**
> (nada de tela genérica; estados loading/empty/error/permissão; acessível e responsivo).

O layout atual tem `com.fksoft.domain.<módulo>.internal.*` — convenção de visibilidade do **Go**, que
não se aplica ao Java/Spring Modulith. **Achatar:** mover os tipos de `…/<módulo>/internal/` para
`…/<módulo>/`, em `main` e `test`, nos 11 módulos (`accounts, booking, commercialpolicy, compliance,
exchange, finance, intelligence, people, quoting, reconciliation, sourcing`). Preservar a encapsulação
via `@NamedInterface`/ArchUnit; refactor **estrutural** (sem mudar comportamento/JSON); `./mvnw verify`
verde antes e depois. **Aceite:** nenhum pacote `internal` sob `com.fksoft.domain`; gates verdes.

## FASE 10 — UX & Frontend profissional · `SPEC-0026` (nova)

Elevar o frontend ao padrão do fkerp-poc (a base Angular 22 zoneless + signals já casa). **Trazer:**
PrimeNG 21 (preset **Aura** via `@primeuix/themes`) + primeicons + `@angular/cdk` (gradua a DL-0003);
**Tailwind v4** integrado ao PrimeNG por camadas CSS; **shell SaaS** (sidebar, top bar, drawer mobile);
navegação orientada a workflow; **tema claro/escuro** (`ThemeService` + tokens `--app-*`); **paleta de
comandos** `Ctrl/Cmd+K` + atalhos globais/contextuais + `?` ajuda + autofoco; **proteção de não-salvos**
(`canDeactivate`); **estados reais** em toda tela; **login** com silent refresh; **dashboard com KPIs**.
**Aceite:** todas as telas repaginadas ao padrão profissional; `ng lint`/`ng test`/`ng build` verdes;
nada fora do i18n.

## FASE 11 — Observabilidade & monitoramento · `SPEC-0027` (nova)

Trazer a stack de observabilidade do fkerp-poc (`infra/`): **Micrometer + Actuator + Prometheus**
(`/actuator/prometheus`); **logs estruturados em JSON** (com correlation id, sem segredos/dado
pessoal); **Prometheus + Loki + Grafana Alloy + Grafana** via Docker Compose (datasources e dashboard
pré-provisionados); endpoint `GET /api/version`. **Aceite:** `docker compose up` sobe
app+db+observabilidade; métricas/logs no Grafana; `/actuator/health` e `/actuator/prometheus` expostos;
`/api/version` responde a versão.

## FASE 12 — Qualidade & E2E · `SPEC-0028` (nova)

**Playwright** nas jornadas críticas, em **stack isolada/descartável** (`compose.e2e.yaml`: Postgres
efêmero em `tmpfs`, frontend **4201**, scripts `e2e:up`/`e2e:down`, `baseURL` via `E2E_BASE_URL`) —
**nunca** toca o banco de dev; **cobertura** (`@vitest/coverage-v8` no front + JaCoCo no back);
**caminhos tristes** sistemáticos (401/403/400/404/409/422, idempotência, bordas); **job de E2E no CI**.
**Aceite:** suíte Playwright verde na 4201 com o banco de dev intacto; cobertura reportada; CI com E2E.

## FASE 13 — Identity/AuthZ profissional · gradua `SPEC-0024`

Trocar o stub de identidade pelo modelo da POC: **Spring Security + OAuth2 Resource Server (JWT)**,
autorização **por escopo** (ex.: `crm:lead:read:all`) agrupada em **perfis**; o backend é a única
autoridade e o frontend apenas espelha; seeds de usuário por perfil (dev); substitui o
`DevStubUserContextProvider` mantendo a porta `UserContextProvider`. Consolida/gradua a **SPEC-0024**
(Fase 8k). **Aceite:** login + refresh; endpoints protegidos por escopo; testes de guard/interceptor e
de autorização (sad paths) verdes.

## FASE 14 — Upgrade de stack (Spring Boot 4 / versões) · ADR

Avaliar alinhar o backend ao fkerp-poc, que usa **Spring Boot 4** (este está em **3.5.16** por
estabilidade, DL-0002). **ADR** de upgrade 3.5 → 4.x (Spring Framework 7, Spring Modulith 2.x, APIs
deprecadas, Testcontainers/Flyway) — só executar com **gates verdes**. Avaliar `@swimlane/ngx-graph`
(editor visual de workflow) **só se** houver necessidade real de workflow configurável — a POC
construiu e depois **reverteu parcialmente** um motor de workflow configurável por excesso de custo
(lição de Regra Zero; não antecipar).

## FASE 15 — Documentação bilíngue (pt-BR + en-US) · regra + chore

Padronizar a documentação voltada ao **usuário/cliente** em **pt-BR + en-US**, mantidas em sincronia
(como no fkerp-poc). **Já feito:** manual bilíngue (`docs/MANUAL.md` + `docs/MANUAL.en-US.md` + regra no
`CLAUDE.md`). **Estender:** **release notes** (`docs/release-notes/`, versão en-US) e eventuais guias de
usuário; a mesma fatia atualiza **as duas** línguas. **Fora de escopo:** relatórios técnicos de
desenvolvimento ficam **só pt-BR** (engenharia interna — Regra Zero). **Aceite:** docs de
usuário/cliente em pt-BR **e** en-US; nenhuma versão defasada.

> **Documentação por fase (Definition of Done):** ao entregar qualquer fase, atualize também
> `docs/MANUAL.md` + `docs/MANUAL.en-US.md` e a documentação afetada (spec/ADR/release note/
> `docs/ROADMAP-STATUS.md`). Nenhuma mudança visível ao usuário é "pronta" sem o manual refletindo-a.

---

## FASE 16 — Telas de operação (completar a UI) · `SPEC-0029` · **✅ concluída**

Quitou a dívida do **DL-0109**: o backend tinha ~22 módulos mas o frontend cobria ~5. Em 4 fatias
(16a–16d, releases `0.24.0`–`0.27.0`) todo módulo do backend ganhou **tela de operação** (Finance,
Billing, Payout, Compliance, AfterSales, Sourcing, Exchange-desk, Cancelamento, Intelligence/DSS,
CommercialPolicy, Marketing, Portfolio, People/RH, Ponto, Assets, Admin, Platform/TI, Identity/acesso),
reusando o padrão da SPEC-0026 (service+`PageResponse`, `<app-screen-state>`, nav por papel). Correção
de processo: capacidade de negócio agora exige tela na Definition of Done.

## FASE 17 — Substituir Keycloak por auth server self-hosted (Spring Authorization Server) · re-gradua `SPEC-0024`

Decisão do dono: **remover 100% do Keycloak** (serviços em `docker-compose.yml`/`compose.e2e.yaml`,
`infra/keycloak/`, `KEYCLOAK_*`) e servir OIDC pelo próprio Spring via **Spring Authorization Server**
self-hosted (**sem novo Docker** — embutido ou módulo co-localizado): `/oauth2/authorize|token|jwks`,
`/login`, `/userinfo`, **user store local** de volta (BCrypt, migração V32), cliente SPA público
PKCE+refresh, claim de papéis. **Preserva** o front OIDC+PKCE e o Resource Server (só troca o IdP). ADR-0018;
substitui DL-0103, reaponta DL-0104…0107. Entregáveis completos + gates verdes. **Aceite:** login e
papéis funcionando sem Keycloak (dev + E2E), `./mvnw verify` + gates de front verdes. MINOR `0.28.0`
(**breaking** — Keycloak sai). *Recomendação: rodar o AuthZ Server embutido no app para não subir novo processo.*

## FASE 18 — Módulo `cadastro`: enums de referência → cadastros com telas · `SPEC-0031` (nova)

Decisão do dono: **todo enum que não seja máquina de estado nem imutável vira cadastro** (dado de
referência editável). Novo módulo **`cadastro`** com registry genérico `cadastro_item(type,code,label,
active,…)` + **telas de CRUD** ("Cadastros" no shell, papel admin). Critério **agressivo**: mantém só
máquinas-de-estado (`*Status`/lifecycle), técnicos (`*FailureClass`, circuit-breaker) e fixados por lei
(`LegalType`, `LegalBasis`); converte o resto (`AdminExpenseKind/SupplierType/Recurrence`, `AssetType`,
`DocumentType`, `ConsentPurpose`, `InsightType`, `Verdict`, `GoalMetric`, `WithholdingKind`, `TaxRegime`,
`OfferOrigin`, `ChargeKind`, `CancellationType`, `EntryType`, `PayoutKind`, `SupportCaseType`, …). **Invariante:**
o valor persistido vira `code` validado com `code`=nome do enum ⇒ **JSON de contrato inalterado**; a lógica
que ramifica usa constantes de `code`; seeds preservam os valores. 4 fatias por grupo de domínio (18a–18d,
`0.29.0`–`0.32.0`); cada decisão limítrofe num DL. Entregáveis completos por fatia (backend+telas+E2E+docs).

---

## FASE 19 — Refactoring de maturidade (production readiness ampliado) · plano aprovado 2026-07-02

Decisão do dono após 7 relatórios de análise ponta a ponta (domínio×cliente, decision-log×mercado,
arquitetura, segurança, QA, CI/CD, observabilidade): o ERP é *production-shaped*, não
*production-ready*. A fase fecha essa distância em **12 fatias**, cada uma com spec/DL/ADR + gates
verdes + release MINOR: **19a** autorização default-deny por papel (matriz completa + completude
como portão) ✅ `0.33.0` · **19b** revisão do decision-log aplicada (quarentena de inbound
DL-0017-bis; anexo/Fator-R + flag de emissão DL-0044-bis) · **19c** segredos fail-fast + anti-replay
nos webhooks + upload/cofre hardening + lockout no form login + silent-refresh same-origin ·
**19d** documentação de API real (springdoc anotado, Authorize PKCE, snapshot de contrato + teste
de drift) · **19e** emulação das integrações (Portal de Experiências/Locação, GDS, NFS-e, gateway —
adaptadores HTTP com timeout/retry/breaker contra emuladores; ERP servindo câmbio como Open-Host) ·
**19f** migração de bibliotecas (Jackson 3 sem a ponte, Boot 4.1.x) · **19g** multi-instância/HA
(JWK persistido, AS/sessão em JDBC, fila DB-backed; revisa ADR-0002) · **19h** hedge cambial
(`ForwardContract`, cobertura hedged/unhedged, `HedgeAdvisor` no DSS) · **19i** QA hardening
(concorrência, PIT, jqwik, branch gates, timezone) · **19j** aderência à arquitetura & design de
código (auditoria doc×código + fitness functions novas + endpoint agregado de dashboard) ·
**19k** CI/CD supply-chain (CodeQL/Dependabot/imagem GHCR) + observabilidade (conserto do scrape,
alerting) · **19l** postura de produção (perfil prod, TLS/reverse proxy, backup/DR, checklist de
Open Questions ao dono).

---

## FASE 20 — Experiência do usuário corporativo + DSS real · plano do dono 2026-07-02

Decisão do dono: o público final é **corporativo, teclado-first**, e a inteligência do ERP (DSS)
precisa ser **real** (modelos/algoritmos de verdade, 2026), não fachada. Cada fatia = spec/DL +
gates verdes + release MINOR; UX é bilíngue no manual quando muda algo visível.

- **20a — Teclado-first (atalhos para tudo + busca de funcionalidade).** Todo módulo/ação acessível
  por atalho; **dicionário de atalhos** acessível de qualquer ponto (tecla `?`), já existe base
  (`ShortcutService`/`CommandRegistry`, DL-0093) — estender a **cobertura total**. **Busca geral de
  funcionalidade** (paleta `Ctrl/Cmd+K` graduada para navegar a qualquer tela/ação por nome).
  Formulários: navegação por `Tab` consistente, **autocomplete** nos campos de referência
  (cadastros/contas/marcas), foco inicial correto. Pesquisar padrões de mercado (command palette,
  a11y WAI-ARIA APG) antes de decidir.
- **20b — Proteção de fluxo + `Esc`/`Enter` universais.** Aviso de **dados não salvos** ao
  abandonar formulário/ação (gradua o `canDeactivate` existente para cobrir toda tela); **`Esc`
  fecha** (modal ou não, com confirmação se sujo); **`Enter` confirma** a ação primária. Padrão
  único aplicado a todas as telas.
- **20c — DSS real (modelos e algoritmos).** Substituir o DSS de fachada por **apoio à decisão
  determinístico/estatístico de verdade**, sempre **aceitável/rejeitável** pelo humano: começar
  pelos de maior retorno e base sólida — `OverrideNudge` (distância à próxima faixa de comissão,
  aritmética exata), previsão de demanda/receita (série temporal: média móvel/Holt-Winters ou
  regressão simples sobre os eventos), detecção de anomalia de margem/churn (limiares estatísticos,
  z-score), auto-preenchimento sugerido de cotação/parâmetros. **Pesquisar fontes confiáveis** de
  algoritmos (forecasting clássico, detecção de outliers) e escolher o mais simples que entrega
  valor (Regra Zero). Modelo probabilístico fica atrás de porta, com validação determinística antes
  de afetar estado (`messaging-and-integrations.md` §AI). Guardrail: aconselha, nunca comanda.
- **20d — Design moderno e amigável.** Refinar a UI (espaçamento, hierarquia, tipografia, densidade
  corporativa, estados, feedback) mantendo PrimeNG/Tailwind; acessível e responsivo. Pesquisar
  referências de ERPs/SaaS modernos antes de decidir tokens/paleta.

> O dono autorizou decidir sozinho nestas fatias **desde que haja pesquisa em fontes confiáveis
> antes de cada decisão**. Registrar cada escolha num DL, como nas fases anteriores.

---

## Perguntas em aberto que **travam** fatias (da Parte 13 do redesenho)

| # | Pergunta | Trava a fatia |
|---|---|---|
| 1 | Nome do câmbio (`Exchange`?) | Slice 2 — *assumido sim*; confirmar |
| 4 | Override do fornecedor: faixas retroativas ou fixo? | Slice 3/4 — *fixo no v1*, faixas adiadas |
| 5 | Comissão ao agente é escopada (agência/produto/canal)? | Slice 4 |
| — | **Fórmula exata de preço** (markup vs. tarifa; moeda da base) | Slice 4 (keystone) |
| 3 | Portal é *merchant of record* ou afiliado? | Slice 9 |
| 6 | Tipo de REP (C/A/P)? | Slice 11 |
| 7 | Quem emite a NF de comissão e em que regime? | Fase 8 (Billing) |
| 2 | `Portfolio` + `Assets`: os dois ou um? | Fase 8 |
| 8 | Operador edita só dados ou também regras em runtime? | transversal — manter em aberto |

> Regra de ouro (`CLAUDE.md`, invariante 3): o Claude Code **pergunta antes de implementar** o que
> depende dessas decisões. A resposta é registrada na seção *Open Questions* da spec dona — não
> "inventada para sumir".

---

## Recomendações para as Open Questions (sugestões do arquiteto)

> **Como ler isto.** Abaixo vai uma **recomendação de partida** para cada pergunta, com a
> justificativa. **Você decide** — estas são sugestões, não regras. As specs **continuam com a
> Open Question em aberto** até a sua confirmação; ao decidir, mova a resposta para *Business
> Rules* da spec dona (com data/quem decidiu), como manda o `TUTORIAL.md` §5. Nada aqui é "inventar
> regra para sumir" — é conselho explícito, que você aprova ou troca.

| # | Pergunta | Recomendação | Por quê (resumo) |
|---|---|---|---|
| **Preço** | Fórmula do Quoting (SPEC-0005) | **Base comissionável em BRL** (base convertida pela taxa congelada); **preço = base BRL + markup**, com `markup` governado **default 0**. A margem primária é o **spread**. | Venda à agência é em BRL (evita misturar moeda); bate com o exemplo (USD 500 → 2.700 → 405/270/135); markup como add-on opcional cobre tanto "GSA spread puro" (markup=0) quanto "tarifa + markup". |
| **Q1** | Nome `Exchange` (SPEC-0003) | **Manter `Exchange`.** | É o termo da linguagem ubíqua e é dono da taxa **e** da posição de risco. `Currency` é estreito; `Treasury` sugere caixa fora de escopo. |
| **Q2** | `Portfolio` + `Assets` (SPEC-0020/0021) | **Dois contextos separados.** | Perguntas diferentes (o que a Acme **representa** × **patrimônio** interno), linguagem/ciclo/dono distintos. Unir acoplaria comercial a TI. Assets (genérico) pode ser o último a entrar. |
| **Q3** | Merchant of record × afiliado (SPEC-0010) | **Atributo por marca/contrato** (`merchantOfRecord` em `RepresentationContract`); **default afiliado** (costBearer = fornecedor), **merchant=true** no Portal de Experiências (costBearer = Acme). | Arranjos variam por marca → é **dado**, não flag global. O caso do redesenho (portal cobrado mesmo reembolsando) é o merchant daquele acordo específico. |
| **Q4** | Override do fornecedor: fixo × faixas (SPEC-0004/0013) | **Fixo por marca no v1**, com `OverrideTier` desenhado como value object para plugar faixas depois sem refatorar; `OverrideNudge` atrás de flag até existir a tabela de faixas. | POC envia fixo; o exemplo do redesenho (>US$50k/ano → +3% retroativo) é valioso, mas precisa da tabela real do diretor para ligar o Nudge. |
| **Q5** | Escopo da comissão do agente (SPEC-0004/0014) | **Parâmetro governado por escopo** (agência > produto > global), reusando o motor de precedência da SPEC-0014; **default global**. | O redesenho diz que pode ter escopo por agência/produto; modelar como governed parameter evita hardcode e reaproveita o que já existe. |
| **Q6** | Tipo de REP (SPEC-0012) | Mirar **REP-P (software/nuvem)** para a exportação oficial do AFD/AEJ; ingestão da SPEC-0012 como "upload da exportação oficial" (serve também para REP-C via USB). | REP-P (Portaria 671) roda em nuvem, é certificado INPI e exporta AFD/AEJ oficialmente — melhor fit que USB. **Confirmar qual REP o cliente usa.** |
| **Q7** | Regime tributário / quem emite (SPEC-0016) | Assumir **Simples Nacional** inicialmente; cálculo de ISS/retenções **parametrizado por regime** (Simples/Presumido/Real) e município, atrás de estratégia trocável; emitente = a própria Acme. | Mais comum em PME; manter o cálculo parametrizado evita refação quando o contador confirmar o regime real. |
| **Q8** | Operador edita regras em runtime (SPEC-0014) | **Sim para parâmetros e diretivas** (self-service p/ diretor/admin, auditável); **não para fluxos** (máquinas de estado/integrações = spec + deploy). | O motor de parâmetros governados foi feito para o diretor "mexer nas regras no faro"; fluxos continuam governados por código. |

**Parâmetros governados — defaults recomendados** (todos confirmáveis; entram como `SYSTEM_DEFAULT` no seed da SPEC-0014):

- **Tolerância de discrepância de conciliação** (SPEC-0007): maior entre **R$ 1,00** e **0,5% do spread esperado**.
- **Limite de alerta de drift cambial** (SPEC-0011): **|drift| > 2%** da exposição estrangeira aberta do livro (ou um teto absoluto em BRL, conforme apetite de risco do diretor).
- **SLA do AfterSales** (SPEC-0018): **1ª resposta 24h, resolução 72h** (cancelamento/reembolso **48h**).

> Sugestão de uso: leve esta tabela à reunião com o diretor/contador, marque **OK** ou **trocar**
> em cada linha, e só então abra a fatia dona pelo laço do `TUTORIAL.md`.

---

## Como um card vira código

Cada card acima vira uma sessão de Claude Code seguindo o ciclo do `docs/TUTORIAL.md`:
resolver perguntas em aberto com você → (plan mode) → **teste vermelho** → esqueleto da fatia →
**verde** → refatora → portões (ArchUnit/Modulith/Spotless) → Definition of Done → commit.
Specs são artefatos vivos: se a regra muda durante a fatia, atualiza-se a spec no mesmo PR.

---

## Índice completo de Specs (0001–0025)

Todos os 22 contextos do redesenho viram entregável. Núcleo/Apoio = spec rica; Genéricos
(Finance/Identity/Admin/Assets) = fronteira + seam + decisão **comprar vs. construir**. Onde o negócio
ainda não decidiu, a spec **marca Open Question** — não inventa regra.

| Spec | Módulo / contexto | Tipo | Fase | Entregável (1 por spec) |
|---|---|---|---|---|
| 0001 | Platform (esqueleto) | infra | 0 | Monólito modular que anda + portões verdes + Event Storming |
| 0002 | Accounts | Supporting | 1 | Cadastro de conta comercial (CNPJ/MEI/CPF) com validação/unicidade |
| 0003 | Exchange (congelada) | Core | 1 | Taxa única de venda (Open-Host) + histórico |
| 0004 | Commissioning | Core | 1 | Comissão de duas pontas + spread (cálculo puro) |
| 0005 | Quoting | Supporting | 1 | Composição + sugestão + override com rastro (**keystone**) |
| 0006 | Booking | Supporting | 1 | Ciclo de vida + localizador + accrual na confirmação |
| 0007 | Reconciliation | Core | 1 | Caso por venda: a pagar × a receber × comissão × ganho/perda cambial |
| 0008 | Compliance | Supporting | 2 | Cofre + anexo obrigatório + retenção + veto de fechamento |
| 0009 | Sourcing + Integration | Supporting | 3 | 1ª ACL real: ramo INTEGRATED (preço fechado) |
| 0010 | Cancellation/merchant | Supporting | 4 | Política como objeto + armadilha ALL_SALES_FINAL + no-show |
| 0011 | Exchange (exposição) | Core | 5 | Subsídio × drift + posição do livro + relatórios de câmbio |
| 0012 | Crawler de ponto | Supporting | 6 | Snapshot operacional p/ People + AFD/AEJ legal p/ Compliance |
| 0013 | Intelligence (DSS) | Core estrat. | 7 | Insight que aconselha (PromoFxAdvisor; OverrideNudge gated) |
| 0014 | CommercialPolicy | Supporting | 8 | Parâmetros governados + precedência (gradua o markup da 0005) |
| 0015 | Finance | Generic | 2* | AP/AR + período/fechamento (**co-entrega com 0008**) |
| 0016 | Billing | Supporting | 8 | NFS-e sobre a comissão + ISS/retenções |
| 0017 | Payout | Supporting | 8 | Repasse/liquidação/reembolso + parcelamento + comprovante |
| 0018 | AfterSales | Supporting | 8 | Chamados/alteração/reembolso/SLA + custo de servir |
| 0019 | Marketing | Supporting | 8 | Campanha/segmentação/newsletter + consentimento LGPD |
| 0020 | Portfolio | Supporting | 8 | Marcas representadas + contratos + metas |
| 0021 | Assets | Supp./Gen. | 8 | Patrimônio interno (equip./licenças) |
| 0022 | People | Generic | 8 | Colaboradores + jornada (consome snapshot) + banco de horas |
| 0023 | Platform (contexto) | Supporting | 8 | Custódia e-CNPJ + governança de jobs + auditoria de sistema |
| 0024 | Identity | Generic | 8 | Auth real (OIDC) + papéis/permissões + auditoria de acesso |
| 0025 | Admin | Generic | 8 | Fornecedores/contratos administrativos → Finance + Compliance |

`*` 0015 é "Fase 8" como módulo genérico, mas seu **seam mínimo de AP/AR + período** é pré-requisito do
veto do Compliance e, na prática, **co-entrega na Fatia 2**.

> A ordem de implementação continua sendo **uma fatia por vez** pelo ciclo do `TUTORIAL.md`. Ter as
> specs escritas **não** significa criar 22 módulos vazios de uma vez — significa que o destino está
> desenhado; o código nasce fatia a fatia, e cada spec é atualizada no PR da sua fatia.

---

