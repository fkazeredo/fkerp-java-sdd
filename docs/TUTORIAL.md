# ERP Acme Travel — Tutorial de Construção com o Claude Code

> O **como fazer** de cada fatia. O `ROADMAP.md` diz *o que* construir e em que ordem; as specs em
> `docs/specs` dizem *o que cada fatia precisa satisfazer*; **este tutorial é o laço que você repete
> em toda sessão** com o Claude Code para transformar uma spec num entregável de ponta a ponta,
> sempre começando pelos testes. Convenção do projeto: **prosa em pt-BR, código em inglês**.

---

## 1. Modelo mental: quem manda em quê

São três papéis, e a ordem de autoridade importa quando algo conflita:

1. **A arquitetura e a Regra Zero** (`CLAUDE.md`, `architecture/*.md`). É a lei. O Claude Code
   **não** pode contrariar — nem a seu pedido, nem para "ir mais rápido". Se uma instrução sua
   bate de frente com a arquitetura, o esperado é o Claude Code te avisar, não obedecer.
2. **A spec da fatia** (`docs/specs/000X-...md`). É o contrato do que está sendo entregue agora:
   regras de negócio, exemplos, critérios de aceite, *Open Questions* e *Out of Scope*.
3. **Você, o dono** (product owner + tech lead). Decide as perguntas em aberto, aprova o plano,
   revisa o PR. O Claude Code implementa; **quem decide produto é você**.

> Regra de ouro do laço: **o Claude Code nunca inventa regra de negócio**. Quando a spec tem uma
> *Open Question* que afeta o código, ele **para e pergunta** (seção 5). Decisão inventada é dívida
> e retrabalho.

---

## 2. Preparação (uma única vez)

1. **Suba o esqueleto.** A primeira sessão é a SPEC-0001 (*walking skeleton*). Ela não tem regra de
   negócio — é o projeto que **sobe, conecta no Postgres, tem `/api/system/health`, tela Angular
   mínima, ArchUnit + Spring Modulith verdes e CI**. Sem ela, nenhuma fatia de negócio tem onde
   pousar. Rode o laço da seção 3 com a SPEC-0001 como qualquer outra (só que sem *Open Questions*
   de negócio).
2. **Ambiente local.** Tenha **JDK**, **Docker** (os testes de integração usam Testcontainers — o
   Docker precisa estar de pé) e o **`mvnw`** do projeto. Nunca um Maven global: sempre `./mvnw`.
3. **Aponte o Claude Code para o contexto certo.** No começo de cada sessão, deixe explícito que ele
   deve seguir, nesta ordem: `CLAUDE.md` → `architecture/` → a spec da fatia. Ex. de abertura:
   > "Leia `CLAUDE.md` e os arquivos em `architecture/`. Vamos implementar **somente** a
   > `docs/specs/0002-accounts-commercial-account.md`, **teste primeiro**, respeitando as fronteiras
   > de módulo. Antes de escrever código, entre em **plan mode** e me proponha o plano."
4. **Trabalhe em branch + PR pequeno por fatia.** Uma fatia = um branch = um PR revisável. Nada de
   um PR gigante com três fatias.

---

## 3. O laço de uma fatia (repita isto sempre)

Este é o coração do tutorial. Toda fatia — da SPEC-0002 em diante — segue **os mesmos sete passos**:

```txt
0. PERGUNTAS   Resolver com o dono as Open Questions que afetam o código  → registrar na spec
1. PLAN        Claude Code em plan mode: plano da fatia (migração→domínio→app→API→tela)  → você aprova
2. RED         Escrever o teste de aceite/integração a partir dos exemplos da spec  → ele FALHA
3. SKELETON    Criar só o esqueleto da fatia (tipos, portas, migração vazia) p/ compilar
4. GREEN       Implementar o mínimo até o teste passar  → verde
5. REFACTOR    Limpar com os testes te protegendo (nomes, duplicação, fronteiras)
6. GATES + DoD ArchUnit/Modulith/Spotless verdes + checklist de pronto  → commit/PR convencional
```

