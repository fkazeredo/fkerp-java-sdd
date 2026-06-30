# DL-0097 — GET /api/version a partir do build-info do Spring Boot + git commit (degradação graciosa)

- **Fase:** 11 (Observabilidade & monitoramento)
- **Spec(s):** SPEC-0027 (BR4)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A fase pede `GET /api/version` com **versão/commit git/hora de build**. A POC entrega só a `version`
(de uma config property). A tarefa pede explicitamente **build-info do Spring Boot** (version + git
commit + build time). Faltava decidir a **fonte** de cada campo e o **comportamento fora de um jar
empacotado** (dev/teste, onde não há `build-info.properties`/`git.properties`).

## Decisão

Payload **`{ version, gitCommit, buildTime }`**:

- **`version`** ← `BuildProperties.getVersion()` quando disponível; **fallback** para a versão do
  `pom.xml` exposta como property (`@project.version@` filtrada para `application.yml`,
  `app.version`). Fonte de verdade da versão é o `pom.xml` (ADR 0015).
- **`gitCommit`** ← `GitProperties.getShortCommitId()` (gerado pelo `git-commit-id-maven-plugin` em
  `git.properties`); **fallback** `"unknown"` quando ausente.
- **`buildTime`** ← `BuildProperties.getTime()` (ISO-8601); **fallback** `"unknown"` quando ausente.

Geração: `spring-boot-maven-plugin` com o goal **`build-info`** (gera
`META-INF/build-info.properties`) + **`git-commit-id-maven-plugin`** (gera `git.properties`,
`failOnNoGitDirectory=false`, `failOnUnableToExtractRepoInfo=false`). Os beans `BuildProperties` e
`GitProperties` são **opcionais** (`ObjectProvider`/`@Autowired(required=false)`): se não existirem
(execução fora do build empacotado), o controller degrada para os fallbacks e **nunca quebra**
(BR4). O endpoint é **público** (DL-0095) — só metadados, sem segredo.

## Justificativa

- **Tarefa da fase (autoridade):** pede build-info do Spring Boot com os três campos; é o mecanismo
  oficial (`BuildProperties`/`GitProperties` + plugins) e alimenta também o `/actuator/info`.
- **ADR 0015:** a versão é fonte de verdade no `pom.xml`; `build-info` lê justamente o `project.version`
  no empacotamento, então `BuildProperties.version` == SemVer do pom — sem duplicação.
- **Degradação graciosa:** os testes de integração rodam a app **sem** o `build-info`/`git.properties`
  do jar (contexto de teste); tornar os beans opcionais com fallback mantém o endpoint 200 e o teste
  determinístico (`version` ainda vem do `app.version` filtrado do pom).
- **Por que `git-commit-id-maven-plugin`:** é o plugin de fato para `git.properties`, compatível com o
  `GitProperties` do Spring Boot; `failOnNoGitDirectory=false` evita quebrar build em árvores sem
  `.git` (CI shallow, tarball).

## Alternativas descartadas

- **Só `version` de config (como a POC):** não entrega commit/buildTime que a tarefa pede.
- **Ler `git.properties` à mão / chamar `git` em runtime:** reinventa o que `GitProperties` já entrega;
  chamar `git` em runtime é frágil e não funciona no container. Descartado.
- **Beans obrigatórios (`required=true`):** quebraria o contexto de teste/dev sem `build-info`. Por isso
  opcionais + fallback.
- **Expor só via `/actuator/info`:** a fase pede um `GET /api/version` dedicado (rodapé/about da UI);
  `/actuator/info` continua existindo e também carrega o build-info, mas o contrato estável é
  `/api/version`.

## Impacto

- **Specs:** SPEC-0027 BR4, AC1.
- **Arquivos:** `pom.xml` (goal `build-info` no `spring-boot-maven-plugin`; `git-commit-id-maven-plugin`;
  filtrar `application.yml` para resolver `@project.version@`); `application.yml`
  (`app.version: @project.version@`); `application/api/VersionController.java` +
  `application/api/dto/VersionResponse.java` (record `{version, gitCommit, buildTime}`); `OpenApiConfig`
  (frase do endpoint). Endpoint público no `SecurityConfig` (DL-0095).
- **Migração/Contrato:** nenhuma migração. **Contrato novo aditivo:** `GET /api/version` (OpenAPI).

## Como reverter

Remover o controller/DTO/endpoint público e os plugins de build-info/git. A versão continua no pom
(ADR 0015). Mudança localizada — **barata**.
