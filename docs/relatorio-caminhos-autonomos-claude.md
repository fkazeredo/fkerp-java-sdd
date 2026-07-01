# Relatório — Caminhos escolhidos pelo Claude em modo autônomo

**Data:** 2026-07-01  
**Base lida:** `docs/decision-log/` até `DL-0109`, com apoio de `docs/RUN-PHASE.md` e `docs/ROADMAP-STATUS.md`.

## Resumo executivo

Ao ganhar autonomia, o Claude seguiu um caminho bastante conservador: decidiu pelo menor modelo útil que preservasse fronteiras, testes, rastreabilidade e possibilidade de troca futura. A linha mestra foi construir um ERP modular funcional, com domínio protegido, integrações atrás de portas, eventos in-process, idempotência e gates fortes, evitando transformar o projeto em um ERP contábil/CRM/procurement completo antes de existir decisão de negócio.

O padrão mais recorrente foi: quando o `ROADMAP` recomendava algo, ele adotou; quando faltava decisão do dono, escolheu o valor mais defensável e marcou `Confiança=Baixa`; quando a reversão afetaria dado real, imposto, LGPD, certificado, câmbio ou arquitetura de identidade, marcou `Reversibilidade=Cara`.

O resultado foi um produto backend-heavy, bem testado e com muitas capacidades de domínio, mas com uma dívida importante revelada depois: várias fases entregaram API e regra de negócio sem tela operacional. Essa lacuna foi registrada em `DL-0109` e transformada na Fase 16 de telas de operação.

## Estratégia geral adotada

| Caminho | Decisão consolidada |
|---|---|
| Arquitetura | Modular monolith com Spring Modulith, módulos explícitos, fronteiras verificadas por ArchUnit e Spring Modulith. |
| Entrega | Fatias pequenas, cada uma com testes, migração, release note e decisão registrada antes do código dependente. |
| Domínio | Domínio sem dependência de infraestrutura; DTO externo fica em ACL; módulos conversam por fachada pública, eventos ou ids por valor. |
| Integrações | Portas hexagonais + mocks rastreáveis; provedor real fica para adapter futuro. |
| Financeiro | Construir livro-caixa/AP/AR suficiente para o ERP, não um GL contábil pleno. Contabilidade oficial = comprar/integrar. |
| Governança | Preferir alerta auditável a bloqueio quando o bloqueio depender de decisão operacional ainda não fechada. |
| Segurança | Começar com auth real in-house, depois graduar para OIDC/Keycloak em dev/E2E, mantendo papéis/permissões no ERP. |
| Qualidade | Gates desde cedo: Modulith, ArchUnit, Checkstyle/Spotless, JaCoCo, Vitest e Playwright. |
| UX | Modernizar shell/telas existentes na Fase 10; depois reconhecer que isso não cobriu todos os módulos e planejar a Fase 16. |

## Caminhos por área

### 1. Fundação técnica e modularidade

O Claude manteve o pacote base `com.fksoft` (`DL-0001`), mesmo sabendo que renomear depois seria caro. A justificativa foi manter coerência com a documentação e evitar fixar uma marca fictícia no pacote Java.

Na stack, começou com Java 21, Spring Boot 3.5, Spring Modulith 1.4 e Maven Wrapper (`DL-0002`, `DL-0004`). Depois, na Fase 14, decidiu subir para Spring Boot 4.0.7 e Modulith 2.0.7 (`DL-0108`), mas mantendo Jackson 2 em produção via `spring-boot-starter-classic` para não arriscar contrato JSON. Jackson 3 ficou como dívida rastreada.

Para modularidade, escolheu `detection-strategy=explicitly-annotated` no Spring Modulith (`DL-0006`). Mais tarde, quando removeu pacotes `internal`, preservou encapsulamento com `@ModuleInternal` + regra ArchUnit (`DL-0089`). Ou seja: achatou a estrutura física, mas não abriu as fronteiras.

### 2. Núcleo comercial manual

