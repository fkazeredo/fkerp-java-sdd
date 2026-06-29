# ADR 0015: Versionamento semântico (SemVer) e gerência de configuração de releases

## Status

Accepted

## Context

O projeto já produz versões (`0.1.0` na Fase 0): a versão vive no `backend/pom.xml`
(`<version>`), é espelhada por uma **tag git**, por um arquivo em `docs/release-notes/<versão>.md`
e referenciada na `docs/ROADMAP-STATUS.md`. As regras de delivery (`architecture/delivery.md`)
mencionam apenas "Semantic versioning when applicable; releases tagged from `main`" — uma diretriz
solta, sem critério escrito de **quando** cada dígito muda, **quem** é a fonte da verdade e **como**
o número se relaciona com as fases do ROADMAP e com os Conventional Commits.

Sem esse critério explícito, o número da versão vira decisão ad-hoc a cada release: dois
desenvolvedores (ou duas sessões autônomas) podem incrementar de formas diferentes, e o consumidor
do artefato não consegue inferir o **risco de atualizar** só pelo número. Versionar é parte da
**gerência de configuração** do produto: o identificador de versão precisa ter significado estável
e auditável.

O dono pediu para padronizar o versionamento como **SemVer** (Semantic Versioning) e formalizar a
regra em ADR. Este ADR registra a política e passa a ser a autoridade sobre o assunto (acima do
texto genérico de `delivery.md`, que passa a apontar para cá).

## Decision

