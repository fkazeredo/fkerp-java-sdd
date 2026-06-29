# ERP Acme Travel — Redesenho de Domínio (versão consolidada e didática)

> Documento único e canônico. Substitui versões anteriores. Escrito para três públicos: quem conhece o negócio da Acme Travel, quem é de TI mas nunca usou Domain-Driven Design, e quem não é técnico. Termos técnicos têm um quadro **“Em miúdos”**; conceitos de dinheiro têm exemplo numérico.
>
> Convenção: texto e telas em português; nomes de código em inglês.
>
> **Nomes fictícios:** «Acme Travel» e os nomes de portais/fornecedores («Portal de Experiências», «Portal de Locação», «Locadora Internacional», «Marketplace de Tours», «Rede Hoteleira») são placeholders anônimos. Nenhum é empresa real; troque pelo que preferir.
>
> **Decisões assumidas (a confirmar — Parte 13):** câmbio = `Exchange`; inventário desdobrado em `Portfolio` (representação comercial) + `Assets` (patrimônio interno); ponto eletrônico = ponto físico de funcionários (RH) lido por web crawling.

---

## Parte 0 — Como ler este documento

Descreve **como o software deve enxergar o negócio da Acme Travel** antes do código. A causa nº 1 de fracasso em software não é código ruim — é ter entendido o negócio errado. A Parte 2 ensina o vocabulário; as demais o usam. Negócio: foque nos quadros “Em miúdos” e exemplos. TI: os exemplos numéricos justificam cada decisão.

---

## Parte 1 — O essencial em uma página

**O que a Acme Travel é.** Representante comercial (GSA — *General Sales Agent*) de marcas estrangeiras de turismo na América Latina, com portais próprios para agentes (Portal de Experiências de experiências; Portal de Locação de aluguel de carro). O cliente direto é a **agência/agente**, não o viajante.

**Como ganha dinheiro.** Comissão de **duas pontas**: recebe do fornecedor, paga ao agente, vive da **diferença (spread)**.

**O que o ERP é — e não é.** Não é dono dos produtos nem dos preços (moram nos portais, em sites de terceiros, em catálogos físicos). É dono da **verdade comercial e financeira** aplicada em cima: câmbio, comissão, margem, conciliação, e o **rastro documental/fiscal**.

**As regras que governam tudo.**
- **Fronteira do produto: aberta.** Produto e preço-base vêm de qualquer lugar, inclusive digitados de um catálogo físico.
- **Fronteira do dinheiro: governada, não travada.** O sistema sempre calcula e **sugere** o número; o humano pode divergir, mas a divergência fica registrada contra a sugestão.
- **Fronteira do compliance: obrigatória.** Lançamento financeiro sem documento comprobatório não fecha o mês. Documento é cidadão de primeira classe, com prazo de guarda.
- **Manual e integrado geram os mesmos eventos.** A diferença é atributo, não fluxo separado.

**Não é um ERP burro.** Há um contexto estratégico de inteligência (DSS) cujo trabalho é **potencializar lucro** — apoio à decisão amarrado às alavancas reais da Acme Travel. Aconselha; nunca comanda.

---

## Parte 2 — Vocabulário técnico, explicado para leigos

DDD (Domain-Driven Design): projetar software de modo que o código fale a língua do negócio.

**Domínio / subdomínio.** O problema inteiro / suas fatias. Tipos: **Core** (a vantagem competitiva, feito à mão), **Supporting** (necessário, não diferencial), **Generic** (commodity — compra-se).
> **Em miúdos:** num restaurante, *core* é a cozinha; *supporting* é a reserva de mesas; *generic* é a folha de pagamento.

**Bounded Context.** Fronteira onde uma palavra tem um significado só. “Reserva” para Operações é serviço a entregar; para o Financeiro é obrigação a liquidar.
> **Em miúdos:** os cômodos da casa — “quente” é o fogão na cozinha e o chuveiro no banheiro.

**Linguagem ubíqua.** As mesmas palavras no negócio e no código.

**Entidade / Value Object / Aggregate.** Entidade tem identidade que dura (Reserva 123). Value Object é definido só pelos valores (“USD 320”). Aggregate é um bloco consistente com uma porta de entrada.
> **Em miúdos:** uma nota de R$10 (value object) vs. seu contrato de aluguel (entidade); um pedido com itens, acessado pela porta do Pedido (aggregate).

