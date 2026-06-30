# Manual de Instruções — ERP Acme Travel

> Manual em **português**, para o usuário/operador (não técnico). Descreve **o que o sistema já faz
> hoje**. É atualizado **a cada fatia entregue** (ver o comando *User manual* no `CLAUDE.md`).
> Versão em inglês (espelho, mantida em sincronia): `docs/MANUAL.en-US.md`.
>
> **Versão do sistema:** 0.22.0 · **Fase atual:** 11 (Observabilidade & monitoramento)

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

### Fase 8 — Portfólio (marcas representadas, contratos e metas)

O **portfólio** registra **o que a Acme representa** comercialmente: as **marcas/fornecedores** que ela
vende em nome de terceiros (a Acme é uma representante/GSA), os **contratos de representação** que dão
esse direito e as **metas por marca**. Ele **não** mexe em preço nem em comissão — serve de
**referência** ("qual marca") para a cotação, a comissão e a Inteligência, e ajuda a governança a
acompanhar contratos e metas.

O que o operador faz:

- **Cadastrar uma marca:** informa o **identificador** da marca (ex.: `ALAMO`) e o **nome** de exibição.
  A marca nasce **ativa**. Não se cadastram duas marcas com o mesmo identificador.
- **Desativar uma marca:** quando a representação termina, a marca fica **inativa** (continua no
  histórico, mas não é mais representada ativamente).
- **Listar/consultar marcas:** ver todas, ou filtrar por **ativas/inativas**.
- **Registrar um contrato de representação:** informa a **vigência** (de/até), o **documento do
  contrato** (que já está guardado no cofre de documentos — Compliance) e, se quiser, **condições de
  referência** (não são preços). Vender uma marca **sem contrato vigente** **não é bloqueado** — o
  sistema apenas **sinaliza** (alerta), e quem faz a venda decide.
- **Conferir a cobertura do contrato:** perguntar se uma marca tem **contrato vigente** numa data — é
  uma consulta de apoio (alerta), nunca uma trava.
- **Alerta de contrato a vencer:** o sistema sinaliza, **uma vez por contrato**, os contratos que
  **estão a vencer** (até 30 dias) ou já venceram, para a governança agir. É **aviso**, não bloqueio.
- **Definir uma meta por marca:** escolhe a marca, o **período** (um ano `2026` ou um mês `2026-06`) e
  a **métrica** — **receita** (um valor em reais) ou **volume** (uma quantidade de vendas). Cada marca
  tem **uma** meta por período e métrica.
- **Atribuir uma venda à marca:** registra que uma **reserva** pertence a uma marca representada. É
  esse vínculo que permite ao sistema somar a venda na marca certa.
- **Acompanhar o realizado vs meta:** o sistema mostra, para uma marca e período, **quanto já foi
  realizado** e o **percentual de atingimento**. O realizado vem das **vendas confirmadas** da marca
  (volume) e do **spread realizado** delas (receita) — calculado a partir dos eventos de venda, **sem
  alterar** a venda. Vendas sem marca atribuída não entram em nenhuma meta.

> Para quem é de TI: `POST /api/portfolio/brands`, `GET /brands/{id}`, `GET /brands?status=`,
> `DELETE /brands/{id}` (desativa); `POST /brands/{brandRef}/contracts`,
> `GET /brands/{brandRef}/contract-coverage?on=`; `POST /contracts/flag-expiring` (dispara o alerta de
> expiração); `POST /brands/{brandRef}/goals`, `GET /brands/{id}/goals/{period}/progress`,
> `POST /brands/{brandRef}/sales` (intake venda→marca). Identificadores de outros contextos (documento,
> reserva, caso) são **valores**, sem FK; nenhum preço/comissão mora aqui — o realizado é só uma
> projeção (read-model) dos eventos de venda.

### Fase 8 — Patrimônio interno (equipamentos, licenças e outros bens)

O **patrimônio interno** (*Assets*) registra os **bens da própria Acme** — equipamentos, **licenças de
software** e outros bens —, com o **custo** de aquisição e os vínculos ao **documento** (no cofre de
documentos / Compliance) e ao **lançamento financeiro** correspondentes. É um registro **enxuto**: serve
para amarrar custo↔documento e **avisar quando uma licença está para vencer**; **não** é um sistema
completo de gestão de ativos (não calcula depreciação, não controla manutenção/chamados de TI, não é
estoque de revenda). É **patrimônio, não produto**: não entra em preço nem em venda.

O que o operador faz:

- **Cadastrar um bem:** escolhe o **tipo** (equipamento, licença de software ou outro), informa a
  **identificação** (ex.: "JetBrains All Products Pack"), a **data** e o **custo** de aquisição e, se
  quiser, o **fornecedor**, o **documento** (nota/contrato já guardado no cofre) e o **lançamento de
  custo** (no financeiro) — estes dois são referenciados por **identificador**, sem duplicar o dado. O bem
  nasce **ativo**. Para uma **licença de software**, a **data de vencimento é obrigatória**.