No cadastro de contas, assumiu que CADASTUR/IATA seriam opcionais no v1 (`DL-0007`). Para câmbio, manteve o nome do módulo como `Exchange` (`DL-0008`).

Na precificação, adotou a fórmula `preço = base BRL + markup`, com `markup` default zero e margem primária via spread (`DL-0009`). Essa é uma decisão econômica relevante e cara de trocar, porque muda como a rentabilidade é entendida.

Em Booking, recusou a hipótese de `Quote → Booking` 1:1; o travamento real ficou no localizador único (`DL-0010`). Na conciliação, escolheu tolerância de discrepância como `max(R$ 1,00; 0,5% do spread esperado)` (`DL-0011`).

### 3. Compliance e financeiro mínimo

O Compliance ganhou catálogo `entryType × DocumentRequirement`, cofre de documentos via porta `FileStorage`, hash SHA-256 e retenção (`DL-0012`, `DL-0015`).

O Finance foi deliberadamente limitado: AP/AR, período, fechamento e razão em moeda original, sem conversão automática e sem contabilidade plena (`DL-0013`, `DL-0014`). Essa decisão foi reafirmada quando o Finance ficou “full”: livro-caixa operacional, não plano de contas, partidas dobradas, DRE, SPED ou Razão oficial (`DL-0042`).

### 4. Integração de entrada e quoting integrado

Para webhook inbound, decidiu por HMAC-SHA256 em `X-Signature`, comparação em tempo constante e contrato externo isolado na ACL (`DL-0016`).

Quando uma cotação integrada chega com Account inexistente, o Claude escolheu rejeitar com 422, sem criar conta provisória nem fila de pendência (`DL-0017`). Essa ficou com confiança baixa porque é decisão de negócio.

Para quote integrado, decidiu reutilizar o agregado `Quote`; preço integrado é confiável, não roda motor de sugestão, e campos de composição manual podem ser nulos (`DL-0018`). A resiliência da ACL inbound ficou limitada a classificação de falha, idempotência e observabilidade, sem circuit breaker por não haver chamada externa de saída (`DL-0019`).

### 5. Cancelamento e “armadilha do merchant”

Claude manteve cancelamento dentro do módulo `booking`, sem criar módulo novo (`DL-0020`). Modelou `merchantOfRecord` como atributo de política por marca/contrato, com default afiliado (`DL-0021`).

A decisão central foi: encargos são fatos distintos e nunca se compensam (`DL-0024`). Multa, cobrança de fornecedor, reembolso ao cliente e no-show viram obrigações separadas. Essa é uma tese econômica forte e cara de reverter.

No-show recebeu prova rastreável de voo cancelado, mas a conformidade do documento ficou no Compliance (`DL-0023`). Multas e encargos preservam a moeda original (`DL-0022`).

### 6. Câmbio, exposição e relatórios

Para taxa de mercado, escolheu uma porta `MarketRateProvider`, com registro manual de contingência no v1 e feed externo futuro como adapter (`DL-0025`).

O congelamento de taxa ficou global por par de moeda (`DL-0026`), o alerta de drift default ficou em 2% da exposição aberta (`DL-0027`), e a abertura/fechamento de `FxPosition` foi costurada via Reconciliation para evitar ciclo Modulith (`DL-0028`).

Esse caminho mostra um padrão forte: quando o grafo modular acusou ciclo, Claude mudou a direção da dependência em vez de afrouxar o gate.

### 7. Ponto, People e jornada

Na questão do REP, escolheu mirar REP-P e modelar AFD/AEJ como upload da exportação oficial assinada (`DL-0029`). Essa é uma das decisões mais sensíveis: confiança baixa e reversibilidade cara, porque o tipo real de REP depende do cliente.

Criou o módulo `people` como dono do snapshot operacional, deixando o crawler técnico em `infra.integration` (`DL-0030`). O crawler usa disjuntor, retry e dead-letter in-process, sem broker e sem resilience4j (`DL-0031`). A ingestão do AFD valida envelope/assinatura estrutural e preserva o `.p7s` original no cofre (`DL-0032`).