**Evento de domínio.** Um fato passado (`BookingConfirmed`) que outros escutam.
> **Em miúdos:** aviso no alto-falante do negócio.

**ACL (Anti-Corruption Layer).** Tradutor na fronteira com sistema externo.
> **Em miúdos:** a alfândega.

**Open-Host Service.** Um “cardápio” público único que todos consomem (ex.: a taxa de câmbio).

**Arquitetura hexagonal.** Negócio no centro; externo plugado por tomadas padronizadas.

**Read-model.** Cópia otimizada para leitura/relatório, separada da que grava.

**Policy como objeto.** Regra virada dado, que se lê e muda (ex.: política de cancelamento).

**Strangler fig.** Substituir o sistema antigo aos poucos.

**Proveniência.** O rastro de origem de um número.

**DSS.** Apoio à decisão: aponta o número/oportunidade; não decide por você.

**Documento hábil/idôneo.** O comprovante legal que lastreia um lançamento contábil (NF, recibo, contrato, comprovante). Conceito da NBC ITG 2000.
> **Em miúdos:** o “recibo que prova” que a operação existiu, sem o qual a contabilidade não pode (e não deve) registrar.

---

## Parte 3 — O negócio da Acme Travel, destrinchado

### 3.1 Representação (GSA)
A Acme Travel é o braço de vendas de marcas estrangeiras, com portais próprios (Portal de Experiências, Portal de Locação). Cadeia: `Fornecedor → Acme Travel → Agência/Agente → Viajante`. Cliente direto = agência (raramente CPF). Por isso o modelo é “**Conta Comercial** cota/reserva produto”.

### 3.2 Comissão de duas pontas — com números
```
Tarifa do fornecedor (base):           USD 500
Comissão do fornecedor à Acme Travel (override): 15% = USD 75   (ponta de cima)
Comissão da Acme Travel à agência:               10% = USD 50   (ponta de baixo)
Margem da Acme Travel (spread):                  USD 25
```
Sutilezas: **base comissionável** muda por fornecedor (Locadora Internacional exclui extras); **cancelamento estorna as duas pontas**; **imposto incide só sobre a comissão** (separar “dinheiro que passa” de “receita”).

### 3.3 Realidade híbrida
Quatro mundos convivem: portais integrados (Portal de Experiências/Portal de Locação); sites externos sem integração; sistemas de terceiros/catálogos físicos (o ERP nunca terá estruturado); demanda crua (cotação, WhatsApp, telefone). **Manual é fluxo de primeira classe, não exceção.**

### 3.4 O diretor top-down
Mexe nas regras no faro. Caso marcante: **congela o câmbio** (taxa única de venda) e usa como **promoção** para mover volume. O sistema absorve a decisão com rastro e dá ferramentas de decisão; não briga com o estilo.

---

## Parte 4 — A tese de arquitetura

**1. O ERP é dono da verdade comercial, não do catálogo/preço.** Produto e preço-base nascem fora; câmbio, comissão, reserva (compromisso), conciliação e o rastro fiscal vivem dentro. O ERP **compõe a cotação** a partir de um preço-base externo; não precifica o produto. (Por isso não há contexto “Pricing” dono de preço; a composição é capacidade do `Quoting`, alimentada por `Exchange` + `Commissioning` + `CommercialPolicy`.)

**2. Fronteira do produto aberta; do dinheiro governada.** O humano informa produto e preço-base livremente (até texto livre). Sobre isso o sistema calcula e **sugere**, mas não trava: o humano pode divergir, com `OverrideRecord` (quem, quando, de→para, motivo).

**3. Sugestão só no manual (escopo do POC).** Integração assume preço pronto/fechado (`priceOrigin = INTEGRATED`); o motor de sugestão roda só no `MANUAL`. O gancho para recompor o integrado depois fica adormecido, sem dívida.

**4. Fronteira do compliance: obrigatória.** Todo lançamento em Contas a Pagar/Receber precisa de documento comprobatório anexado; sem ele, não passa no fechamento mensal. Documentos têm prazo legal de guarda (Parte 7.7). Isto é exigência legal, não preferência.

**5. Manual e integrado geram os mesmos eventos.** “Origem” e “nível de integração” são atributos. Evita o “ERP de duas caras”.

---

## Parte 5 — Mapa de subdomínios