- **Consultar/listar bens:** ver um bem pelo identificador, ou listar filtrando por **tipo** e/ou
  **situação** (ativo/baixado). Pode também pedir as **licenças a vencer** nos próximos **N** dias.
- **Dar baixa em um bem:** quando o bem sai de uso, registra-se a **baixa** com o **motivo**; o sistema
  guarda **quem** baixou e **quando** (auditoria). A baixa é **definitiva** — um bem já baixado não pode
  ser baixado de novo.
- **Aviso de licença a vencer:** o sistema sinaliza, **uma vez por licença**, as licenças que **estão a
  vencer** (até 30 dias) ou já venceram, para a TI/governança renovar a tempo. É **aviso**, não bloqueio.

> Para quem é de TI: `POST /api/assets`, `GET /assets/{id}`,
> `GET /assets?type=&status=&expiringWithinDays=`, `POST /assets/{id}/retire` (baixa com motivo),
> `POST /assets/flag-expiring` (dispara o aviso de vencimento). Os identificadores de documento
> (Compliance) e lançamento (Finance) são **valores**, sem FK; nenhum preço de venda mora aqui. Se o
> negócio precisar de **gestão plena de ativos** (depreciação, manutenção), a recomendação é **comprar**
> um sistema dedicado e usar este módulo como registro/integração.

### Fase 8 — Pessoas (RH): colaboradores, jornada e banco de horas

O módulo de **Pessoas** (*People*) é o **mínimo de RH** construído **sobre o ponto eletrônico** já
existente: ele transforma o **espelho operacional** do ponto (que o robô do ponto coleta — dado do
dia a dia, **não** o documento legal) em **jornada do período**, **banco de horas** e **avisos de
divergência** para o RH tratar. **Não é folha de pagamento:** não calcula eSocial, FGTS, férias nem
13º — para isso o caminho é **comprar/integrar** um sistema de folha; aqui ficam o **colaborador**, a
**jornada** e o **saldo**.

O que o operador faz:

- **Cadastrar um colaborador:** informa um **identificador** (matrícula/código, único), a **data de
  admissão** e a **jornada contratada** por dia no formato `HH:mm` (ex.: `08:00`). O colaborador nasce
  **ativo** (situação: ativo, afastado ou desligado). Pode-se associar o **contrato de trabalho**
  (documento guardado no cofre) por referência, sem duplicar o dado.
- **Processar a jornada de um período:** para um colaborador e um mês (`AAAA-MM`), o sistema monta a
  **jornada** a partir do espelho operacional já coletado e calcula o **banco de horas**:
  **saldo = horas trabalhadas − horas contratadas** no período. Saldo **positivo** são **horas
  extras**; **negativo** são **faltas** (a lei admite banco negativo). O cálculo **mede** o saldo —
  ele **não** paga hora extra nem marca folga (isso é folha).
- **Consultar a jornada e o banco de horas:** ver a jornada montada do período e o **banco de horas**
  (horas trabalhadas, horas contratadas, saldo com sinal `+`/`−`, e quantas divergências o período
  tem). Ex.: trabalhou `176:20`, contratado `176:00` → saldo `+00:20`.
- **Tratar divergências:** quando o ponto tem **marcação ímpar** (uma entrada sem a saída), **marcação
  faltante** ou uma **jornada incoerente**, o sistema **abre um aviso** (divergência) numa **fila para
  tratamento humano** — e **nunca corrige sozinho**. A fila pode ser filtrada por **período** e por
  **situação** (aberta/resolvida).
- **Arquivar o holerite:** o holerite/espelho processado é **guardado no cofre de documentos**
  (Compliance) como documento de **folha**, com **retenção de 5 anos** e marcado como **dado pessoal**
  (acesso auditado — LGPD). O RH anexa o arquivo; o sistema cuida do prazo de guarda.

> Para quem é de TI: `POST /api/people/employees` (cadastrar), `GET /employees/{id}`,
> `GET /employees?status=`, `POST /employees/{id}/journey` (processar a jornada do período),
> `GET /employees/{id}/journey?period=`, `GET /employees/{id}/timebank?period=`,
> `GET /api/people/discrepancies?period=&status=` (fila de divergências),
> `POST /employees/{id}/payslip` (arquivar o holerite no cofre). O **espelho do ponto** é tratado
> sempre como **dado operacional, não legal** — o documento com fé legal (AFD/AEJ assinado) vive no
> cofre, vindo da exportação oficial do ponto (não desta tela). **Folha pesada** (eSocial/FGTS/13º) =
> **comprar/integrar**.

### Fase 8 — Plataforma (TI): certificado e-CNPJ, jobs e auditoria de sistema

