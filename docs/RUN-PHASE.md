Implemente a fase-alvo do ROADMAP do ERP Acme Travel (Java/Spring Boot + Angular) em modo autônomo: decida o que estiver em aberto, implemente sem pedir aprovação, e registre cada decisão em docs/decision-log/.

## Ler antes de começar
OVERVIEW.md; CLAUDE.md; tudo em architecture/; docs/ROADMAP.md (inclusive a tabela "Recomendações para as Open Questions"); docs/TUTORIAL.md; tudo em docs/adr/; e todas as specs da fase-alvo em docs/specs/ (identifique-as pelo índice e pelas fases do ROADMAP; inclua a SPEC-0001 se a fase depender da fundação).

## Pré-requisito
Se alguma fase anterior à fase-alvo ainda não estiver implementada e verde, implemente-a primeiro, na ordem do ROADMAP, até chegar na fase-alvo.

## Modo autônomo (não me pergunte)
Para cada lacuna ou Open Question que afete o código, decida nesta ordem:
1. Se docs/ROADMAP.md "Recomendações para as Open Questions" recomenda algo, adote.
2. Senão, pesquise em fontes confiáveis, oficiais ou acadêmicas e decida pela opção mais recomendada.
3. Se faltar um dado que só o negócio tem, adote o valor mais defensável e marque Confiança=Baixa e Reversibilidade=Cara.
Registre a decisão em docs/decision-log/ antes de escrever o código que depende dela, e mova o item da spec de "Open Questions" para "Business Rules" marcando "ASSUMIDO (ver DL-NNNN)".

## docs/decision-log/
Crie a pasta se não existir. Um arquivo append-only por decisão: DL-0001, DL-0002, … Mantenha docs/decision-log/INDEX.md com todas, destacando no topo as de Reversibilidade=Cara ou Confiança=Baixa. Cada arquivo DL-NNNN-titulo.md contém:
- Cabeçalho: Fase, Spec(s), Data, Status=ASSUMIDO, Confiança (Alta/Média/Baixa), Reversibilidade (Barata/Moderada/Cara).
- Lacuna: o que não estava decidido.
- Decisão: o que foi adotado (valor, fórmula ou modelagem).
- Justificativa: por quê; cite as Recomendações do ROADMAP ou as fontes pesquisadas.
- Alternativas descartadas: cada uma e o motivo.
- Impacto: specs, arquivos, migrações e contratos afetados.
- Como reverter: o que mudar e o tamanho do refactoring.

## Planejar e implementar
Gere o plano da fase, salve em docs/plan/, e siga direto para a implementação, sem esperar aprovação. Implemente uma fatia por vez, na ordem de dependência do ROADMAP, pelo laço do TUTORIAL.md: teste vermelho → esqueleto → verde → refatora → portões → Definition of Done.

Regras inegociáveis:
- ArchUnit, Spring Modulith, Spotless/Checkstyle e CI ligados desde o primeiro commit e capazes de quebrar o build. Nunca afrouxe, pule ou apague um teste ou portão para o código passar.
- Migração Flyway por mudança de schema (idempotente; nunca editar uma já aplicada).
- DomainException com code == chave i18n; mensagens em pt-BR + fallback.
- Nenhuma exceção crua de banco vazando; OpenAPI atualizada.
- Observabilidade da spec: evento de negócio logado, dado pessoal mascarado, correlation id.
- Sem FK cross-contexto (id de outro contexto é valor); eventos in-process.
- Uma fatia = uma feature branch; commits pequenos em Conventional Commits; ./mvnw verify verde antes do merge (detalhes no bloco Git).
- Ambiente: JDK + Docker no ar (Testcontainers); sempre ./mvnw.

## Git (gitflow, autônomo)
O repositório já existe. Você tem autonomia para fazer commit, push e merge sozinho — não peça aprovação.
- Garanta as branches base do gitflow: main (produção) e develop (integração). Crie develop a partir de main se não existir.
- Para CADA fatia, abra uma feature branch a partir de develop: git checkout develop && git checkout -b feature/<fatia>.
- Implemente a fatia pelo laço do TUTORIAL.md, fazendo commits pequenos em Conventional Commits ao longo do caminho (feat:, test:, fix:, docs:). Faça push da feature branch.
- Quando a fatia estiver verde (./mvnw verify verde, portões passando) e o caderno de testes atualizado, faça o merge em develop: git checkout develop && git merge --no-ff feature/<fatia>. Rode ./mvnw verify em develop e faça push de develop. Apague a feature branch já mergeada.
- Ao fim da fase: crie release/<versão> a partir de develop, faça merge em main e em develop, crie a tag (git tag <versão>, a versão do release note) e faça push de main, develop e das tags.
- Nunca faça merge de uma fatia que não esteja verde.
- Se o remoto origin não estiver configurado (ou faltar credencial para o push), avise e siga sem o push — não trave a implementação por isso.

## Testes (proporcionais, desde o início)
- Unitários: regras de domínio, value objects, máquinas de estado, exceções.
- Integração: persistência, transações, API, eventos (Testcontainers, Postgres real).
- Arquitetura: ArchUnit + Spring Modulith no suite; um teste deve falhar ao plantar uma violação de fronteira.
- E2E (Angular): a jornada crítica da fase, derivada dos "Acceptance Criteria" das specs.
- Smoke: /api/system/health (liveness e readiness).
Cada "Tests Required" e "Acceptance Criteria" de cada spec vira teste e passa. Todo bug corrigido ganha teste de regressão.

## Caderno de testes — docs/test-report/
Crie a pasta se não existir. Para cada fatia, escreva/atualize docs/test-report/<fatia>.md e mantenha docs/test-report/INDEX.md. Cada arquivo contém:
- Escopo: a spec e os Acceptance Criteria cobertos pela fatia.
- Casos de teste por tipo (unitário, integração, e2e, smoke): nome do caso, o que verifica e a qual Acceptance Criteria / regra de negócio corresponde.
- Resultado: passou/falhou de cada caso + a saída resumida do ./mvnw verify e dos e2e.
- Cobertura: o que NÃO está coberto e por quê.
- Como reproduzir: os comandos para rodar cada nível de teste localmente.
Atualize-o ao fechar cada fatia (antes do merge). Nada de caderno vazio ou genérico.

## Entregar ao final
- Plano final em docs/plan/.
- Caderno de testes finalizado em docs/test-report/ (índice + um arquivo por fatia).
- Release note em docs/release-notes/<versão>.md (SemVer; crie a pasta e um template se não existirem).
- Relatório salvo como arquivo em docs/, com: fatias entregues; arquivos criados ou alterados; specs/ADRs atualizados; migrações; testes por tipo e o resultado de cada um, mais a saída do ./mvnw verify; impacto em OpenAPI; lista das decisões com link para cada DL-NNNN (destacando Reversibilidade=Cara e Confiança=Baixa); riscos; e o que ficou para a próxima fase.