| Contexto | Tipo | Papel |
|---|---|---|
| **Exchange** | Core | câmbio centralizado, congelamento, exposição, posição do livro |
| **Commissioning** | Core | comissão de duas pontas, faixas de override, spread |
| **Reconciliation** | Core | casa as pontas, ganho/perda cambial |
| **Intelligence** (DSS) | Core estratégico | descritivo + preditivo + prescritivo; potencializa lucro |
| **CommercialPolicy** | Supporting | markup, promoção, diretivas, parâmetros governados (não preços) |
| **Quoting** | Supporting | compõe a cotação a partir do preço-base externo + regras; sugere (manual) |
| **Booking** | Supporting | reserva, ciclo de vida, política de cancelamento |
| **Sourcing** | Supporting | de onde vem a oferta + nível de integração |
| **Accounts** | Supporting | agência/agente CNPJ/MEI/CPF, carteira, cadastros legais |
| **Compliance** | Supporting | cofre de documentos, anexo obrigatório, retenção legal, trilha |
| **Integration** | Supporting | tradutores (ACL) dos sistemas externos, inclusive o crawler de ponto |
| **Platform** (TI) | Supporting | saúde, credenciais, jobs/crawler, observabilidade, auditoria |
| **Billing** | Supporting | nota fiscal sobre a comissão, impostos, retenções |
| **Payout** | Supporting | repasse ao agente, liquidação ao fornecedor |
| **AfterSales** | Supporting | chamados, alteração, cancelamento, reembolso |
| **Marketing** | Supporting | campanha, segmentação, newsletter, LGPD |
| **Portfolio** | Supporting | as marcas/produtos que a Acme Travel representa |
| **Assets** | Supporting/Generic | inventário/patrimônio interno |
| **Finance** | Generic | razão contábil, fluxo de caixa, contas a pagar/receber |
| **Identity** | Generic | login, papéis, permissões, auditoria de acesso |
| **People** | Generic | colaboradores, jornada, ponto (físico, via crawling) |
| **Admin** | Generic | contratos, fornecedores administrativos |

> `Compliance` é novo e cross-cutting: muitos contextos anexam documentos nele, mas a regra de retenção/obrigatoriedade vive num lugar só. `Finance` mantém os razões de AP/AR, mas a regra de anexo obrigatório é imposta por `Compliance`.

---

## Parte 6 — Os contextos, um a um (resumo)

**Accounts** — quem é o parceiro (CNPJ/MEI/CPF), carteira, CADASTUR/IATA. Não calcula dinheiro.
**Sourcing** — de onde vem a oferta + nível de integração; texto livre é oferta válida.
**Quoting** — compõe a cotação (preço-base + Exchange + Commissioning + CommercialPolicy); sugere no manual, registra fechado no integrado.
**Exchange** — dono da taxa e da posição de risco; Open-Host Service.
**Commissioning** — direito à comissão nas duas pontas; base comissionável; spread.
**Reconciliation** — casa a pagar × a receber × comissão esperada × recebida × ganho/perda cambial.
**Payout** — paga o agente, liquida o fornecedor; parcelamento.
**Billing** — NF de serviço sobre a comissão; ISS, retenções.
**CommercialPolicy** — regras e parâmetros governados (markup, promoção, diretivas), não preços.
**Booking** — reserva, ciclo de vida, política de cancelamento, localizador (inclusive externo).
**AfterSales** — chamados, alteração, cancelamento, reembolso, SLA.
**Integration** — tradutores/ACL dos externos; abriga o conector de crawling do ponto.
**Platform** — saúde, credenciais, jobs (orquestra o crawler), observabilidade, auditoria de sistema.
**Marketing** — campanha, segmentação, newsletter externa, consentimento LGPD.
**Intelligence** — o DSS (Parte 8). Só lê eventos; aconselha.
**Portfolio** — o que a Acme Travel representa (marcas, contratos de representação, metas por marca).
**Assets** — patrimônio interno (equipamentos, licenças).
**Compliance** — o cofre de documentos e as regras de retenção/anexo (Parte 7.7).
**People** — colaboradores, jornada, ponto físico lido por crawling (Parte 7.8).
**Finance / Identity / Admin** — genéricos; avaliar comprar.

---

## Parte 7 — Conceitos de domínio (com exemplos)

### 7.1 Comissão de duas pontas
```
CommissionStatement: SupplierCommission(a receber) + AgentCommission(a pagar) + Spread(derivado)
OverrideTier — escala por volume (ex.: > US$ 50k/ano → +3% retroativo)
Eventos: ExpectedCommissionAccrued, CommissionReversed, OverrideTierReached, SpreadRealized
```