O módulo de **Plataforma** (*Platform*) é a **infraestrutura operada** que sustenta os módulos
fiscais e de integração. Ele **não tem regra de negócio** — ele **guarda** segredos, **governa** as
rotinas automáticas (jobs) e **registra** a auditoria do sistema. É voltado ao **time de TI**.

O que o operador de TI faz:

- **Acompanhar o certificado e-CNPJ:** o certificado digital (ICP-Brasil) é peça obrigatória para
  emitir NFS-e e assinar o ponto. O sistema **guarda o certificado de forma cifrada** (o material
  secreto — a chave privada e a senha — **nunca** aparece em tela, log ou relatório) e mostra **apenas
  os dados públicos**: titular, validade, **dias para vencer** e situação (válido / a vencer / vencido).
  Quando a validade se aproxima (30 dias), o sistema **gera um alerta** automaticamente — para o
  certificado não vencer sem aviso e travar a emissão de notas.
- **Ver o catálogo de rotinas (jobs) e o histórico:** o sistema lista as **rotinas automáticas**
  (o robô do ponto, as varreduras de prazo de SLA, de licença a vencer, de contrato a vencer, de
  retenção no cofre e de validade do certificado) e o **histórico de cada execução**: quando começou e
  terminou, se **deu certo, falhou ou foi pulada** (porque já tinha rodado naquele período), e quantos
  itens tratou. Uma falha aparece **como falha** — o sistema **nunca** disfarça falha como sucesso.
- **Disparar uma rotina manualmente:** quando preciso, o TI pode **rodar uma rotina na hora** (por
  exemplo, refazer a varredura de licenças a vencer). O sistema garante que **só uma execução roda por
  vez** (se já estiver rodando, avisa que está **em execução**) e que **não duplica** o trabalho do
  período.
- **Consultar a auditoria de sistema:** um registro **somente-anexação** (não pode ser apagado nem
  reescrito) dos fatos de **segurança, integração e jobs** — quem fez, o quê, quando. Pode ser filtrado
  por **ator**, **tipo** e **período**. A auditoria guarda **só metadados** — **nunca** o material do
  certificado.

> Para quem é de TI: `GET /api/platform/certificate/status` (situação do certificado — **só
> metadados**), `POST /api/platform/certificate` (custodiar um certificado; o material é cifrado e
> nunca devolvido), `GET /api/platform/jobs` (catálogo), `GET /api/platform/jobs/runs?job=&status=`
> (histórico), `POST /api/platform/jobs/{name}/trigger` (disparo manual, responde 202; já em execução
> = 409), `GET /api/platform/audit?actor=&type=&from=&to=` (auditoria). **Onde** o certificado é
> custodiado de fato (cofre de nuvem/HSM) e se é A1 (arquivo) ou A3 (token) é decisão de infra do dono;
> hoje o material fica cifrado (AES-256-GCM) com a chave guardada **fora do banco**.

### Fase 8 — Entrar no sistema (login), papéis e auditoria de acesso

A partir desta versão o sistema tem **login de verdade**: cada pessoa entra com **usuário e senha**, e
**o que cada uma pode fazer depende do seu papel**. Antes, o sistema usava um "usuário de
desenvolvimento" com acesso a tudo — agora a segurança é real e **o servidor é a autoridade**: ele
confere o papel a cada ação (a tela nunca decide sozinha).

Como funciona, na prática:

- **Entrar (login).** Abra a tela **"Entrar"**, informe **usuário** e **senha** e confirme. Se estiver
  certo, você entra e seu nome aparece no topo, com o botão **"Sair"**. Se o usuário ou a senha
  estiverem errados, aparece uma mensagem **genérica** ("usuário ou senha inválidos") — de propósito, o
  sistema **não diz** se foi o usuário ou a senha que errou (segurança).
- **Sair.** O botão **"Sair"** no topo encerra a sessão e volta para a tela de login.
- **Papéis (o que cada um pode fazer).** Cada usuário tem um ou mais **papéis**: **Diretor**,
  **Financeiro**, **Operacional**, **TI**, **Curador de Políticas** e **Leitor**. As **ações sensíveis**
  exigem o papel certo, por exemplo:
  - **emitir nota fiscal de comissão** e **fechar o mês** → papel **Financeiro**;
  - **disparar uma rotina (job) / custodiar o certificado** → papel **TI**;
  - **emitir uma diretiva comercial** → papel **Diretor** (regra de política → Diretor ou Curador).
  Se você tentar uma ação sem o papel necessário, o sistema **recusa** (mensagem "acesso negado") e
  **registra a tentativa** na auditoria.
- **Auditoria de acesso.** Cada **login** e cada **recusa de acesso** ficam registrados (quem, qual
  ação, quando) — **sem nunca** guardar a senha ou o token. É a trilha que o time de TI/diretoria
  consulta para acompanhar acessos.

