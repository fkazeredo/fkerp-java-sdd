# DL-0126 — Documentação de API real: @Tag em todos, erros globais, Authorize PKCE, snapshot de contrato

- **Fase:** 19d (Refactoring de maturidade — pedido explícito do dono: doc de API)
- **Spec(s):** SPEC-0024 (segurança/OIDC); `architecture/modules-and-apis.md` (OpenAPI obrigatório)
- **ADR relacionado:** ADR-0018 (AS self-hosted); ADR 0011 (contrato de erro)
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O springdoc (3.0.3) já estava no pom e servia `/v3/api-docs` + Swagger UI, mas **vazio de
conteúdo**: **zero** anotações `@Tag`/`@Operation`, o botão **Authorize** não fazia nada útil
(bearer manual) e a `Info.description` era um **changelog de ~200 linhas** dentro do código
(duplicando as release notes). A doc existia mas não era usável.

## Decisão

1. **`@Tag` em todos os 37 controllers** (nome de negócio + descrição pt-BR) → o Swagger UI agrupa a
   superfície por contexto. Fitness function ArchUnit `REST_CONTROLLERS_ARE_TAGGED_FOR_OPENAPI`:
   todo `@RestController` deve ter `@Tag` (novo controller sem tag quebra o build).
2. **Contrato de erro documentado uma vez, globalmente** (`GlobalErrorResponsesCustomizer`): registra
   o schema `ApiErrorResponse` e adiciona 400/401/403/404/409/422 a toda operação — em vez de repetir
   `@ApiResponse` em ~75 endpoints. É **documentação** (superset); a autoridade do status segue no
   `HttpErrorMapping`.
3. **Botão Authorize funcional**: security scheme **OAuth2 Authorization Code + PKCE** apontando ao
   AS self-hosted (`/oauth2/authorize|token`, client `acme-erp-web`, ADR-0018) + um `bearerAuth`
   para colar token. O operador loga na UI e testa um endpoint gated.
4. **`Info.description` enxuta**: o changelog volta para onde vive (`docs/release-notes/`).
5. **Snapshot de contrato versionado** (`docs/api/openapi.json`) + **teste de drift**
   (`OpenApiSnapshotIntegrationTest`): compara `/v3/api-docs` normalizado (pretty, chaves ordenadas,
   sem o `servers` volátil) ao arquivo commitado; mudou o contrato sem atualizar o snapshot → falha.
   Regenera com `-Dopenapi.snapshot.write=true` (na mesma fatia que muda o contrato). Generaliza o
   invariante de contrato da Fase 18 à superfície inteira.

## Justificativa

- springdoc **é** a ferramenta moderna da stack (Boot + webmvc); o trabalho era torná-la real, não
  trocar de ferramenta (Regra Zero).
- `@Tag` completo + erros globais + Authorize PKCE + operationId (nome do método) entregam uma doc
  **usável** sem anotar 75 endpoints à mão.
- O snapshot versionado transforma "o contrato mudou sem querer" num **erro de build** (aderência).

## Alternativas descartadas

- **`@Operation` em cada endpoint (75×):** muita cerimônia mecânica; o `operationId` + `@Tag` +
  descrição já dão contexto. Sumários por endpoint ficam como follow-up incremental (seam aberto).
- **therapi-runtime-javadoc** (auto-Javadoc → OpenAPI): traria o Javadoc dos métodos, mas adiciona
  um annotation processor com risco de ordenação junto ao Lombok — custo/risco > ganho agora.
- **UI Scalar** como substituto do Swagger UI: avaliada; o Swagger UI com Authorize PKCE já cobre a
  necessidade. Adotar Scalar depois é aditivo.
- **springdoc-maven-plugin** (dump no build subindo o app): duplica infra; o teste de snapshot reusa
  o harness Testcontainers existente.

## Impacto

- **Arquivos:** `@Tag` nos 37 controllers; `GlobalErrorResponsesCustomizer`; `OpenApiConfig`
  reescrito (PKCE + info enxuta); `OpenApiSnapshotIntegrationTest`; regra ArchUnit nova (18ª);
  `docs/api/openapi.json` (novo, versionado).
- **Contratos:** o **conteúdo** da doc melhora; os endpoints/JSON não mudam de shape (o snapshot é o
  espelho fiel do que já existe).

## Como reverter

Barata: remover o customizer/PKCE e as tags. O snapshot + drift-test são aditivos e recomendados.