### 7.2 Câmbio: exposição, congelamento e promoção — com números
A Acme Travel vende em reais hoje e paga o fornecedor em dólar depois → **exposição** (lucro/risco pelo tempo). O diretor congela **uma taxa única de venda** (ex.: 5,40); o fornecedor é pago no **câmbio real**. Para ele, congelar é **promoção** (oferecer taxa melhor que a real para atrair volume). Decompor o gap é o que muda o relatório:
```
Custo fornecedor USD 1.000 ; congelada 5,40 ; mercado no freeze 5,55 ; na liquidação 5,70
Subsídio intencional = (5,55−5,40)×1.000 = R$ 150  → custo de promoção (consciente)
Drift de mercado     = (5,70−5,55)×1.000 = R$ 150  → risco (aposta)
Gap total            = R$ 300
RateFreeze (= Promotion via Exchange) ; ExchangeExposure (posição AGREGADA do livro)
Eventos: RateFrozen, RateSubsidyAccrued, BookPositionDrifted
```
Só mensurável porque `Exchange` é dono das duas pontas (taxa servida × taxa real da liquidação).

### 7.3 Parâmetros governados e o diretor
```
Precedência (quem ganha): Diretiva do diretor > Promoção > Contrato > Política > Padrão do sistema
Cada número guarda proveniência (qual camada venceu, quem, quando). Vive em CommercialPolicy.
```
Câmbio congelado é taxa única global; comissão ao agente pode ter escopo por agência/produto (a confirmar).

### 7.4 Ciclo da reserva e política de cancelamento
```
Booking: QUOTED → ORDERED → PENDING(até 72h) → CONFIRMED → (CHANGED|CANCELLED|NO_SHOW) → COMPLETED
CancellationPolicy: type(STANDARD|ALL_SALES_FINAL|CUSTOM), windows[{horas, %multa}], refundable, costBearer
NoShowPolicy (carro): fee, waivedIfFlightCancelled
```
Armadilha: em `ALL_SALES_FINAL`, o portal (Portal de Experiências) é cobrado pela Marketplace de Tours mesmo reembolsando o cliente — duas obrigações distintas que não se anulam.

### 7.5 Conciliação
```
ReconciliationCase cruza: a pagar ao fornecedor (moeda estrangeira, vence depois) × a receber da agência (reais)
                          × comissão esperada × recebida × ganho/perda cambial
```
Pontas em tempos e moedas diferentes. Responde, com número, “virou margem ou foi vaidade?”.

### 7.6 Sourcing híbrido e a fronteira governada
```
priceOrigin = MANUAL     → PriceSuggestion + Override permitido (registrado)
priceOrigin = INTEGRATED → trustExternalPrice = true ; sem recompor (escopo do POC)
PriceSuggestion = basePrice(externo/manual) + Exchange + Commissioning + CommercialPolicy
  grava suggestedAmount × appliedAmount ; se diferem → OverrideRecord{quem,quando,de→para,motivo}
```
O produto inventado no balcão gera os mesmos eventos que um de API.

### 7.7 Compliance — documentos, anexo obrigatório e retenção

**O conceito legal.** A contabilidade não pode lançar uma operação sem o respectivo **documento hábil/idôneo** (NBC ITG 2000, Resolução CFC 1.330/2011; Código Civil art. 1.179–1.180, que manda o Livro Diário referenciar o documento probante). Não é “sempre NF nem sempre recibo” — depende da operação:

| Operação | Documento hábil esperado | Observação |
|---|---|---|
| Compra/serviço de pessoa jurídica (CNPJ) | **NF-e / NFS-e** | obrigatória entre empresas |
| Serviço de autônomo (pessoa física) | **RPA** (Recibo de Pagamento Autônomo) | + retenções (IRRF, INSS) |
| Conta de consumo (água, luz, telefone) | **fatura + comprovante de pagamento** | não gera NF |
| Empréstimo/mútuo | **contrato** | formaliza a operação |
| Comissão a receber/pagar | **NF de comissão / fatura / relatório de comissão** | base = só a comissão (ISS) |
| Pagamento em si | **comprovante (PIX, TED, boleto)** | complementa o documento fiscal |
| Reembolso | **comprovante + documento de origem** | |
| Folha/ponto | **holerite, espelho de ponto, AFD/AEJ** | ver 7.8 |
| Reserva/venda | **voucher, localizador, confirmação** | lastreia a operação comercial |