> **Atualizado na Fase 13:** a autenticação **própria** descrita acima (usuário/senha no próprio
> sistema) foi **substituída** pelo **login único corporativo (SSO) via provedor de identidade externo**
> — ver a seção *"Fase 13 — Entrar com SSO"* mais adiante. Os papéis, as permissões e a auditoria de
> acesso **continuam iguais**; só **mudou a forma de entrar**.

### Fase 8 — Fornecedores e contratos administrativos (luz, água, telefone, software)

Esta versão entrega o **balcão administrativo**: um cadastro **simples** dos **fornecedores
administrativos** da empresa (conta de luz, água, telefone, mensalidade de software/serviço) e dos
**contratos** que os sustentam — de modo que cada **despesa** caia no **lançamento certo** das Contas
a Pagar e **aponte o documento** que o fechamento do mês exige (a fatura, o RPA do autônomo, a NFS-e do
serviço). **Não** confundir com os **fornecedores de turismo/marcas** (esses ficam no "Portfólio").

Como funciona, na prática:

- **Cadastrar um fornecedor administrativo.** Informe o **tipo** (Concessionária de consumo, Software,
  Serviço ou Outros), o **nome** e, quando houver, o **CNPJ/CPF**. O fornecedor nasce **ativo**.
- **Registrar um contrato.** Para um fornecedor, registre a **vigência** (início e, se houver, fim), a
  **recorrência** (ex.: mensal), o **valor** e o **documento do contrato** (que já está guardado no
  cofre de documentos). Uma vigência incoerente (fim antes do início) é recusada.
- **Lançar uma despesa do mês.** Informe o **fornecedor**, o **mês** (ex.: 2026-06), o **valor** e o
  **tipo** da despesa. O sistema **cria automaticamente** o lançamento em **Contas a Pagar** e **diz
  quais documentos** você precisa anexar para o mês fechar:
  - **conta de consumo** (luz/água/telefone) → exige a **fatura** (e o comprovante na hora de pagar);
  - **serviço de autônomo** (pessoa física) → exige o **RPA**;
  - **software/serviço de empresa (PJ)** → exige a **NFS-e**;
  - **outros** → sem documento obrigatório no registro.
  Se você tentar lançar **a mesma despesa duas vezes** (mesmo fornecedor, mês e tipo), o sistema
  **recusa** (não duplica o lançamento).
- **A regra de ouro vale aqui também.** Enquanto a **fatura/documento** não estiver anexada, aquele
  lançamento **impede o mês de fechar** — exatamente como qualquer outra conta. O Admin **não** fecha o
  mês nem dispensa documento: ele só **gera o lançamento** e **aponta o documento**; quem trava é o
  Financeiro+Compliance.
- **Aviso de contrato a vencer.** O sistema **avisa** quando um contrato administrativo está perto do
  vencimento (até 30 dias antes) — é só um **alerta** para você renegociar/renovar, **nunca** bloqueia
  nada.
- **Quem pode mexer.** Como uma despesa administrativa vira uma obrigação financeira, **cadastrar
  fornecedor/contrato e lançar despesa** exigem o papel **Financeiro**; sem ele, o sistema **recusa** e
  **registra** a tentativa. Toda alteração fica **auditada** (o CNPJ/CPF nunca aparece inteiro nessa
  trilha — é dado pessoal).

> Para quem é de TI: `POST /api/admin/suppliers` (cadastrar fornecedor), `GET /api/admin/suppliers?type=&status=`
> (listar), `POST /api/admin/suppliers/{id}/contracts` (registrar contrato), `POST /api/admin/expenses`
> (lançar despesa → devolve o id do lançamento e os documentos exigidos), `POST /api/admin/contracts/flag-expiring`
> (varrer contratos a vencer). As escritas exigem o papel **Financeiro**. Compras completas
> (cotação/ordem de compra) **não** fazem parte deste módulo — se forem exigidas, **compra-se** um
> sistema de compras.

### Fase 10 — Nova experiência (telas profissionais, atalhos, tema e painel)

Esta fase **não muda regras de negócio**: ela renova **toda a aparência e a navegação** do sistema,
deixando-o com cara de ERP profissional. O que você passa a ver e usar:

- **Tela de entrada (login) renovada.** A mesma do login da Fase 8, agora com visual limpo e o campo de
  senha com botão para **mostrar/ocultar**. Ao entrar, você cai no **Painel**. Se a sessão tiver
  expirado e você tentar abrir uma tela direto, o sistema leva ao login e, depois de entrar, **volta
  para a tela que você queria**.
- **Sessão que se mantém.** Ao recarregar a página, o sistema **revalida sua sessão em silêncio** com o
  servidor (sem pedir senha de novo enquanto o acesso for válido). Quando o acesso expira de fato, ele
  pede o login outra vez. (Login único corporativo segue como próxima etapa — Fase 13.)