**Por que nessa ordem.** O teste primeiro (passo 2) trava o comportamento na linguagem da spec antes
de existir implementação que te enviese. O esqueleto (3) existe só para o vermelho ser "falha de
asserção", não "não compila". O verde mínimo (4) honra *current need over speculative need*. Os
*gates* (6) são inegociáveis — **se ArchUnit/Modulith reclamam, conserte o código, nunca afrouxe a
regra**.

### Definition of Done (o checklist do passo 6)

Uma fatia só está pronta quando **tudo** abaixo é verdade:

- [ ] Todos os critérios de aceite da spec viraram teste **e passam**.
- [ ] `./mvnw verify` **verde** — inclui ArchUnit e Spring Modulith (fronteiras intactas).
- [ ] Migração Flyway nova e **idempotente** (`V{n}__...sql`); nada de editar migração já aplicada.
- [ ] Erros usam `DomainException` com `code == chave i18n`; mensagens em `messages_pt_BR.properties`.
- [ ] Sem exceção crua de banco vazando (índice único → erro de negócio traduzido).
- [ ] OpenAPI atualizada; enums com valores externos explícitos.
- [ ] Observabilidade da spec atendida (evento de negócio logado, dado pessoal mascarado, correlation id).
- [ ] Se houve costura adiada: **mock/stub rastreável** apontando a spec futura — não lógica falsa.
- [ ] Spotless/format aplicado.
- [ ] Commit no padrão **Conventional Commits**; PR pequeno e descritivo.

---

## 4. Exemplo completo: Fatia 1 — Accounts (SPEC-0002), com prompts reais

Abaixo, a fatia inteira traduzida em **cinco prompts** para o Claude Code. Eles seguem os sete
passos da seção 3. Adapte o texto; o que importa é a **sequência e as âncoras** (teste primeiro,
escopo travado na spec, gates no fim).

### Prompt A — contexto + plano (passos 0 e 1)

> "Leia `CLAUDE.md`, `architecture/` e `docs/specs/0002-accounts-commercial-account.md`. Vamos
> implementar **apenas** essa spec, **teste primeiro**. Antes de qualquer código, entre em **plan
> mode**: liste a migração, o value object `Document`, o agregado `Account`, a porta de aplicação,
> os endpoints (`POST /api/accounts`, `GET /api/accounts/{id}`, `GET /api/accounts?...`) e a tela
> Angular mínima. **Não** implemente carteira, transições de status, nem validação externa de
> CADASTUR/IATA — está em *Out of Scope*. As *Open Questions* (cadastros obrigatórios por tipo,
> carteira, transições) ficam fora desta fatia; confirme que concorda antes de prosseguir."

Você lê o plano, ajusta o que quiser, e só então libera o próximo passo.

### Prompt B — RED: os testes primeiro (passo 2)

> "Agora escreva **somente os testes**, que devem **falhar** por ora:
> 1. **Unitário** do `Document`: tabela de CNPJ/CPF com DV válido e inválido, incluindo dígitos
>    repetidos como inválidos (BR2).
> 2. **Integração com Testcontainers (Postgres)**: `POST /api/accounts` com CNPJ válido → 201 e
>    `status=ACTIVE`; documento com DV inválido → 400 `account.document.invalid` apontando
>    `documentNumber`; documento duplicado → 409 `account.document.duplicate`; `GET` por id
>    inexistente → 404 `account.not-found`; listagem paginada com filtro por `status`.
> Use os exemplos de request/response da spec como fonte da verdade. Rode e me mostre a **falha
> vermelha** (deve ser asserção/rota inexistente, não erro de compilação irrecuperável)."

Se o vermelho vier por motivo errado (ex.: nem compila por falta de tipo), é sinal de que falta o
esqueleto — peça o passo 3 antes de tentar o verde.

