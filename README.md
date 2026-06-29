# ERP Acme Travel — Java + Spring Boot + Angular

> 🌐 **Idioma / Language:** **Português (pt-BR)** · [English](README.en-US.md)

Pacote **completo e autossuficiente** das especificações e regras de arquitetura do ERP da Acme Travel
(um GSA — representante comercial de marcas de turismo). **Stack: Java 21 + Spring Boot no backend,
Angular no front, Postgres, monólito modular.** Prosa em pt-BR; identificadores de código em inglês.

> Este pacote é **só Java**. (Existe uma versão espelho em Go + React, com o mesmo domínio e as mesmas
> regras — mas ela **não** está aqui, para não confundir. Aqui é Java/Spring/Angular, ponto.)

---

## 1. Por onde começar (leia nesta ordem)

1. **`erp-turismo-b2b-redesenho.md`** — o documento de domínio: o que a Acme é, como ganha dinheiro
   (comissão de duas pontas + spread), os 22 contextos e as regras de negócio. **É a fonte da verdade.**
2. **`CLAUDE.md`** — as regras operacionais que o Claude Code segue em **toda** tarefa (Regra Zero,
   ordem de autoridade, "nunca inventar regra de negócio", Definition of Done, mapa de roteamento).
3. **`architecture/`** — as regras detalhadas, carregadas sob demanda (backend, persistência, módulos,
   segurança, mensageria, testes, front Angular, etc.).
4. **`docs/ROADMAP.md`** — em que ordem construir (fatias verticais por fase) + o índice das 25 specs
   + **as recomendações para as Open Questions** (sugestões de partida que você decide).
5. **`docs/TUTORIAL.md`** — o **laço de 7 passos** que você repete a cada fatia com o Claude Code
   (perguntas → plan → teste vermelho → esqueleto → verde → refatora → portões/DoD), com prompts reais.
6. **`docs/specs/`** — as 25 especificações (0001–0025), uma por contexto. **`docs/adr/`** — as decisões
   de arquitetura (0010–0014) + template.

---

## 2. Estrutura do pacote

```txt
acme-travel-erp-java/
  README.md                          <- este arquivo
  erp-turismo-b2b-redesenho.md       <- domínio (fonte da verdade)
  CLAUDE.md                          <- regras operacionais (sempre carregadas)
  architecture/                      <- 12 docs de regras (carregados sob demanda)
    core-principles.md  backend.md  modules-and-apis.md  persistence.md
    messaging-and-integrations.md  security.md  observability.md  delivery.md
    frontend-angular.md  workflow.md  testing.md  simulation-and-mocking.md
  docs/
    ROADMAP.md                       <- ordem das fatias + índice + recomendações das Open Questions
    TUTORIAL.md                      <- o laço de cada fatia (com prompts)
    adr/                             <- 0000-template + 0010..0014
    specs/                           <- 0000-template + 0001..0025 (todas as 25)
```

---

## 3. Como usar com o Claude Code (resumo)

O método é **fatia vertical, teste primeiro**: cada fatia atravessa migração → domínio → API → tela e
fica demonstrável ao fim. **Não** se cria 22 módulos vazios de uma vez; o código nasce **uma fatia por
vez**, na ordem do ROADMAP.

1. **Setup (1×):** rode a **SPEC-0001** (esqueleto que sobe, conecta no Postgres, tem `/api/system/health`,
   tela Angular mínima, ArchUnit + Spring Modulith verdes e CI). Tenha **JDK + Docker** (os testes de
   integração usam Testcontainers) e use sempre o **`./mvnw`** do projeto.
2. **Por fatia (SPEC-0002 em diante):** siga o laço do `TUTORIAL.md`. Antes de codar, **decida as Open
   Questions** que afetam a fatia (o ROADMAP traz recomendações de partida) e registre a decisão na spec.
3. **Portões inegociáveis:** `./mvnw verify` precisa ficar verde — inclui ArchUnit e Spring Modulith. Se
   reclamarem, **conserte o código, nunca afrouxe a regra**.

---

## 4. O que decidir antes de codar (Open Questions)

Nenhuma regra de negócio foi inventada: onde o redesenho não decide, a spec **registra a pergunta**. As
decisões do dono que **travam** fatias estão na tabela do `ROADMAP.md` (Q1–Q8 + a **fórmula de preço** do
Quoting), agora **com uma recomendação de partida para cada uma**. Leve essa tabela ao diretor/contador,
marque OK/trocar, e só então abra a fatia dona.

Sequenciamento que vale lembrar: **Finance (0015) co-entrega com Compliance (0008) na Fase 2** — o veto de
fechamento do Compliance depende do conceito de período, que é do Finance.

---

## 5. Convenções herdadas

Todas as specs herdam o bloco **"Convenções do projeto"** da **SPEC-0001** (Money = `BigDecimal` scale 2;
taxa de câmbio scale 6; HALF_UP; UTC/ISO-8601; `DomainException{code}` com `code` == chave i18n; coluna
`@Version`; locking pessimista em transições financeiras; sem FK cross-contexto — ids como valor; portas
+ ACL para o externo; jobs com idempotência/locking/histórico). Os ADRs 0010–0013 fixam: camada `infra`
centralizada com portas por módulo; exceções de domínio sem concern de transporte; três camadas
hexagonais; política de Lombok. O ADR 0014 fixa os módulos iniciais e a ordem das fatias.