> Recibo simples só vale como documento hábil para quem **não é obrigado a emitir NF** (Lei 8.846/1994); nota de débito **não** é documento hábil.

**A regra do financeiro, modelada.** Toda entrada em Contas a Pagar/Receber referencia um `Document` no cofre. A entrada pode ser criada provisoriamente, mas **não passa no fechamento mensal** sem o documento obrigatório anexado:
```
Document (no cofre/Compliance): type, fileRef, hash, issuedAt, retentionUntil, signedFormat?
DocumentRequirement (policy): por tipo de lançamento, qual documento é obrigatório e quando
MonthlyClose: trava o período; rejeita lançamentos sem o documento exigido
Eventos: DocumentAttached, RequirementUnmet, PeriodClosed, RetentionExpiring
```
> **Em miúdos:** o sistema deixa você lançar “devo R$ X ao fornecedor Y”, mas no fim do mês ele confere: “cadê a nota?”. Sem ela, o mês não fecha.

**Retenção legal (vira política de guarda no cofre).**

| Documento | Prazo mínimo | Base legal |
|---|---|---|
| Documentos fiscais (NF-e/NFS-e), comprovantes de tributo | **5 anos** | CTN art. 173/174 |
| XML de DF-e (armazenamento) | **11 anos** | Ajuste SINIEF 2/2025 (a prescrição p/ o contribuinte segue 5 anos) |
| Livros e documentos contábeis | **10 anos** (conservador) | Código Civil art. 205 |
| Folha de pagamento, cartões/registros de ponto | **5 anos** | legislação trabalhista/previdenciária |
| Exames ocupacionais (ASO), PPP, PPRA | **20 anos** | normas de SST |
| FGTS/GFIP | **5 anos** (pós-STF 2014; cautela p/ períodos anteriores) | Lei 8.036/1990 |
| Contrato de trabalho, ficha de registro | **indeterminado** | trabalhista |
| Contratos de representação/comerciais | guardar enquanto vigentes + 5–10 anos | civil/fiscal |

> A retenção pode conflitar com a minimização da LGPD; a base legal de “cumprimento de obrigação legal” autoriza guardar. O cofre marca `retentionUntil` por documento e só permite expurgo após o prazo. Documentos com dado pessoal exigem controle de acesso e trilha.

**Certificado digital (ICP-Brasil).** O e-CNPJ é peça obrigatória do fluxo: emissão de NFS-e/NF-e, transmissão de SPED/eSocial, e as assinaturas do ponto (REP-A/REP-P). É um pré-requisito de integração que o `Platform` precisa gerir (custódia segura do certificado).

### 7.8 Ponto eletrônico físico, lido por crawling

Confirmado: o ponto é **físico** (REP), integrado por **web crawling** que vamos projetar. O essencial regulatório (Portaria MTP 671/2021, que revogou a 1.510/2009 e a 373/2011; complementada pela 1.486/2022):

- **Tipos de REP:** REP-C (equipamento físico convencional, certificado INMETRO; AFD extraído por USB), REP-A (alternativo, exige acordo coletivo), REP-P (via programa/software, certificado INPI, pode rodar em nuvem).
- **Artefatos:** **AFD** (Arquivo Fonte de Dados) = marcações brutas, imutáveis, `.txt` ASCII posicional, ordenado por **NSR** (Número Sequencial de Registro), com data/hora, CPF/PIS, CNPJ do empregador, série do REP e hash de integridade. **AEJ** (Arquivo Eletrônico de Jornada) = jornada tratada (banco de horas, ausências, matrícula eSocial), gerado pelo Programa de Tratamento (PTRP). **Espelho de Ponto** = relatório legível.
- **Detalhe que muda o desenho do crawler:** **só o REP gera o AFD com validade legal**; o programa de tratamento gera apenas espelho e AEJ. Logo, raspar a tela do portal do fornecedor de ponto entrega **dado operacional** (espelho/jornada), útil para RH, **mas não o artefato legal**. O AFD/AEJ assinado (CAdES `.p7s`) deve ser capturado pela exportação oficial e guardado no `Compliance` como documento de retenção (5 anos).

