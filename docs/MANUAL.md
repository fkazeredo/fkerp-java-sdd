# Manual de Instruções — ERP Acme Travel

> Manual em **português**, para o usuário/operador (não técnico). Descreve **o que o sistema já faz
> hoje**. É atualizado **a cada fatia entregue** (ver o comando *User manual* no `CLAUDE.md`).
> Versão em inglês (espelho, mantida em sincronia): `docs/MANUAL.en-US.md`.
>
> **Versão do sistema:** 0.10.0 · **Fase atual:** 8 (Finance, pleno)

## 1. O que é o sistema

O **ERP Acme Travel** é o sistema de gestão comercial e financeira da Acme Travel (uma
representante de marcas de turismo — GSA). Quando pronto, ele vai cuidar de câmbio, comissões,
cotações, reservas, conciliação e dos documentos fiscais das vendas.

As funcionalidades de negócio são entregues **uma fatia vertical por vez**. Este manual foca nas
fatias que já têm **tela/jornada para o usuário**; capacidades internas aparecem aqui conforme
ganham uso direto pelo operador (veja o *Histórico de versões* no fim).

## 2. Como acessar

> A Fase 0 ainda não tem um instalador; o sistema é iniciado por linha de comando. Você precisa ter
> **Docker** e **Node.js** instalados. Os passos abaixo são simples e estão explicados.

**Passo 1 — Iniciar o servidor e o banco de dados.** Na pasta do projeto, execute:

```
docker compose up --build
```

Isso sobe o banco de dados e o servidor da aplicação. Quando aparecer que está "started", o servidor
está pronto em `http://localhost:8080`.

**Passo 2 — Abrir a tela.** Em outra janela de terminal, na pasta `frontend`, execute uma vez
`npm install` e depois:

```
npm start
```

Abra o navegador em **`http://localhost:4200`**.

**Para encerrar:** feche o `npm start` e rode `docker compose down` na pasta do projeto.

## 3. Funcionalidades disponíveis

### Fase 0 — Tela "Saúde do sistema"

É a única tela desta fase. Ao abrir `http://localhost:4200`, o sistema verifica automaticamente se
está tudo no ar e mostra um destes estados:

| Estado | O que você vê | O que significa |
|---|---|---|
| Verificando | **"Verificando…"** | O sistema está consultando se está tudo no ar (alguns instantes). |
| Saudável | **"Sistema saudável"** + Status: `UP` e Banco de dados: `UP` (caixa verde) | Aplicação e banco de dados estão funcionando. |
| Com erro | **"Não foi possível contatar o backend"** + botão **"Tentar novamente"** (caixa vermelha) | O servidor está fora do ar ou inacessível. Verifique se o `docker compose up` está rodando e clique em **Tentar novamente**. |

> Para quem é de TI: a mesma verificação está disponível em `GET http://localhost:8080/api/system/health`,
> que responde `{ "status": "UP", "db": "UP" }`.

### Fase 3 — Procedência da oferta (de onde a venda veio)

A partir desta fase o sistema registra **de onde uma oferta veio** — o que o redesenho chama de
*Sourcing*. Isso vale tanto para a venda digitada à mão (um preço de um site externo, de um catálogo
de papel ou de um pedido por telefone) quanto para a cotação que chega automaticamente do **site de
cotação** (ver abaixo).

**Registrar uma oferta manualmente.** O operador pode anotar a procedência de uma oferta informando:

- **Descrição do produto** (texto livre — por exemplo, "City Tour Rio - dia inteiro"). Não precisa
  estar num catálogo estruturado: texto livre é uma oferta válida.
- **Preço-base** (valor e moeda).
- **Origem:** Portal próprio, Site externo, Catálogo de terceiros, ou Demanda crua (pedido avulso).
- **Nível de integração:** Nenhum, Entra (o sistema externo alimenta o ERP) ou Dois sentidos.
- **Referência externa** (opcional): por exemplo, o número da cotação no site de origem.

> Para quem é de TI: `POST /api/sourcing/offers` registra a oferta e `GET /api/sourcing/offers/{id}`
> consulta. É um registro de **rastreabilidade** (de onde veio a venda); ele não calcula preço.

**Cotação automática do site de cotação (cotação integrada).** Quando o **site de cotação** envia uma
cotação para o sistema, o ERP cria automaticamente uma **cotação integrada**: o preço que veio é um
**preço fechado e confiável**, então o sistema **não recalcula** câmbio, comissão nem margem — ele
apenas registra a cotação com aquele preço. O que o operador precisa saber:

- A cotação aparece com origem **INTEGRADA** e o **valor aplicado é exatamente o preço recebido** (sem
  sugestão e sem ajuste manual).
- O sistema só aceita a cotação se a mensagem vier **assinada** (uma chave de segurança combinada com
  o site). Mensagem sem assinatura ou com assinatura errada é **recusada** e **nada é criado**.
- Se o **mesmo** número de cotação chegar de novo, o sistema **não duplica**: devolve a mesma cotação
  já criada.
- A cotação precisa estar ligada a uma **conta comercial já cadastrada** (pelo documento). Se o
  documento não corresponder a nenhuma conta, a cotação é **recusada** (cadastre a conta primeiro).

> Para quem é de TI: o site chama `POST /api/integration/quotation-site/inbound` com o cabeçalho
> `X-Signature`. A saúde do conector está em `GET /api/integration/quotation-site/health`.

### Fase 4 — Política de cancelamento, multas e a "armadilha do merchant"

A partir desta fase o cancelamento deixa de ser "desfaz tudo, sem multa" e passa a seguir uma
**política de cancelamento** configurável por produto/fornecedor. A política diz: que tipo é
(Padrão, Venda final, ou Personalizado), quais são as **janelas de multa** (quanto se cobra conforme
a proximidade do serviço), se é reembolsável, **quem paga** a multa, e se a venda é "merchant"
(o portal assume a cobrança/reembolso) ou afiliada.

**Configurar a política de um produto.** Um usuário administrativo informa, para um produto/fornecedor:

- **Tipo:** *Padrão* (multa pela janela), *Venda final* (`ALL_SALES_FINAL` — não reembolsável do
  ponto de vista do fornecedor) ou *Personalizado*.
- **Janelas de multa:** pares "até quantas horas antes do serviço" → "percentual da multa" (por
  exemplo: até 24h antes, 50%; até 72h antes, 25%). Cancelou com mais antecedência que todas as
  janelas? Sem multa.
- **Quem paga** a multa: a agência, a Acme ou o fornecedor.
- **Venda merchant?** Se sim, a Acme assume a obrigação com o fornecedor e o eventual reembolso ao
  cliente. Se não (afiliada — o padrão), quem assume é o fornecedor.
- **Multa de no-show** (carro) e se ela é **dispensada com prova de voo cancelado**.

> Para quem é de TI: `PUT /api/products/{ref}/cancellation-policy` grava e
> `GET /api/products/{ref}/cancellation-policy` consulta. Produto sem política configurada usa um
> **padrão seguro** (Padrão, afiliado, sem janelas, sem multa).

**Cancelar uma reserva.** Ao cancelar, o operador informa o **motivo**, **quando o serviço começa** e,
se houver, **um reembolso ao cliente**. O sistema usa a política **congelada na confirmação da
reserva** (não a política atual — assim, mudar a política depois não altera reservas já confirmadas) e
devolve a lista de **encargos** gerados:

- **Multa** (na venda Padrão/Personalizado), conforme a janela e com quem paga.
- Na **Venda final**: o **custo com o fornecedor** continua **devido por inteiro**, mesmo que se
  decida **reembolsar o cliente**. São **duas obrigações separadas que não se anulam** — é a
  **"armadilha do merchant"**: tratá-las como uma só (descontar uma da outra) esconderia dinheiro
  perdido. O sistema mantém as duas visíveis.

**Registrar um no-show (carro).** Se o cliente não comparece, o sistema cobra a **multa de no-show**
da política. Se a política permite **dispensa com prova de voo cancelado** e o operador informa a
prova, a multa é **dispensada**.

> Para quem é de TI: `POST /api/bookings/{id}/cancel` agora aceita `{reason, serviceStartsAt,
> refundAmount}` e devolve os encargos; `POST /api/bookings/{id}/no-show` aceita
> `{flightCancelledProof}`. A verificação formal do documento de prova é responsabilidade do cofre de
> documentos (Compliance), em fase posterior.

### Finance — Contas a Pagar/Receber e o fechamento do mês

O sistema mantém o **livro-caixa** da Acme: o registro do que a empresa **deve** (Contas a Pagar) e
do que tem a **receber** (Contas a Receber), organizado por **mês contábil** (período `AAAA-MM`).

**Lançar uma conta a pagar ou a receber.** O operador registra um lançamento informando a direção
(a pagar ou a receber), a parte (fornecedor, agência/agente), o valor e a moeda, o tipo do lançamento
e o mês. O lançamento nasce **provisório** (pode ainda faltar o documento). Ele pode depois ser
**confirmado**.