Mais tarde, People passou a calcular jornada e banco de horas sobre o snapshot operacional, sem reescrever o crawler (`DL-0069`). O banco de horas ficou mensal, em minutos, com janela CLT configurável default de 6 meses (`DL-0070`). Divergências alertam e vão para fila humana; nunca corrigem automaticamente (`DL-0071`). Holerites vão para Compliance como PAYROLL, por valor (`DL-0072`).

### 8. Intelligence/DSS

Claude escolheu inteligência determinística, não LLM vivo (`DL-0035`, `DL-0036`). O `PromoFxAdvisor` usa eventos existentes e calcula sinais por agência; `OverrideNudge` ficou atrás de feature flag até haver faixas de decisão.

Também decidiu que Intelligence é consumidor-folha: escuta eventos, mantém projeções e aconselha; não comanda fluxo de negócio nem consulta repositórios internos de outros módulos (`DL-0034`). A porta para narrativa/LLM foi desenhada, mas o default é rule-based e testável.

### 9. CommercialPolicy como motor de parâmetros

Na Fase 8a, Claude graduou o stub de política comercial para um motor real de precedência (`DL-0037`): Diretiva > Promoção > Contrato > Política > Padrão, com especificidade por escopo e desempate determinístico.

Permitiu self-service em runtime para parâmetros e diretivas, mas não para fluxos (`DL-0038`). Seeds foram limitados às chaves já usadas, evitando inventar comportamento (`DL-0039`). O `MarkupProvider` foi graduado mantendo o contrato do Quoting (`DL-0040`).

Esse caminho virou base para SLA de AfterSales, markup e futuros parâmetros governados.

### 10. Billing, impostos e NFS-e

Em Billing, escolheu regime default Simples Nacional, emitente Acme e cálculo atrás de `TaxRegimeStrategy` trocável (`DL-0044`). Essa decisão é crítica e deve ser confirmada por contador, porque regime tributário real não é técnico.

Criou módulo `billing` e modelou `CommissionInvoice`; a base tributável é a comissão, nunca o pacote bruto (`DL-0045`). NFS-e municipal ficou como porta `NfseGateway` com mock rastreável e assinatura via `CertificateSigner` (`DL-0046`).

A emissão é idempotente, arquiva a nota no Compliance e publica evento para o Finance lançar tributo, mantendo Billing como módulo folha (`DL-0047`).

### 11. Payout, reembolso e liquidação

Payout foi modelado com `PaymentGateway` e mock assíncrono por webhook, seguindo ADR 0006 (`DL-0048`). O meio de pagamento real segue em aberto.

Liquidação estrangeira guarda moeda original, `settlementRate` e baixa em BRL (`DL-0049`). Essa é outra decisão cara, porque o fluxo real de remessa/câmbio ainda depende de banco, operação e regra do cliente.

Parcelamento v1 ficou sem juros, com distribuição exata de centavos (`DL-0050`). `SupplierSettled` é consumido pelo Finance uma vez, e reembolso não cancela obrigação de fornecedor (`DL-0051`), preservando a armadilha do merchant.

### 12. AfterSales

SLA foi governado por CommercialPolicy: 24h primeira resposta, 72h resolução e 48h para cancelamento/reembolso (`DL-0052`).

Breach de SLA é job com relógio controlado e alerta idempotente, não bloqueio (`DL-0053`). AfterSales orquestra cancelamento em Booking e reembolso em Payout via fachadas, mantendo idempotência e grafo acíclico (`DL-0054`).

### 13. Marketing, LGPD e atribuição

Marketing ficou como camada de consentimento, segmentação simples, campanha/newsletter e atribuição; não CRM pleno (`DL-0059`).

Consentimento é append-only, com estado atual derivado da última linha (`DL-0056`). Newsletter usa porta `NewsletterSender` e mock rastreável; v1 é single opt-in, mas o modelo comporta double opt-in no futuro (`DL-0055`).