Desenho:
```
PointClockCrawler (em Integration, orquestrado por Platform)
  loga no portal do fornecedor de ponto → captura marcações/espelho (dado operacional)
  publica: PointSnapshotCollected, PointCrawlingFailed   (fila + circuit breaker)
People (RH) consome o snapshot → jornada, banco de horas, divergências
Compliance guarda o AFD/AEJ assinado (exportação oficial) com retentionUntil
Nunca escreve direto no miolo; tudo via evento/snapshot.
```
> **Em miúdos:** o robô lê a “tela” do ponto para o RH ver jornada e faltas no dia a dia; mas o “documento com fé legal” (AFD assinado) é exportado do aparelho e guardado no cofre, porque é ele que vale na fiscalização.

---

## Parte 8 — Inteligência e DSS (o que torna o ERP não-burro)

Três camadas, amarradas às alavancas de lucro reais da Acme Travel. **Aconselha, nunca comanda** — guardrails alertam, o humano decide. Tudo é read-model que escuta eventos; nada aqui manda na operação.

### 8.1 Camadas
- **Descritivo:** o que aconteceu (fato, com número).
- **Preditivo:** o que tende a acontecer (projeção).
- **Prescritivo:** a ação sugerida (a oportunidade concreta).

### 8.2 Catálogo de relatórios e ações, por alavanca

**A) Margem e lucro (o core econômico)**
- *Margem real por venda/agência/produto/canal* (spread − custos de servir, inclusive retrabalho de AfterSales). Descritivo.
- *Vazamento de margem*: onde override aplicado < devido, markup esquecido, comissão paga > contrato. Prescritivo → corrigir.
- *Produto “vende bonito, dá prejuízo”*: alto volume + alto AfterSales + margem real baixa. Prescritivo → repactuar ou descontinuar.

**B) Comissão e override (lucro direto no core)**
- *OverrideNudge*: “você está a R$ 30k da próxima faixa da Locadora B; +12 reservas = +3% retroativo no ano.” Prescritivo, alto retorno.
- *Faixas em risco de não bater* no fim do período de apuração → ação comercial direcionada.
- *Divergência de comissão recebida × esperada* (de Reconciliation) → cobrar o fornecedor.

**C) Câmbio e promo-câmbio**
- *PromoFxResult*: gap do período separado em subsídio (intencional) × drift (risco).
- *PromoConversion (ROI)*: subsídio gasto × volume/receita atraídos → a promo se paga?
- *PromoFxAdvisor (prescritivo)*: onde o congelamento converte (manter) vs. só queima margem (apertar), por rota/agência/produto.
- *LiveExposure*: posição cambial aberta do livro + alerta quando o drift fica perigoso.
- *Counterfactual*: margem se a venda usasse mercado + spread.

**D) Carteira e agências (o modelo da Acme Travel é desenvolver agência)**
- *ChurnRisk*: agência que desacelerou (queda de frequência/ticket) → reativar antes de perder.
- *Cross-sell*: agência que só aluga carro e nunca vendeu experiência → oferta dirigida.
- *Up-tier*: agência perto de virar de segmento → empurrão para subir.
- *Carteira ociosa por representante* → redistribuir esforço.

**E) Fornecedor (negociação)**
- *SupplierLeverage*: forecast de demanda por marca/destino → munição para negociar tarifa/override melhores.
- *Fornecedor problemático*: alto AfterSales/cancelamento → renegociar SLA ou substituir.
- *Concentração de risco*: dependência excessiva de um fornecedor/portal.

**F) Canal e conversão**
- *Funil por canal* (cotação → proposta → reserva) e *taxa de conversão*.
- *Canal que mais converte × que mais dá retrabalho manual*.
- *Atribuição de campanha → reserva* (de Marketing) → o que de fato virou venda.

**G) Operação e pós-venda**
- *Reservas pendentes (PENDING) perto do timeout de 72h* → agir antes de auto-rejeitar.
- *No-show e cancelamentos por fornecedor/rota* → padrão e custo.
- *Exposição a faturamento merchant* (ALL_SALES_FINAL) em aberto → risco financeiro a monitorar.

**H) Financeiro, conciliação e compliance**
- *Divergências de conciliação* (AP × AR × comissão) priorizadas por valor.
- *Lançamentos sem documento perto do fechamento* (de Compliance) → o que falta anexar para fechar o mês.
- *Retenções vencendo / documentos a expurgar* → higiene do cofre.
- *Fluxo de caixa projetado* cruzando a pagar (moeda estrangeira, vence depois) × a receber (reais).

**I) Forecast e risco (preditivo)**
- *Demanda por destino/produto/temporada* → planejamento e negociação.
- *Receita e margem projetadas* por marca/canal.
- *Alerta de meta em risco* (comercial, por marca, por representante).