### Prompt C — GREEN: o mínimo para passar (passos 3 e 4)

> "Implemente o **mínimo** para os testes ficarem verdes:
> - `Document` como **value object** que valida os dígitos na construção (a invariante BR2 mora no
>   domínio, não no controller).
> - Agregado `Account` (campos da spec, `status` nasce `ACTIVE`, `@Version`).
> - Migração `V2__create_accounts.sql` com a tabela e o **índice único** `ux_accounts_document
>   (legal_type, document_number)`.
> - Porta de aplicação + adaptador REST dos três endpoints; `PageResponse` na listagem.
> - Tradução do índice único para `account.document.duplicate` (nada de exceção crua de banco
>   vazando — `persistence.md`).
> - Chaves i18n em `messages_pt_BR.properties` (e fallback `messages.properties`).
> - Emita `AccountRegistered` como evento **in-process** (`{accountId, legalType, occurredAt}`),
>   logado **sem o número completo do documento** (LGPD).
> Não acrescente nada além do escopo da spec. Rode `./mvnw verify` e me mostre tudo **verde**."

### Prompt D — REFACTOR + gates (passos 5 e 6, parte arquitetural)

> "Com os testes verdes te protegendo, **refatore**: nomes claros, sem duplicação, sem regra de
> negócio vazando para o controller. Depois rode os **gates**: ArchUnit e Spring Modulith para
> confirmar que `accounts` não furou fronteira de módulo, e o Spotless/format. **Se algum gate
> falhar, conserte o código — não relaxe a regra.** Mostre o resultado final."

### Prompt E — Definition of Done + commit (passo 6, fechamento)

> "Rode o **checklist de Definition of Done** do `TUTORIAL.md` item a item e me diga o estado de
> cada um. Atualize a OpenAPI. Se algum item não passar, corrija. No fim, faça **um** commit em
> **Conventional Commits** (ex.: `feat(accounts): cadastro e consulta de Conta Comercial`) e prepare
> um PR pequeno com um resumo do que entrou e do que ficou explicitamente fora."

Pronto: a Fatia 1 é um **entregável de ponta a ponta** — migração + domínio + API + tela, nascida
dos testes, com fronteiras intactas. As fatias seguintes (Exchange, Commissioning, Quoting…) são
**o mesmo laço** com a spec correspondente.

> **Dica de Quoting (Fatia 4).** Quando chegar na keystone, a costura com `CommercialPolicy` (o
> *markup*) entra como **stub rastreável** (`MarkupProvider`, `source=SYSTEM_DEFAULT`) que aponta a
> spec futura — exatamente o padrão de `simulation-and-mocking.md`. E **antes** de codar, resolva
> com o dono a *Open Question* da **fórmula de preço** (markup-sobre-base × repasse de tarifa; moeda
> da base comissionável). Isso é decisão de negócio: não deixe o Claude Code chutar.

---

## 5. Quando você bate numa *Open Question*

Toda spec tem uma seção *Open Questions*. Quando uma delas **afeta o código desta fatia**, o laço
manda **parar**:

1. **Pare.** Não deixe o Claude Code escolher por conta — ele foi instruído a perguntar, não a
   inventar (`CLAUDE.md`).
2. **Decida com quem é dono do negócio.** Ex.: "override de fornecedor é % fixo ou faixas?" → no v1
   é **fixo** (decisão já registrada no ADR 0014/redesenho). Para acelerar, o `ROADMAP.md` tem a
   seção **"Recomendações para as Open Questions"**: uma sugestão de partida (com justificativa) para
   cada pergunta. Use como **ponto de partida** da sua decisão — não como decisão automática.
3. **Registre a decisão na spec** (mova de *Open Questions* para *Business Rules*, com data/quem
   decidiu) **antes** de escrever o teste. A spec é documento vivo.