**Fechar o mês — e a "regra de ouro".** Ao fechar um período, o sistema **consulta o cofre de
documentos (Compliance)**. Se houver lançamento sem o documento obrigatório, **o mês não fecha**: o
sistema responde quais lançamentos estão pendentes e o que falta anexar. Com os documentos anexados,
o mesmo período **fecha**. Esse é o ponto onde "não fecha sem a nota".

**Lançamentos automáticos a partir das operações.** O operador não precisa lançar tudo à mão: quando
uma reserva gera um encargo, o sistema **cria o lançamento sozinho**, no mês em que o fato aconteceu:

- **Cancelamento com multa** → uma conta **a receber** (a multa da agência).
- **Reembolso ao cliente** → uma conta **a pagar** (o reembolso).
- **Custo do fornecedor na venda final (merchant)** → uma conta **a pagar** ao fornecedor — que
  **convive** com o reembolso ao cliente, sem se anular (a "armadilha do merchant").
- **No-show com multa** → uma conta **a receber** (a multa do não comparecimento).

Cada operação vira lançamento **uma única vez**, mesmo que o aviso interno chegue repetido — não há
duplicação.

**Ver o balancete do mês.** A qualquer momento, o operador pode consultar o **balancete operacional**
de um período: para **cada moeda** (sem misturar moedas), quanto há **a pagar**, **a receber** e o
**saldo** (a receber menos a pagar), além de quantos lançamentos estão provisórios, confirmados ou
liquidados. É uma visão de caixa do mês — não uma demonstração contábil.

> Para quem é de TI: `POST /api/finance/entries` (cria, devolve `PROVISIONAL`),
> `POST /api/finance/entries/{id}/confirm`, `GET /api/finance/entries?...` (lista paginada),
> `POST /api/finance/periods/{aaaa-mm}/close` (fecha — `409 finance.period.cannot-close` com as
> pendências quando o Compliance veta), `GET /api/finance/periods/{aaaa-mm}` (status + totais
> AP/AR por moeda) e `GET /api/finance/periods/{aaaa-mm}/trial-balance` (balancete por moeda com
> `payable`/`receivable`/`net` + contagens por status). Os lançamentos automáticos são criados ao
> consumir os eventos de cancelamento/no-show das reservas, de forma idempotente.

### Fase 8 — Pós-venda (chamados, SLA, reembolso e cancelamento)

O **pós-venda** registra os **chamados** (reclamação, pedido de alteração, pedido de cancelamento,
pedido de reembolso ou informação) ligados a uma reserva e acompanha o **prazo de atendimento (SLA)**.

O que o operador faz:

- **Abrir um chamado:** informa a reserva, o tipo e um resumo. O sistema calcula automaticamente os
  prazos de SLA a partir das **regras de SLA governadas** (padrão: **1ª resposta em 24h**, resolução
  em **72h**, e **48h** para cancelamento/reembolso). Esses prazos podem ser ajustados pela diretoria
  via **diretiva** (sem precisar de nova versão do sistema).
- **Conduzir o chamado:** assumir, pôr em andamento, colocar em espera e, ao fim, **resolver** e
  **encerrar**. Reabrir um chamado já resolvido fica registrado (conta para o "custo de servir").
- **Resolver com reembolso:** ao aprovar um reembolso, o sistema **encaminha o pagamento ao módulo de
  Repasse/Reembolso (Payout)** referenciando o próprio chamado — **uma única vez** (não cria reembolso
  duplicado). O reembolso ao cliente **não apaga** a obrigação com o fornecedor (armadilha do merchant
  preservada).
- **Resolver com cancelamento:** ao aprovar um cancelamento, o sistema **aciona o cancelamento da
  reserva** (que aplica a política de multa); o pós-venda **não** mexe na reserva por conta própria.
- **SLA estourado:** quando o prazo passa sem resolução, o chamado é **marcado como "em violação de
  SLA" (alerta)** — isso **não trava** o atendimento; serve para priorizar e para medir o "custo de
  servir" por produto/fornecedor.

> Para quem é de TI: `POST /api/aftersales/cases` (abre — devolve `OPEN` com `dueAt`),
> `POST /api/aftersales/cases/{id}/assign|progress|wait|resolve|close` (transições; `resolve` pode
> acionar Booking e/ou Payout), `GET /api/aftersales/cases/{id}` e
> `GET /api/aftersales/cases?type=&status=&bookingId=&breached=&page=&size=`. A varredura de SLA roda
> por job com relógio controlável; o reembolso é idempotente por chamado.