Adotar o **Semantic Versioning 2.0.0** (<https://semver.org>) como política oficial de
versionamento do produto, no formato `MAJOR.MINOR.PATCH` (ex.: `1.34.26`).

### 1. Significado de cada posição

- **MAJOR** (1.x.x): mudanças **estruturais profundas** que **quebram a compatibilidade** com
  versões anteriores (mudanças disruptivas). Quem usava a versão anterior pode parar de funcionar
  sem adaptar o código/contrato. Ex.: remover/renomear endpoint ou campo de contrato, mudar formato
  de erro, quebrar contrato de evento publicado, migração de schema incompatível com o consumidor.
- **MINOR** (x.34.x): **novas funcionalidades** introduzidas de forma **100% retrocompatível**. O
  sistema ganha recursos novos, e tudo que funcionava na MINOR anterior continua funcionando. Ex.:
  novo módulo/fatia do ROADMAP, novo endpoint, novo campo opcional, nova capacidade.
- **PATCH** (x.x.26): **correções de bugs** e otimizações internas, sem novas funções e sem quebrar
  nada. Serve para deixar a versão estável e segura. Ex.: `fix:` de comportamento, correção de
  migração ainda-não-aplicada, ajuste de mensagem i18n, melhoria interna sem efeito de contrato.

### 2. Regras de contagem (reset/zeragem)

- Ao incrementar **MINOR**, o **PATCH volta a zero**: `1.34.26 → 1.35.0`.
- Ao incrementar **MAJOR**, **MINOR e PATCH voltam a zero**: `1.34.26 → 2.0.0`.
- Os números **não têm limite** e **não carregam "vai-um"**: depois de `1.9.0` vem `1.10.0` (não
  `2.0.0`); cada posição é um inteiro independente, comparado numericamente (não lexicograficamente).

### 3. Fase de desenvolvimento inicial (`0.y.z`)

Enquanto a versão MAJOR for **zero** (`0.y.z`), o produto está em **desenvolvimento inicial**: é
considerado instável e **qualquer mudança pode quebrar** — as garantias estritas de
retrocompatibilidade do SemVer só **passam a valer a partir de `1.0.0`**. Mesmo assim, **adotamos
desde já a disciplina de incremento** (MINOR para capacidade nova, PATCH para correção), para que o
histórico seja legível e a transição para `1.0.0` seja natural.

A subida para **`1.0.0`** é uma **decisão do dono**: acontece quando os contratos públicos (APIs
REST, formato de erro, eventos publicados, schema) são declarados **estáveis / prontos para
produção**. Não é automática.

### 4. Mapeamento com o ROADMAP e os Conventional Commits

- Cada **entrega de fase** do ROADMAP que agrega capacidade nova e retrocompatível corta uma
  **MINOR** durante a fase `0.y`: Fase 0 → `0.1.0` (entregue); Fase 1 → `0.2.0`; Fase 2 → `0.3.0`;
  e assim por diante. Correções pontuais entre fases cortam **PATCH** (ex.: `0.2.1`).
- A natureza do conjunto de commits sugere o incremento (Conventional Commits, `delivery.md`):
  predomínio de `feat:` retrocompatível ⇒ **MINOR**; só `fix:`/`refactor:`/`docs:` sem efeito de
  contrato ⇒ **PATCH**; qualquer **breaking change** (marcado `!` ou rodapé `BREAKING CHANGE:`) ⇒
  **MAJOR** (ou MINOR enquanto `0.y`, por estarmos em desenvolvimento inicial — mas o breaking
  **deve** ser destacado no release note).

### 5. Fonte da verdade e artefatos de configuração

- **Fonte da verdade do número:** `backend/pom.xml` `<version>`. Tudo mais espelha esse valor.
- **Tag git:** uma tag por release, cortada de `main` no fim da fase (gitflow, `delivery.md`).
  Mantemos o formato **sem prefixo `v`** já em uso (`0.1.0`), por consistência com o que existe.
- **Release note:** `docs/release-notes/<versão>.md` (mesmo número), a partir de `_TEMPLATE.md`.
- **Controle de fases:** `docs/ROADMAP-STATUS.md` registra a versão entregue por fase.
- Versões **pré-release/build metadata** do SemVer (`-rc.1`, `+build`) **não** são usadas por
  padrão; entram só se uma necessidade concreta aparecer (e então com nota no release).

Este ADR **não** altera a versão atual: `0.1.0` permanece válida; a próxima entrega (Fase 1)
seguirá a política e será `0.2.0`.

## Consequences

**Positivas**

- O número de versão passa a **comunicar risco de atualização** sem ler o changelog: PATCH é
  seguro, MINOR adiciona sem quebrar, MAJOR exige adaptação.
- Critério **determinístico** de incremento: sessões autônomas (ex.: `docs/RUN-PHASE.md`) e humanos
  versionam igual, sem decisão ad-hoc.
- Alinha pom.xml, tag git, release-notes e ROADMAP-STATUS num só identificador auditável — boa
  **gerência de configuração**.

**Negativas / custo**

- Exige **disciplina de contrato**: para honrar MINOR retrocompatível é preciso saber o que é
  breaking (endpoint, DTO, enum, formato de erro, evento, schema). Isso já é cobrado em
  `modules-and-apis.md`, mas agora tem consequência direta no número.
- Em `0.y.z` a garantia formal é fraca (tudo pode quebrar); a disciplina de incremento é convenção
  nossa, não obrigação do SemVer — precisa ser sustentada por revisão.
- Decidir a subida para `1.0.0` continua sendo julgamento do dono (não há gatilho automático).

## Alternatives Considered

- **Manter a diretriz solta de `delivery.md`** ("semantic versioning when applicable"). Rejeitado:
  sem critério escrito de quando cada dígito muda, o versionamento vira ad-hoc e não dá para
  automatizar nem auditar.
- **CalVer (versionamento por data, ex. `2026.06`).** Rejeitado: comunica *quando* foi lançado, mas
  **não** comunica risco de compatibilidade — que é exatamente o que o consumidor de um ERP modular
  com contratos (APIs/eventos) precisa saber ao atualizar.
- **Número de build sequencial único (`#142`).** Rejeitado: opaco; não distingue correção de
  funcionalidade de quebra; inútil para decidir atualização.
- **Acoplar a versão 1:1 ao número da fase do ROADMAP.** Rejeitado: fases entregam capacidade
  (MINOR), mas correções entre fases (PATCH) e quebras (MAJOR) não cabem nessa contagem; o SemVer
  expressa as três dimensões, a fase expressa só uma.
