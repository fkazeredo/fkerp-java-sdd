# ADR 0016: Achatar os pacotes `internal` do domain; encapsular por `@ModuleInternal` + ArchUnit

## Status

Accepted (Fase 9 — ADR + chore estrutural; refina ADR 0012 e ADR 0014; sem mudança de contrato)

## Context

O layout do domain usava `com.fksoft.domain.<módulo>.internal.*` para esconder os tipos
module-private (entidades, repositórios, listeners, codecs) de outros módulos. Essa é a **convenção
de visibilidade do Go** (`internal/` é especial para o compilador Go), trazida na origem do produto.
Em Java/Spring Modulith ela é apenas *folclore de pasta*: quem realmente esconde o subpacote
`internal` é o Spring Modulith (por padrão, só a base package de um módulo é a API; subpacotes são
module-private).

O `docs/ROADMAP.md` (Fase 9) decide **achatar**: mover tudo de `…/<módulo>/internal/` para
`…/<módulo>/`, em main e test, nos módulos de domínio, e **preservar a encapsulação via
`@NamedInterface`/ArchUnit**. É herança Go ao contrário: pacotes planos, encapsulação imposta por
**tooling**, não por pasta.

O fato técnico que governa o desenho: **ao achatar, a base package vira a *unnamed named interface*
do Spring Modulith** — toda classe pública nela passa a ser API exposta. O Modulith **deixa de
esconder** os tipos que antes estavam em `internal`. Se nada substituir esse esconde-automático, a
encapsulação some silenciosamente. O ponto da Fase 9 é **mover** o mecanismo de fronteira, não
removê-lo (CLAUDE.md Regra 5 — nunca afrouxar um portão).

Diagnóstico do código atual (pesquisa antes da decisão): nenhum import cross-module de `*.internal.*`
existe hoje (o Modulith já barra); as únicas referências cross-pacote a `internal` são o `*Service`
do próprio módulo, os testes do próprio módulo, e o adapter `infra.security` lendo
`identity.internal.IdentityUser/Repository` — acesso de infra à persistência do módulo, **permitido**
pela arquitetura (ADR 0010/0012: `infra` pode operar a persistência do módulo que serve).

## Decision

1. **Achatar.** Remover todo subpacote `internal` sob `com.fksoft.domain` (main e test): os tipos
   passam para a base package do módulo. Os tipos que eram package-private dentro de `internal`
   continuam package-private na base package (o compilador segue escondendo-os). Movimento puramente
   estrutural: sem mudança de comportamento, JSON, evento publicado, schema ou chave i18n. (O FQCN
   dos tipos ex-internal muda de pacote — eles não fazem parte de nenhum contrato de fio.)

2. **Marcador `@com.fksoft.domain.ModuleInternal`.** Um kernel não-módulo (como `domain.error` e
   `domain.money`), `@Target(TYPE)` e `@Retention(CLASS)` (visível ao bytecode do ArchUnit, invisível
   em runtime). Anotado em **cada tipo público** que era `internal`. É o sinal explícito que
   substitui o marcador-de-pasta `.internal`: declara "isto é miolo do módulo, não API".

3. **Encapsulação por ArchUnit (as novas garras).** Nova regra
   `MODULE_INTERNAL_TYPES_ARE_NOT_VISIBLE_ACROSS_MODULES`: **nenhuma** classe de **outro** módulo de
   domínio pode depender de um tipo `@ModuleInternal`. Exceções deliberadas: o próprio módulo
   (inclui seus testes — mesmo prefixo `com.fksoft.<…>.<módulo>`), e `..infra..` (o adapter pode
   operar a persistência do módulo — preserva `infra.security` → `identity`). Esta regra recria, para
   os 22 módulos, exatamente o que o Modulith fazia com o `internal`. Os predicados de fronteira já
   existentes (Intelligence "advises never commands", Portfolio "references never commands", Platform
   "orchestrates never owns") trocam o teste `pkg.contains(".internal")` por
   `isAnnotatedWith(ModuleInternal.class)` — mesma força, sinal novo.

4. **Teeth preservadas.** `ArchitectureRulesHaveTeethTest` ganha um caso que prova que a regra geral
   **falha** quando uma classe de outro módulo toca um tipo `@ModuleInternal` (fixture dedicado fora
   de `com.fksoft`). Os teeth de Intelligence/Platform (via `BookingService`) seguem válidos.

5. **Spring Modulith mantido.** `@ApplicationModule` e `ModularityTests.verify()` permanecem
   (ciclos, grafo de módulos, API nomeada). A prosa dos `package-info` passa a descrever a API
   nomeada + a convenção `@ModuleInternal` no lugar de "o subpacote `internal` é module-private".

## Consequences

**Positivas**
- Layout plano, alinhado à herança Go e ao projeto irmão fkerp-poc. Sem pasta cerimonial.
- Encapsulação **provada por tooling** (ArchUnit), não por convenção de pasta: a regra cobre os 22
  módulos uniformemente e tem teeth (teste que falha ao plantar a violação).
- O sinal de "miolo do módulo" fica no **tipo** (`@ModuleInternal`), legível na própria classe, em
  vez de implícito no caminho do arquivo.
- Tipos antes package-private em `internal` continuam package-private — encapsulação do compilador
  intacta, sem anotação.

**Negativas / custo**
- Movimento amplo de arquivos (126 main + 10 test) num único refactor estrutural; diff grande, porém
  mecânico e protegido pelos 466 testes + gates.
- Cada tipo público ex-internal carrega uma anotação `@ModuleInternal` — uma linha por arquivo. É o
  preço de manter o sinal explícito; mais barato que enumerar `allowedDependencies` por módulo.
- A regra ArchUnit precisa conhecer a exceção `infra` (e os testes do próprio módulo) — documentada
  no Javadoc da regra. Se um novo adapter de infra precisar de outra persistência de módulo, ele já
  está coberto pela exceção `..infra..` (consistente com ADR 0010/0012).

## Alternatives Considered

- **Manter os pacotes `internal`.** Rejeitado: contraria a Fase 9 do ROADMAP e a herança Go. É o
  status quo que a fase existe para remover.
- **Só visibilidade package-private (sem marcador, sem regra).** Rejeitado: muitos tipos ex-internal
  são `public` porque o `*Service` do módulo (e os testes do módulo, em outro pacote) precisam deles.
  Torná-los package-private exigiria mover serviço e testes para o mesmo pacote-arquivo ou rebaixar o
  acesso — quebraria os testes do módulo e não daria a um adapter de infra legítimo (security) acesso
  à persistência. Não há como esconder de *outros módulos* mas expor ao *próprio módulo* só com
  `public`/package-private quando o consumidor legítimo está em pacote diferente.
- **`@ApplicationModule(allowedDependencies = …)` + `@NamedInterface` por subpacote de API.**
  Rejeitado por Regra Zero: exigiria que cada módulo enumerasse explicitamente todas as suas
  dependências permitidas (cerimônia alta, frágil a cada nova colaboração) e/ou recriar subpacotes de
  API — reintroduzindo a divisão de pastas que a fase quer eliminar. Um `@NamedInterface` é
  *package-scoped*; com tudo numa base package, não dá para separar API de miolo por named interface
  sem voltar a ter subpacotes. O marcador de tipo + ArchUnit entrega a mesma garantia sem a cerimônia.
- **Confiar só no Spring Modulith pós-achatar.** Rejeitado: tecnicamente falso — após o achatamento o
  Modulith trata a base package inteira como API e **deixa de esconder** os tipos ex-internal; a
  encapsulação sumiria. Seria afrouxar um portão (Regra 5).