### Fase 8 — Marketing (consentimento LGPD, campanhas e atribuição)

O **marketing** fala com a base **B2B** (agências/agentes) e trata o **consentimento (LGPD)** como
condição obrigatória: **nunca** se manda comunicação para quem não autorizou. O módulo cuida do
consentimento, da segmentação, do disparo da newsletter e da medição de **quanto a campanha trouxe de
venda**. Ele **não é um CRM** completo — é a camada de consentimento e atribuição.

O que o operador faz:

- **Registrar consentimento:** informa o titular (agência/agente), a finalidade (ex.: newsletter) e a
  origem (ex.: formulário de cadastro). Cada decisão vira um **registro que não se apaga**; o estado
  atual é sempre a **última** decisão do titular para aquela finalidade.
- **Revogar consentimento:** a revogação **não apaga** o histórico — entra como uma nova decisão. A
  partir dela, o titular **deixa de receber** os próximos disparos.
- **Consultar consentimento:** ver o **estado atual** e todo o **histórico** de um titular para uma
  finalidade.
- **Criar um segmento:** define o público por **critérios sobre dados que já existem** (ex.: tipo de
  conta, região) — sem coletar nada novo. Critérios fora da lista permitida são recusados.
- **Estimar o alcance (prévia):** o sistema mostra para **quantos titulares consentidos** o segmento
  chegaria.
- **Criar e disparar uma campanha:** a campanha aponta para um segmento e tem um **código** próprio. No
  disparo, o sistema **envia só para quem consentiu**; quem não consentiu é **excluído e contado**
  (aparece como "suprimidos"); ninguém recebe **duas vezes** a mesma campanha. O envio sai por um
  **provedor de newsletter externo** (hoje um simulador rastreável).
- **Atribuir uma venda à campanha:** registra que uma reserva veio de um **código de campanha**. Quando
  essa reserva é **confirmada**, o sistema marca a **conversão** e manda esse sinal para a
  **Inteligência (DSS)** — é assim que se mede o retorno da campanha.
- **Atender pedido de exclusão (LGPD):** remove os **dados de marketing** do titular e **encerra** o
  consentimento, mas **guarda a prova de que ele saiu** (para não voltar a ser incluído por engano). O
  que **outra lei obriga a guardar** (notas fiscais, lançamentos financeiros, a reserva) **não** é
  apagado por aqui.

> Para quem é de TI: `POST /api/marketing/consents`, `DELETE /consents/{id}` (revoga),
> `GET /consents?subject=&subjectType=&purpose=`; `POST /segments`, `GET /segments/{id}/preview`;
> `POST /campaigns`, `POST /campaigns/{id}/send` (devolve `targeted/suppressedNoConsent/queued`),
> `GET /campaigns/{id}`; `POST /attribution`, `GET /attribution?campaignCode=`; `POST /erasure`. O
> consentimento é um log append-only (estado = última linha); o disparo é idempotente por
> `(campanha, destinatário)`; o provedor de newsletter é uma ACL (mock rastreável). Erros não vazam
> dado pessoal.

## 4. Glossário

- **Backend / servidor:** a parte do sistema que processa as regras e fala com o banco de dados.
- **Banco de dados:** onde as informações ficam guardadas (PostgreSQL).
- **Saúde / health:** uma verificação rápida de que o sistema está no ar e respondendo.
- **Procedência / Sourcing:** o registro de **de onde** uma oferta veio (portal próprio, site
  externo, catálogo, pedido avulso) e do quanto ela é integrada.
- **Cotação integrada:** cotação criada a partir de um **preço fechado e confiável** vindo de um
  sistema externo; o ERP não recalcula o preço (sem sugestão, sem ajuste manual).
- **Webhook:** uma mensagem que um sistema externo envia automaticamente para o ERP (aqui, a cotação
  vinda do site de cotação), sempre **assinada** para garantir que é legítima.
- **Política de cancelamento:** as regras de cancelamento de um produto — tipo, janelas de multa,
  quem paga, reembolsável, e se a venda é merchant ou afiliada.
- **Janela de multa:** "até X horas antes do serviço, cobra Y% de multa". Cancelar com mais
  antecedência que todas as janelas não gera multa.
- **Venda final (`ALL_SALES_FINAL`):** venda não reembolsável do ponto de vista do fornecedor: o
  custo com ele continua devido mesmo que se reembolse o cliente.
- **Merchant of record:** quando a Acme/portal **assume** a cobrança e o reembolso de uma marca; o
  contrário é **afiliada** (quem assume é o fornecedor).