Atribuição usa intake próprio `campaignCode → booking`, sem alterar `BookingConfirmed` (`DL-0057`). No apagamento LGPD, Claude escolheu remover PII de marketing, mas preservar tombstone anonimizado de revogação e métricas sem PII (`DL-0058`). Essa é decisão de confiança baixa e reversibilidade cara; precisa de DPO/jurídico antes de uso real.

### 14. Portfolio, Assets e Admin como registros enxutos

Portfolio foi separado de Assets (`DL-0060`). Venda sem contrato vigente alerta, não bloqueia (`DL-0061`). Realizado por marca usa intake próprio `booking → brandRef`, sem mudar evento de venda (`DL-0062`). Expiração de representação é job de relógio controlado, alerta e idempotente (`DL-0063`).

Assets também ficou separado, como registro enxuto de patrimônio (`DL-0064`). Não implementou depreciação nem gestão plena; se necessário, comprar/integrar sistema dedicado (`DL-0065`). Licenças a vencer alertam em 30 dias (`DL-0066`), eventos são publicados sem consumidores forçados (`DL-0067`), e baixa é terminal/auditada inline (`DL-0068`).

Admin foi módulo próprio enxuto para fornecedores, contratos e despesas administrativas (`DL-0084`). Procurement completo ficou como comprar/integrar. Despesas mapeiam `kind → EntryType → DocumentRequirement` (`DL-0085`), integram Finance/Compliance por fachadas (`DL-0086`), contratos a vencer alertam (`DL-0087`) e escritas exigem `ROLE_FINANCE` + auditoria (`DL-0088`).

### 15. Platform

Platform virou módulo real para custódia, jobs e auditoria (`DL-0073`). A custódia do e-CNPJ usa AES-256-GCM at-rest, chave mestra fora do banco, só metadados em claro e porta trocável para KMS/HSM/secret manager futuro (`DL-0074`). Essa é uma das decisões mais sensíveis.

Jobs ganharam catálogo, `JobRun`, idempotência por janela e advisory lock no Postgres, sem Quartz/ShedLock (`DL-0075`, `DL-0076`). Auditoria de sistema é append-only e consolidada por listener/fachada (`DL-0077`). O `CertificateSigner` foi graduado para o Platform, mantendo compatibilidade com Billing (`DL-0078`).

### 16. Identity e segurança

No 8k, Claude decidiu entregar auth real in-house com Spring Security + JWT HS256 e store local mínimo de usuários (`DL-0079`, `DL-0080`). Foi uma decisão marcada como baixa confiança/cara porque o IdP real era uma pergunta do dono.

Preservou a porta `UserContextProvider`, mantendo testes verdes com security montada (`DL-0081`). Papéis/permissões e ações sensíveis ficaram no ERP (`DL-0082`), e auditoria de acesso reusou o `system_audit` do Platform (`DL-0083`).

Na Fase 13, essa escolha foi graduada: Keycloak virou IdP de dev/E2E (`DL-0103`), backend passou a validar JWT via JWKS/RS256 (`DL-0104`), testes usam JWKS local (`DL-0105`), frontend passou a OIDC Authorization Code + PKCE com silent-refresh real (`DL-0106`), e o store local de usuários foi aposentado, mantendo o catálogo local de papéis/permissões (`DL-0107`).

### 17. Frontend, observabilidade e qualidade

Na Fase 10, Claude escolheu PrimeNG 21 + Aura, Tailwind v4, CDK e primeicons (`DL-0090`), tema claro/escuro com `.app-dark` e tokens (`DL-0091`), paleta `Ctrl/Cmd+K` própria (`DL-0093`) e dashboard com KPIs client-side sobre endpoints existentes (`DL-0094`).

Antes da Fase 13, “silent refresh” significava revalidação por `/me`, sem refresh token (`DL-0092`). Depois foi resolvido com OIDC e refresh token real (`DL-0106`).