### 8.3 Forma das ações
```
Insight (read-model)
  evidência: o número e a fonte (proveniência)
  recomendação: a ação sugerida, com o ganho/risco estimado
  guardrail: alerta quando um limite é cruzado — NÃO bloqueia
Eventos consumidos: de TODOS os contextos. Saída: sugestão, nunca comando.
```
> **Em miúdos:** cada item acima é “fato + o que dá pra fazer + quanto vale”. A `OverrideNudge` transforma uma faixa de comissão invisível num empurrão de lucro concreto. O humano puxa o gatilho.

---

## Parte 9 — Mapa de contextos

```
            [ Sistemas externos ]
  Marketplace de Tours(Portal de Experiências) · Locadora Internacional/GDS · Locadora B(Portal de Locação) · Rede Hoteleira · Site de cotação · REP de ponto · Newsletter
            │
            ▼
  ┌───────────────────────────────────────────────┐
  │ Integration (ACL + crawler de ponto) — operada por → Platform (TI) │
  └───────────────────────────────────────────────┘
            ▼
  Sourcing ─→ Quoting ──compõe usando──► Exchange (Open-Host) + Commissioning + CommercialPolicy
                 │
                 ▼
              Booking ───────────────► Commissioning (duas pontas)
                 │                          │
                 ▼                          ▼
             AfterSales              Reconciliation ──► Payout / Billing ──► Finance (generic)
                                            │                    │
                                            ▼                    ▼
                                     Compliance (cofre) ◄── anexos de AP/AR, fiscal, ponto
  Accounts → identidade comercial   Portfolio → o que a Acme Travel representa   People → RH/ponto
  Marketing → segmenta + newsletter (ACL)
  Intelligence (DSS) → escuta TODOS, só lê, e aconselha
```
Padrões: Integration = ACL; Exchange/CommercialPolicy = Open-Host; Intelligence = só leitura; Compliance = cofre transversal com regras de retenção; Platform opera o crawler de ponto (publica snapshots/eventos, nunca escreve no miolo).

---

## Parte 10 — Inventário de integrações

| Integração | Tipo | Direção | Nível | Hoje | Alvo |
|---|---|---|---|---|---|
| Portal de Experiências | Portal próprio (API tipo Marketplace de Tours) | dois sentidos | API própria | promo/comissão e parte do câmbio por dentro | ERP vira fonte de câmbio/regra |
| Portal de Locação (marcas de locação (A/B/C)) | Portal próprio (carro) | dois sentidos | própria | parcelamento, comissão, câmbio embutido | câmbio servido pelo ERP |
| Locadora Internacional | Fornecedor externo | consulta/reserva | GDS/API | 15% sobre base rate | ACL; ERP recomissiona |
| Ponto eletrônico (REP) | Externo (RH) | leitura | crawling + exportação AFD | — | snapshot p/ People; AFD/AEJ p/ Compliance |
| Site de cotação gratuita | Entrada de demanda | entra | a construir | — | cria cotação no ERP |
| Newsletter (Mailchimp/RD) | Externo | dois sentidos | API | — | ERP dono da base e do consentimento |
| Sites de fornecedor / catálogo físico | Sem integração | manual | nenhum | manual | Sourcing + reserva manual |

---

## Parte 11 — Glossário (pt-BR / en-US / o que é)