4. **Aí sim** entre no RED. O teste agora encosta numa regra **decidida**, não num palpite.

Se a pergunta **não** afeta esta fatia (é de uma fatia futura), apenas a deixe anotada e siga — não
puxe escopo para frente.

---

## 6. Micro-exemplo de regressão (RED→GREEN→REFACTOR em pequeno)

O laço não é só para a fatia inteira; é o reflexo para **cada regra**. A unicidade de documento da
Account (BR3) ilustra:

```txt
RED       Teste: cadastrar dois Accounts com o MESMO (legalType, documentNumber)
          → espera 409 account.document.duplicate.   (FALHA: hoje cria os dois)
GREEN     Índice único ux_accounts_document + tradução da violação para o erro de negócio.
          (PASSA)
REFACTOR  Centralizar a tradução do erro num único ponto (sem try/catch espalhado),
          mensagem em messages_pt_BR.properties.   (segue verde)
```

Toda vez que um bug aparecer depois, o caminho é o mesmo: **primeiro** um teste que falha
reproduzindo o bug, **depois** o conserto que o deixa verde. Assim a regressão fica trancada.

---

## 7. Cadência e dicas práticas

- **Uma fatia por vez.** Termine o laço inteiro (até o PR) antes de abrir a próxima. Não comece
  Exchange no meio de Accounts.
- **PRs pequenos.** Mais fáceis de revisar e de reverter. Uma fatia raramente precisa ser um PR
  gigante.
- **ArchUnit/Modulith sempre verdes.** São a sua cerca contra o monólito virar bola de lama. Gate
  vermelho = conserto no código, nunca regra afrouxada.
- **Specs são vivas.** Decidiu uma *Open Question*? Edite a spec na mesma sessão. A spec é a verdade
  da fatia; mantê-la atual é parte do trabalho.
- **Escopo esbarrou em algo de fora?** A resposta é **mock rastreável** apontando a spec futura
  (`simulation-and-mocking.md`), **não** lógica falsa em produção e **não** puxar a outra fatia para
  agora.
- **Sempre `./mvnw`.** E **Docker de pé** para os Testcontainers — se a integração "não conecta", o
  primeiro suspeito é o Docker parado.
- **Comece pelo que tem menos pergunta em aberto.** É por isso que o roadmap arranca pelo núcleo
  **MANUAL** (Accounts/Exchange/Commissioning/Quoting): caminho seguro, valor cedo.
- **Demonstre ao fim de cada fatia.** Ela é "de ponta a ponta" justamente para você poder rodar a
  tela/endpoint e ver funcionando — use isso como aceite real, além do `verify` verde.

---

## 8. Resumo de uma página

```txt
SETUP (1x):  rode a SPEC-0001 (esqueleto sobe + health + CI + arch verdes).
             JDK + Docker + ./mvnw prontos.

POR FATIA (SPEC-0002 em diante), sempre o mesmo laço:
  0 Perguntas → decidir Open Questions com o dono, registrar na spec
  1 Plan      → Claude Code propõe plano, você aprova
  2 RED       → testes de aceite/integração a partir da spec  (falham)
  3 Skeleton  → tipos/portas/migração p/ compilar
  4 GREEN     → mínimo até passar
  5 Refactor  → limpar com os testes protegendo
  6 Gates+DoD → ArchUnit/Modulith/Spotless verdes + checklist + commit convencional

NUNCA:  inventar regra de negócio • afrouxar gate de arquitetura • lógica falsa em produção •
        editar migração já aplicada • puxar escopo de fatia futura.
SEMPRE: teste primeiro • escopo travado na spec • costura adiada vira mock rastreável •
        spec é documento vivo • um PR pequeno por fatia.
```

> Próxima ação concreta: abra a **SPEC-0001** no Claude Code e rode o laço (setup). Em seguida, a
> **SPEC-0002 (Accounts)** com os cinco prompts da seção 4. A partir daí, é repetir.