- **Layout SaaS (barra lateral + topo).** À **esquerda**, o menu de navegação (Painel, Contas, Câmbio,
  Cotações, Reservas, Conciliação, Saúde) com destaque para a tela atual. No **topo**, a busca de
  comandos, o **botão de tema** e o seu nome com **"Sair"**. Em telas pequenas (celular), o menu vira
  uma **gaveta** que abre pelo botão de menu.
- **Tema claro/escuro.** O botão de **sol/lua** no topo alterna entre **claro** e **escuro**. Sua
  escolha **fica salva** no navegador; na primeira vez, o sistema segue a preferência do seu
  computador.
- **Paleta de comandos (`Ctrl/Cmd + K`).** Aperte **Ctrl+K** (ou **⌘+K** no Mac) **de qualquer tela**
  para abrir uma caixa de busca: digite o nome de uma tela ou ação (ex.: "Reservas", "Tema", "Sair"),
  use **↑/↓** para escolher e **Enter** para executar. **Esc** fecha.
- **Atalhos de teclado.** Fora dos campos de texto, aperte **`g`** e depois a inicial de uma tela
  (ex.: **`g`** depois **`c`** → Contas) para navegar rápido. Aperte **`?`** para abrir a **ajuda de
  atalhos**. (Os atalhos de letra são ignorados enquanto você digita num formulário, para não atrapalhar.)
- **Aviso de alterações não salvas.** Se você começar a preencher um formulário (ex.: nova conta, fixar
  câmbio, override de cotação) e tentar **sair sem salvar**, o sistema **pergunta antes** se quer mesmo
  sair — evitando perder o que foi digitado.
- **Estados claros em todas as telas.** Cada tela mostra de forma consistente quando está
  **carregando**, quando **não há dados** ("nada para mostrar"), quando deu **erro** (com botão
  **"Tentar novamente"**) e quando você **não tem permissão** para ver aquilo (mensagem de permissão,
  em vez de um erro técnico).
- **Painel (dashboard) com indicadores.** A tela inicial agora é um **Painel** com cartões de resumo:
  **Contas** (total e ativas), **Reservas** (total, pendentes e confirmadas), **Conciliação** (casos,
  abertos, com divergência e o spread esperado somado) e **Câmbio** (a taxa congelada vigente).
  Clicar num cartão **leva para a tela** correspondente. Cada cartão carrega de forma independente e
  mostra seu próprio estado (carregando/erro/permissão).

> Para o time técnico: a base de telas passou a usar **PrimeNG 21 (tema Aura)** + **Tailwind v4** sobre
> **Angular 22**, mantendo todo texto no mecanismo de tradução (pt-BR/en). Os indicadores do Painel são
> calculados **no próprio navegador** a partir dos endpoints de lista que já existiam — **nenhum novo
> endpoint** foi criado no servidor.

### Fase 11 — Monitoramento e versão (para a operação/TI)

Esta fase **não muda nenhuma regra de negócio nem tela do operador comum**: ela dá ao time de **TI**
formas de **acompanhar a saúde e o uso do sistema** e de **saber qual versão está no ar**. É a base de
observabilidade do ERP (métricas, logs e painéis de monitoramento).

- **Qual versão está rodando.** O endereço **`/api/version`** (aberto, sem login) responde com a
  **versão** do sistema (ex.: `0.22.0`), o **código do commit** e a **data/hora do build**. Serve para
  conferir, num relance, exatamente o que está publicado (útil em suporte e em rodapé/“sobre”).
- **Saúde do sistema (sondas).** Os endereços **`/actuator/health`** (e os sub-itens de *liveness* e
  *readiness*) ficam **abertos** para as ferramentas de infra checarem se o sistema está vivo e pronto.
  A tela "Saúde" que o operador já conhecia continua funcionando.
- **Métricas (só para TI).** O endereço **`/actuator/prometheus`** publica as **métricas** do sistema
  (memória/CPU da aplicação, volume e tempo das chamadas, e **contadores de negócio** como reservas
  confirmadas, cotações compostas, NF de comissão emitidas, logins). Esse endereço é **restrito ao
  papel TI** — quem não tem o papel recebe **acesso negado**; quem não está autenticado, **não entra**.
- **Painéis no Grafana.** Junto com o sistema sobe (via `docker compose`) uma **stack de monitoramento**
  (Prometheus + Loki + Grafana). No **Grafana** (porta 3000), o time de TI vê o painel **“Acme Travel
  ERP — Backend Overview”** com memória, taxa de requisições, CPU e os eventos de negócio, além dos
  **logs** centralizados. Os logs do sistema, no container, saem em **formato estruturado (JSON)** com
  o **número de correlação** de cada requisição — e **nunca** trazem senha, token ou dado pessoal.

