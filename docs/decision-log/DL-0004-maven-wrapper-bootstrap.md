# DL-0004 — Bootstrap do Maven Wrapper sem Maven no sistema

- **Fase:** 0 (Fundação)
- **Spec(s):** SPEC-0001
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A regra do projeto (CLAUDE.md) exige usar **sempre o `./mvnw` do projeto**, nunca
um Maven do sistema. Mas o ambiente **não tem Maven instalado** e o repositório
ainda não tinha `backend/`. Era preciso decidir **como gerar o wrapper**.

## Decisão

Versionar o **Maven Wrapper 3.3.x** apontando para **Maven 3.9.11** em
`backend/.mvn/wrapper/maven-wrapper.properties` + scripts `mvnw`/`mvnw.cmd`. Para
gerar os arquivos corretos uma única vez, baixar a distribuição do Maven 3.9.11
(scratchpad), rodar `mvn -N wrapper:wrapper` dentro de `backend/`, e a partir daí
usar exclusivamente `./mvnw`. O `maven-wrapper.jar` (~60 KB) é versionado (exceção
explícita no `.gitignore`), garantindo build reprodutível offline do wrapper.

## Justificativa

- CLAUDE.md ("No system Maven — always use the wrapper from `backend/`").
- `delivery.md` (env local reprodutível). O wrapper torna o build idêntico em
  qualquer máquina/CI sem depender de um Maven pré-instalado.
- Versionar o `maven-wrapper.jar` evita um download extra do jar a cada `git clone`
  e remove um ponto de falha de rede no primeiro `./mvnw`.

## Alternativas descartadas

- **Usar um Maven baixado direto (sem wrapper)** — descartada: viola a regra do
  CLAUDE.md de usar o wrapper do projeto.
- **Escrever os scripts `mvnw` à mão** — descartada: propenso a erro; deixar o
  goal `wrapper:wrapper` gerar os arquivos oficiais é mais seguro.
- **Não versionar o `maven-wrapper.jar`** (baixar via `wrapperUrl` no 1º run) —
  descartada: adiciona dependência de rede no primeiro build; versionar é o padrão
  recomendado e robusto.

## Impacto

- Arquivos: `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper/*`,
  `.gitignore` (exceção para o jar do wrapper), `.gitattributes` (LF no `mvnw`).

## Como reverter

Trocar a versão alvo em `maven-wrapper.properties` e re-rodar `wrapper:wrapper`.
Trivial.