| pt-BR | en-US | O que é |
|---|---|---|
| Conta Comercial | Account | agência/agente (CNPJ/MEI/CPF) |
| Cotação | Quote | proposta de preço com validade |
| Composição da Cotação | Quote Composition | preço-base + câmbio + comissão + markup |
| Sugestão de Preço | Price Suggestion | número que o ERP calcula (manual); editável com rastro |
| Registro de Override | Override Record | quando o humano diverge da sugestão |
| Reserva | Booking | compromisso operacional, com localizador |
| Câmbio (contexto) | Exchange | dono da taxa e da posição de risco |
| Câmbio Congelado | Pinned Sell Rate | taxa única de venda do diretor |
| Exposição Cambial | Exchange Exposure | posição agregada do livro |
| Subsídio Cambial | Rate Subsidy | parte do gap dada de propósito (promo) |
| Drift de Mercado | Market Drift | parte do gap por o mercado mexer (risco) |
| Comissão a Receber | Supplier Commission / Override | do fornecedor |
| Comissão a Pagar | Agent Commission | ao agente |
| Spread / Margem | Spread | a receita real da Acme Travel |
| Repasse | Payout | pagar o agente / liquidar o fornecedor |
| Conciliação | Reconciliation | casar esperado × realizado |
| Documento Hábil | Supporting Document | comprovante legal do lançamento |
| Cofre de Documentos | Document Vault | onde os anexos ficam, com retenção |
| Anexo Obrigatório | Mandatory Attachment | sem ele o mês não fecha |
| Retenção | Retention | prazo legal de guarda |
| Fechamento Mensal | Monthly Close | trava do período; exige documentos |
| Ponto (AFD) | Time Records (AFD) | marcações brutas do REP, imutáveis |
| Jornada Tratada (AEJ) | Processed Journal | jornada/banco de horas |
| Diretiva Comercial | Commercial Directive | ordem top-down do diretor, auditável |
| Parâmetro Governado | Governed Parameter | número resolvido por prioridade, com origem |
| Apoio à Decisão | Decision Support (DSS) | aponta número/oportunidade; não decide |
| Representação | Portfolio | o que a Acme Travel representa |
| Inventário Interno | Assets | patrimônio da empresa |
| Plataforma (TI) | Platform | saúde, credenciais, jobs, observabilidade |

---

## Parte 12 — Caminho de construção (roadmap)

**Fatia 0 — Event Storming** da “venda Portal de Experiências ponta a ponta” (1 semana). Onde a linguagem muda, há fronteira.

**Fatia 1 — Núcleo, 100% manual e rastreável.** Accounts + Sourcing + Quoting (composição + sugestão) + Exchange + Commissioning + Booking (localizador manual) + Reconciliation + CommercialPolicy. **Sucesso:** venda manual de carro em Orlando, câmbio congelado, duas comissões, spread, override com rastro, conciliação — sem integrar nada.

**Fatia 2 — Compliance mínimo.** Cofre de documentos + anexo obrigatório em AP/AR + trava de fechamento mensal + retenção. É barato e protege a empresa cedo.

**Fatia 3 — Uma integração real (ACL).** Site de cotação gratuita, com Platform monitorando; prova o ramo `INTEGRATED` (preço fechado).

**Fatia 4 — Cancelamento como objeto + armadilha do merchant.**

**Fatia 5 — Câmbio com exposição + primeiros relatórios.**

**Fatia 6 — Crawler de ponto.** Snapshot p/ People + captura do AFD/AEJ p/ Compliance, com fila e disjuntor.

**Fatia 7 — Intelligence (DSS) prescritivo.** Começar por `OverrideNudge` e `PromoFxAdvisor` (lucro direto), depois churn, forecast, mix.

**Fatia 8+ — Apoio e genéricos:** Billing, Payout, AfterSales, Marketing, Portfolio, Assets. Finance/Identity/Admin: avaliar comprar.

Empacotamento: **monólito modular** (fronteiras duras), não micro-serviços.

---

## Parte 13 — Decisões em aberto

1. **Nome do câmbio:** `Exchange` serve? (alt.: `Currency`, `Treasury`)
2. **Inventário:** `Portfolio` (representação) + `Assets` (patrimônio) — os dois, ou só um?
3. **A Portal de Experiências é “merchant of record” da Marketplace de Tours** (assume cobrança/reembolso/faturamento) ou afiliada? Define o motor de cobrança/reembolso.
4. **Override do fornecedor** tem escala por volume (tiers retroativos) ou é fixo por marca?
5. **Comissão ao agente é escopada** por agência/produto/canal?
6. **Tipo de REP de ponto** (REP-C físico / REP-A / REP-P)? Muda como se captura o AFD (USB vs. exportação/portal) e o que o crawler consegue de fato.
7. **Quem emite a NF de comissão** e em que regime tributário (Simples/Presumido/Real)? Afeta Billing e a base/retenções.
8. **O operador edita só dados, ou também regras/fluxos em runtime?**

---

*Bases legais citadas: NBC ITG 2000 / Resolução CFC 1.330/2011; Código Civil arts. 1.179–1.180, 1.194, 205; CTN arts. 173–174, 195; Lei 8.846/1994; Ajuste SINIEF 2/2025; Lei 8.036/1990; Portaria MTP 671/2021 (e 1.486/2022); Lei 13.709/2018 (LGPD). Fatos de negócio sobre Acme Travel/Portal de Experiências/Portal de Locação/Locadora Internacional/Marketplace de Tours vêm do relatório de pesquisa que acompanha o projeto.*