> Para o time de TI: para o Prometheus coletar as métricas (endereço protegido), gere um **token** de um
> usuário com papel **TI** e aponte-o no `infra/prometheus/scrape-token` (ver `infra/prometheus/README.md`).
> A stack de monitoramento é **configuração/infra** — não faz parte do build/test do backend.

### Fase 13 — Entrar com SSO (login único corporativo)

Esta versão troca o login por **SSO (login único corporativo)**: em vez de digitar usuário e senha
**dentro** do ERP, você entra pela **conta corporativa**, na **tela do provedor de identidade** (o
sistema usa o **Keycloak** no ambiente de desenvolvimento). É o mesmo padrão de "entrar com a conta da
empresa" que você já vê em muitos sistemas. **Os papéis, as permissões e a auditoria de acesso não
mudaram** — só **mudou a forma de entrar** (mais segura: o ERP **não guarda mais a sua senha**).

Como funciona, na prática:

- **Entrar.** Abra a tela **"Entrar"** e clique em **"Entrar com SSO"**. Você é levado à **tela de login
  do provedor** (a conta da empresa); informe **usuário e senha lá**. Dando certo, você volta ao sistema
  **já autenticado**, no **Painel**, com seu nome e o botão **"Sair"** no topo. Se a senha estiver errada,
  o **próprio provedor** mostra o erro (genérico) e você **não entra**.
- **Sessão sempre válida (renovação silenciosa).** Enquanto você usa o sistema, ele **renova sozinho**,
  em segundo plano, o seu acesso — sem pedir login de novo a cada pouco. Quando a sessão realmente
  termina, você é levado de volta ao login. (Antes, a sessão só era **revalidada**; agora ela é
  **renovada de verdade**.)
- **Sair.** O botão **"Sair"** encerra a sessão **no sistema e no provedor** (login único) e volta para a
  tela de entrar.
- **Papéis e permissões (iguais).** Continua valendo: **Diretor, Financeiro, Operacional, TI, Curador de
  Políticas e Leitor**; as ações sensíveis exigem o papel certo (emitir NF/fechar o mês → Financeiro;
  disparar rotina/custódia do certificado → TI; diretiva → Diretor). Sem o papel, **acesso negado** e a
  tentativa **fica na auditoria**. O **servidor continua sendo a autoridade** — a tela só reflete.

> Para o time técnico: o backend virou um **Resource Server OAuth2** que valida o **token do provedor
> externo (OIDC)** por **JWKS** (com rotação de chave). O endpoint próprio de login (`POST
> /api/identity/login`) foi **removido** — o login agora é no provedor. Seguem disponíveis
> `GET /api/identity/me`, `GET /api/identity/roles` e `GET /api/identity/access-audit`. O provedor de
> identidade **em produção** é decisão do dono (o Keycloak entregue serve dev/E2E, com um realm pronto:
> papéis, o aplicativo web e usuários de exemplo — **só** em desenvolvimento). Sobe junto com
> `docker compose up`.

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
- **Patrimônio interno (*Assets*):** os bens da própria empresa (equipamentos, licenças de software,
- **Banco de horas:** o saldo de horas de um período = horas trabalhadas − horas contratadas; positivo são horas extras, negativo são faltas (a lei admite saldo negativo). Aqui o sistema **mede** o saldo; pagar hora extra ou marcar folga é tarefa da folha.
- **Jornada (do período):** as horas que o colaborador efetivamente cumpriu no mês, montadas a partir do espelho operacional do ponto.
- **Divergência de ponto:** um aviso de marcação ímpar/faltante ou jornada incoerente, aberto para o RH tratar — o sistema nunca corrige sozinho.
- **Holerite:** o demonstrativo de pagamento; aqui ele é guardado no cofre (folha) com retenção de 5 anos e tratado como dado pessoal.
  outros bens), com custo, documento e ciclo de vida (ativo/baixado). É registro, não produto.
- **Baixa de um bem (*retire*):** marcar um bem como fora de uso, com motivo e auditoria (quem/quando).
  É definitiva.
- **Licença a vencer:** uma licença de software cuja **data de vencimento** está próxima (até 30 dias)
  ou já passou; o sistema **avisa** para renovar, sem bloquear nada.
- **Certificado e-CNPJ:** o certificado digital da empresa (ICP-Brasil), obrigatório para emitir nota
  fiscal e assinar o ponto. O sistema **guarda cifrado** e mostra **só os dados públicos** (validade,
  titular) — nunca a chave/senha.
- **Custódia (de segredo):** guardar um material secreto (certificado, senha) **cifrado**, acessível
  só pelo sistema, sem nunca expô-lo. A chave que cifra fica **fora do banco de dados**.
- **Rotina automática (*job*):** uma tarefa que o sistema roda sozinho de tempos em tempos (o robô do
  ponto, as varreduras de prazos). A **governança de jobs** garante que cada uma roda **uma por vez**,
  **sem duplicar** no período, com **histórico** de cada execução.