Observabilidade foi feita com Actuator mínimo e protegido por `ROLE_IT` (`DL-0095`), logs JSON nativos do Spring Boot com correlation id (`DL-0096`), endpoint `/api/version` por build-info/git (`DL-0097`) e métricas de negócio em `infra` sobre eventos já publicados, sem Micrometer no domínio (`DL-0098`).

Qualidade recebeu gates de cobertura: JaCoCo backend ≥ 80% (`DL-0099`), Vitest frontend com thresholds (`DL-0100`), E2E isolado em stack efêmera (`DL-0101`) e Playwright para jornadas críticas/caminhos tristes no CI (`DL-0102`).

## Decisões que mais merecem revisão humana

| DL | Tema | Por que revisar |
|---|---|---|
| `DL-0009` | Fórmula de preço | Define a tese econômica: preço como base BRL + markup, margem primária via spread. |
| `DL-0017` | Account inexistente no inbound | Rejeitar com 422 pode ser certo tecnicamente, mas é fluxo comercial. |
| `DL-0024` | Encargos nunca se compensam | É a tese da armadilha do merchant; mudar exige nova modelagem. |
| `DL-0029` | Tipo de REP | REP-P/upload oficial foi assumido, mas depende do fornecedor real de ponto. |
| `DL-0033` | Periodicidade/fontes do crawler | Diário + período corrente + `sourceRef` configurável pode precisar ajuste operacional. |
| `DL-0044` | Regime tributário | Simples Nacional/default e ISS/retenções precisam de contador. |
| `DL-0048` | Gateway de pagamento | Meio de pagamento real continua aberto; mock prova contrato, não operação real. |
| `DL-0049` | Liquidação estrangeira | Remessa, banco, IOF e baixa em BRL dependem da operação real. |
| `DL-0058` | Apagamento LGPD | Escopo do apagamento versus prova de revogação deve ser validado com DPO/jurídico. |
| `DL-0062` | Venda por marca | Fonte real do vínculo booking→marca ainda é decisão de negócio. |
| `DL-0070` | Banco de horas | Política trabalhista e acordo coletivo precisam de RH/jurídico. |
| `DL-0074` | Custódia e-CNPJ | KMS/HSM/secret manager e A1/A3 são decisão de segurança/infra. |
| `DL-0103` | IdP de produção | Keycloak foi escolhido para dev/E2E; produção ainda depende do dono. |

## Dívidas e escolhas intencionais

O maior débito descoberto foi de UI: backend com muitos módulos e controllers, mas frontend cobrindo poucas telas. O Claude registrou isso em `DL-0109` como falha de processo da autonomia: as fases backend-first jogavam “tela depois” para a Fase 10, mas a Fase 10 repaginou as telas existentes em vez de criar todas as telas faltantes. A correção foi planejar a Fase 16 em quatro fatias frontend-only.

Outras dívidas foram assumidas conscientemente:

- Contabilidade plena, GL, SPED, DRE e plano de contas: comprar/integrar, não construir.
- CRM pleno, procurement completo e gestão de ativos plena: comprar/integrar se o dono exigir.
- Integrações externas reais de NFS-e, pagamento, newsletter, ponto e câmbio: adapters futuros; v1 usa mocks rastreáveis.
- IdP de produção: ainda decisão do dono, embora o contrato OIDC/JWKS esteja pronto.
- Jackson 3: adiado após upgrade para Spring Boot 4, com ponte Jackson 2 mantida em produção.

## Leitura final

O Claude não escolheu “construir tudo”. Ele escolheu construir um núcleo de ERP auditável, testável e modular, com costuras preparadas para trocar mocks por provedores reais e para comprar sistemas satélites quando o escopo ultrapassar o ERP operacional.

A autonomia foi boa em preservar arquitetura, testes e rastreabilidade. O ponto fraco foi de produto: o loop aceitou “API + backend verde” como pronto por tempo demais, e a usabilidade real só apareceu quando o dono testou a interface. O próprio `DL-0109` corrige o processo: daqui para frente, capacidade de negócio precisa ter tela operacional ou um adiamento explícito com fase-alvo real.