- **Armadilha do merchant:** numa venda final merchant, o custo com o fornecedor **e** o reembolso ao
  cliente são **duas obrigações que não se anulam** — o sistema mantém as duas visíveis para não
  perder dinheiro de forma invisível.
- **No-show:** o cliente não comparece; gera uma multa, dispensável com prova de voo cancelado quando
  a política permite.
- **Lançamento (AP/AR):** o registro de uma conta **a pagar** (AP) ou **a receber** (AR), com valor,
  moeda, parte, tipo e o mês a que pertence.
- **Período / mês contábil (`AAAA-MM`):** o mês ao qual os lançamentos pertencem; é a unidade do
  fechamento mensal.
- **Fechamento do mês:** trava o período; só fecha se todos os lançamentos têm o documento obrigatório
  (conferido pelo cofre de documentos / Compliance).
- **Lançamento provisório:** lançamento já registrado, mas que ainda pode estar sem o documento
  obrigatório; vira **confirmado** quando validado.
- **Consentimento (LGPD):** a autorização do titular para receber uma comunicação (ex.: newsletter);
  sem ela, **não** se envia. Fica registrado com finalidade, base legal e data; a **revogação** entra
  como uma nova decisão e o histórico é preservado.
- **Segmento:** um público definido por **critérios sobre dados já existentes** (ex.: tipo de conta,
  região); não coleta dado novo.
- **Campanha:** um envio para um segmento, com um **código** próprio usado para medir a atribuição.
- **Supressão (no disparo):** quando um destinatário é **excluído** do envio por não ter consentimento;
  o sistema **conta** os suprimidos em vez de falhar o disparo todo.
- **Atribuição / conversão:** o vínculo entre o **código** de uma campanha e uma **reserva**; quando a
  reserva é confirmada, vira uma **conversão** (sinal de retorno da campanha para a Inteligência).
- **Exclusão LGPD (erasure):** atender o pedido do titular de apagar seus **dados de marketing**,
  preservando a **prova de revogação** (para não reincluí-lo) e o que **outra lei** manda guardar.

## 5. Histórico de versões do manual

| Versão | Fase | O que mudou no manual |
|---|---|---|
| 0.1.0 | 0 — Fundação | Primeira versão: visão geral, como acessar e a tela "Saúde do sistema". |
| 0.4.0 | 3 — Integração | Procedência da oferta (*Sourcing*): registro manual de oferta e cotação automática vinda do site de cotação (ramo INTEGRADO). |
| 0.5.0 | 4 — Cancelamento | Política de cancelamento por produto, multas por janela, no-show com dispensa por prova de voo, e a "armadilha do merchant" (duas obrigações que não se anulam na venda final). |
| 0.10.0 | 8 — Finance (full) | Contas a Pagar/Receber e o fechamento mensal com a "regra de ouro" (não fecha sem a nota); **lançamentos automáticos** a partir de cancelamentos e no-show das reservas (uma vez só, sem duplicar); balancete do período por moeda. |
| 0.13.0 | 8 — AfterSales | Pós-venda: chamados (reclamação/alteração/cancelamento/reembolso/informação) ligados à reserva; prazos de **SLA governados** (24h/72h/48h, ajustáveis por diretiva) com alerta de violação que **não trava**; resolução que **encaminha** reembolso ao Payout (uma vez, sem cancelar a obrigação do fornecedor) e cancelamento à reserva; "custo de servir" por chamado. |
| 0.14.0 | 8 — Marketing | Marketing B2B com **consentimento LGPD** obrigatório: registrar/revogar/consultar consentimento (histórico preservado); **segmento** por dados existentes com **prévia** de alcance; **campanha** que **só envia para quem consentiu** (suprimidos contados, sem envio duplicado) via provedor de newsletter; **atribuição** código→reserva que vira **sinal de conversão** para o DSS; **exclusão LGPD** que apaga o dado de marketing mas preserva a prova de revogação. |

> Observação: o manual foca nas fatias com tela/jornada para o usuário; capacidades internas das
> Fases 1, 2 e 5–8a aparecem aqui conforme ganham uso direto pelo operador.
>
> Fase 15 — Docs bilíngues (chore, sem mudança de versão): a cobertura bilíngue, antes só do manual,
> passou a incluir o **README** (`README.en-US.md`) e o **changelog consolidado en-US**
> (`docs/release-notes/CHANGELOG.en-US.md`). Relatórios técnicos seguem só em pt-BR (Regra Zero).