- **Auditoria de sistema:** o registro **somente-anexação** de fatos de segurança/integração/jobs
  (quem, o quê, quando) — para rastreabilidade. Guarda **só metadados**, nunca segredo.
- **Métrica:** um número que o sistema publica continuamente sobre si mesmo (memória, tempo de
  resposta, quantas reservas confirmadas etc.), para o time de TI acompanhar a saúde e o uso.
- **Prometheus / Grafana / Loki:** as ferramentas de monitoramento — o **Prometheus** coleta as
  métricas, o **Loki** junta os logs e o **Grafana** mostra tudo em painéis. Sobem junto com o sistema.
- **Log estruturado (JSON):** o registro de eventos do sistema em formato de máquina (JSON), com o
  número de correlação de cada requisição e **sem** senha/token/dado pessoal.
- **Endpoint de versão (`/api/version`):** o endereço (aberto) que informa qual versão/commit/data de
  build está rodando.

## 5. Histórico de versões do manual

| Versão | Fase | O que mudou no manual |
|---|---|---|
| 0.1.0 | 0 — Fundação | Primeira versão: visão geral, como acessar e a tela "Saúde do sistema". |
| 0.4.0 | 3 — Integração | Procedência da oferta (*Sourcing*): registro manual de oferta e cotação automática vinda do site de cotação (ramo INTEGRADO). |
| 0.5.0 | 4 — Cancelamento | Política de cancelamento por produto, multas por janela, no-show com dispensa por prova de voo, e a "armadilha do merchant" (duas obrigações que não se anulam na venda final). |
| 0.10.0 | 8 — Finance (full) | Contas a Pagar/Receber e o fechamento mensal com a "regra de ouro" (não fecha sem a nota); **lançamentos automáticos** a partir de cancelamentos e no-show das reservas (uma vez só, sem duplicar); balancete do período por moeda. |
| 0.13.0 | 8 — AfterSales | Pós-venda: chamados (reclamação/alteração/cancelamento/reembolso/informação) ligados à reserva; prazos de **SLA governados** (24h/72h/48h, ajustáveis por diretiva) com alerta de violação que **não trava**; resolução que **encaminha** reembolso ao Payout (uma vez, sem cancelar a obrigação do fornecedor) e cancelamento à reserva; "custo de servir" por chamado. |
| 0.14.0 | 8 — Marketing | Marketing B2B com **consentimento LGPD** obrigatório: registrar/revogar/consultar consentimento (histórico preservado); **segmento** por dados existentes com **prévia** de alcance; **campanha** que **só envia para quem consentiu** (suprimidos contados, sem envio duplicado) via provedor de newsletter; **atribuição** código→reserva que vira **sinal de conversão** para o DSS; **exclusão LGPD** que apaga o dado de marketing mas preserva a prova de revogação. |
| 0.15.0 | 8 — Portfólio | Representação: cadastrar/desativar/listar **marcas representadas** (identificador único); registrar **contratos de representação** (vigência + documento no cofre), com **alerta** (não bloqueio) para venda sem contrato vigente e **aviso de contrato a vencer** (até 30 dias); definir **metas por marca** (volume ou receita) e acompanhar o **realizado vs meta** a partir das **vendas confirmadas** da marca. Não mexe em preço nem comissão. |
| 0.16.0 | 8 — Patrimônio (Assets) | Registro do **patrimônio interno** (equipamentos, licenças de software, outros bens): cadastrar com **tipo/identificação/data/custo** e vínculos por valor ao **documento** (cofre) e ao **lançamento** (financeiro); licença de software exige **data de vencimento**; **baixa** auditada e definitiva (com motivo); listar/filtrar por tipo/situação e por **licenças a vencer**; **aviso** (uma vez por licença) de licença a vencer (até 30 dias). É patrimônio, não produto — não entra em preço/venda; gestão plena de ativos = comprar. |
| 0.17.0 | 8 — Pessoas (People) | RH mínimo sobre o ponto: cadastrar **colaborador** (identificador único, admissão, **jornada contratada** HH:mm, situação ativo/afastado/desligado); **processar a jornada** de um período a partir do espelho operacional e calcular o **banco de horas** (saldo = trabalhado − contratado; extras/faltas, saldo negativo permitido) — só **mede**, não é folha; **consultar** jornada e banco de horas; **divergências** (marcação ímpar/faltante/jornada incoerente) viram **aviso** numa fila de tratamento, **sem correção automática**; **arquivar o holerite** no cofre (folha, retenção 5 anos, dado pessoal). Folha pesada (eSocial/FGTS/13º) = comprar/integrar. |
| 0.18.0 | 8 — Plataforma (Platform) | Infra de TI: **custódia do certificado e-CNPJ** com o material **cifrado** (a chave/senha nunca aparece) — a tela mostra **só metadados** (titular, validade, dias para vencer, situação) e o sistema **alerta** quando o certificado está a vencer (30 dias); **governança de jobs** — catálogo e **histórico** das rotinas automáticas, **disparo manual** (só uma execução por vez = 409 se já roda; sem duplicar no período), e a falha aparece **como falha** (nunca disfarçada de sucesso); **auditoria de sistema** somente-anexação de eventos de segurança/integração/jobs (quem/o quê/quando), filtrável, **só metadados** (nunca o segredo). |
| 0.19.0 | 8 — Identidade (Identity) | **Login de verdade**: entrar com **usuário e senha** (tela "Entrar"), nome e "Sair" no topo; erro **genérico** sem revelar se o usuário existe. **Papéis e permissões** (Diretor/Financeiro/Operacional/TI/Curador/Leitor): as **ações sensíveis exigem o papel** (emitir NF e fechar o mês → Financeiro; disparar job/custódia do certificado → TI; diretiva → Diretor) — sem o papel, **acesso negado** registrado. **Auditoria de acesso** (login e recusas; quem/ação/quando, **sem senha/token**). O **servidor é a autoridade** — a tela só reflete. Login único corporativo (provedor externo) = próxima etapa (Fase 13). |
| 0.20.0 | 8 — Admin (fornecedores/contratos administrativos) | **Balcão administrativo**: cadastro **enxuto** de **fornecedores administrativos** (luz, água, telefone, software/serviço, autônomo) e seus **contratos** (vigência, recorrência, valor, documento). **Lançar a despesa do mês** cria automaticamente o lançamento em **Contas a Pagar** com o tipo certo e **aponta os documentos exigidos** (conta de consumo → fatura; autônomo → RPA; serviço PJ → NFS-e); **idempotente** (não duplica). **A regra de ouro vale aqui**: despesa **sem o documento impede o mês de fechar**. **Aviso de contrato a vencer** (até 30 dias, só alerta). **Cadastrar/lançar exige o papel Financeiro**; toda alteração **auditada** (CNPJ/CPF nunca aparece inteiro). Compras completas (cotação/ordem) = comprar se exigido. **Fim do bloco 8x.** |
| 0.21.0 | 10 — UX & Frontend profissional | **Nova experiência** (sem mudar regras): **layout SaaS** (barra lateral + topo + gaveta no celular); **tema claro/escuro** com a escolha salva; **paleta de comandos `Ctrl/Cmd+K`** + atalhos (`g`+tecla, `?` ajuda); **login** renovado com **revalidação silenciosa da sessão** (volta para a tela pretendida); **aviso de alterações não salvas** ao sair de um formulário; **estados reais** (carregando/vazio/erro/permissão) em todas as telas; **Painel (dashboard)** com indicadores de Contas/Reservas/Conciliação/Câmbio calculados no navegador. Telas com **PrimeNG 21 + Tailwind v4** sobre Angular 22; **nenhum endpoint novo** no servidor. Gradua a DL-0003. |
| 0.22.0 | 11 — Observabilidade & monitoramento | **Monitoramento e versão (para a operação/TI)**, sem mudar regras de negócio: endereço **`/api/version`** (aberto) com versão/commit/data do build; **sondas de saúde** (`/actuator/health`) abertas; **métricas** (`/actuator/prometheus`) — técnicas (memória/CPU/requisições) e de **negócio** (reservas/cotações/NF/logins) — **restritas ao papel TI**; **stack de monitoramento** (Prometheus + Loki + Grafana) que sobe junto via `docker compose`, com painel "Acme Travel ERP — Backend Overview" e logs centralizados; **logs em JSON** com número de correlação e **sem** segredo/dado pessoal. |
| 0.23.0 | 13 — Identidade/AuthZ profissional (gradua a SPEC-0024) | **Login único corporativo (SSO)**: entrar passa a ser pela **conta da empresa** na tela do **provedor de identidade** (Keycloak no dev), com o botão **"Entrar com SSO"** e **renovação silenciosa de sessão de verdade**; o ERP **não guarda mais senha**. **Papéis, permissões e auditoria de acesso continuam iguais** — só mudou a forma de entrar. **Mudança incompatível:** o login próprio antigo (`POST /api/identity/login`) foi **removido** (login agora é no provedor). |

> Observação: o manual foca nas fatias com tela/jornada para o usuário; capacidades internas das
> Fases 1, 2 e 5–8a aparecem aqui conforme ganham uso direto pelo operador.
>
> Fase 15 — Docs bilíngues (chore, sem mudança de versão): a cobertura bilíngue, antes só do manual,
> passou a incluir o **README** (`README.en-US.md`) e o **changelog consolidado en-US**
> (`docs/release-notes/CHANGELOG.en-US.md`). Relatórios técnicos seguem só em pt-BR (Regra Zero).
