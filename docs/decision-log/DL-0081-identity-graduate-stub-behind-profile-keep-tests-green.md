# DL-0081 — Identity: gradua o stub mantendo a porta `UserContextProvider`; stub permissivo atrás de profile `dev`/`test`; produção usa o JWT

- **Fase:** 8k (Identity)
- **Spec(s):** SPEC-0024 (BR1 — porta inalterada; BR6 — stub só em dev atrás de profile, desligado em
  produção; Tests Required — "stub de dev atrás de profile e desativado em produção"; Acceptance Criteria)
- **ADR relacionado:** `architecture/security.md`; `architecture/simulation-and-mocking.md` (stub
  rastreável atrás de flag); SPEC-0001 (origem do stub)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O sistema roda hoje sobre o `DevStubUserContextProvider` e **434 testes de integração assumem um ator
autenticado com acesso total**. A spec manda **graduar** o stub (BR1: "o `UserContextProvider` real
substitui o stub, sem mudar a interface que os módulos consomem"; BR6: "em dev o stub pode permanecer
atrás de profile; em produção MUST estar desativado"). Falta decidir **como** ligar a segurança real
**sem quebrar** os testes — graduar ≠ rip-and-replace.

## Decisão

1. **A porta `UserContextProvider` permanece intocada** (a interface que todo módulo consome). O que
   muda é **qual adapter** o Spring injeta, governado por **profile**:
   - **Produção / default (sem profile `dev`/`test`):** `JwtUserContextProvider` lê o
     `Authentication` do `SecurityContext` (preenchido pelo filtro JWT) e devolve `{userId, username,
     roles}` reais. O stub **não** é um bean aqui (`@Profile("!test & !dev")` no stub, ou o real
     marcado `@Primary` fora desses profiles).
   - **Dev (`dev`):** o `DevStubUserContextProvider` permanece (ator `dev` com papéis amplos), para
     desenvolvimento local sem login — exatamente o que BR6 permite.
2. **Caminho de teste verde sem afrouxar segurança (a maior restrição da fase):** os testes de
   integração rodam com profile **`test`**, no qual:
   - a **cadeia Spring Security** existe e está ativa (não é desligada), **mas** uma
     `TestSecurityConfig` (em `src/test`) autentica toda requisição como um **ator de teste com acesso
     total** (um `Authentication` fixo via filtro de teste), e o `DevStubUserContextProvider`
     (profile `test`) devolve o mesmo ator ao domínio. Assim, os 434 testes existentes **não
     precisam mandar token** e continuam verdes **porque a segurança está realmente montada** — não
     porque foi removida.
   - Os **novos testes de segurança da SPEC-0024** sobem o caminho **real** (sem o atalho de teste):
     geram tokens JWT determinísticos (com/sem papel) e provam **401** (token ausente/inválido,
     mensagem genérica) e **403** (papel insuficiente, auditado). Eles exercitam o
     `JwtUserContextProvider` real e a cadeia de filtros real.
3. **Endpoints públicos** continuam abertos: `GET /api/system/health` (BR de SPEC-0001),
   `POST /api/identity/login`, e a doc (`/v3/api-docs`, `/swagger-ui`). Webhooks de integração
   (`/api/webhooks/**`, `/api/integration/**`) seguem autenticados pela **assinatura HMAC própria**
   (não por sessão de usuário) — permanecem fora da autenticação por usuário.
4. **Nenhum teste é apagado/enfraquecido.** Se um teste muda, é porque o contrato mudou honestamente
   (ex.: um teste que antes não mandava token e agora exercita o 401 real). Os gates
   ArchUnit/Modulith/Spotless/Checkstyle seguem ligados.

## Justificativa

- **BR1/BR6 e o brief da fase** mandam exatamente isto: porta estável, stub atrás de profile,
  produção sem stub, testes verdes sem bypass de uma camada quebrada.
- **simulation-and-mocking.md:** o stub é rastreável e atrás de flag/profile — padrão já usado no
  projeto.
- **"Segurança montada, não removida":** a `TestSecurityConfig` **inclui** a cadeia de filtros e um
  `Authentication` real de teste; ela não desativa o `SecurityFilterChain`. Isso satisfaz a exigência
  de não "enfraquecer uma camada de segurança agora quebrada".
- **Confiança=Alta / Reversibilidade=Barata:** trocar o adapter por profile é um ponto único de
  configuração; reverter é mexer numa anotação `@Profile`.

## Alternativas descartadas

- **Desligar o Spring Security nos testes (`@AutoConfigureMockMvc(addFilters=false)` global ou
  `security.enabled=false`).** Descartada: enfraqueceria a camada — proibido pelo brief; mascararia
  regressões de segurança.
- **Reescrever os 434 testes para mandar token.** Descartada: custo enorme e desnecessário; o ator de
  teste com acesso total via `TestSecurityConfig` mantém o contrato sem reescrever cada teste.
- **Manter o stub em produção atrás de uma flag.** Descartada: BR6 proíbe o stub em produção.

## Impacto

- **Arquivos:** `infra.security.SecurityConfig` (cadeia real, default/prod), `JwtUserContextProvider`
  (`@Profile("!test & !dev")` ou `@Primary`), `DevStubUserContextProvider` (`@Profile({"dev","test"})`),
  `src/test/.../TestSecurityConfig` (+ filtro de ator de teste) importada pela base de teste.
- **`AbstractPostgresIntegrationTest`:** roda com profile `test` e importa a `TestSecurityConfig` (ou
  via `@ActiveProfiles("test")` + `@Import`).
- **Sem mudança de schema** para a política de profile.

## Como reverter

Trocar as anotações `@Profile`/`@Primary` para escolher outro adapter. Tornar produção sem login
(volta ao stub) seria reverter a própria fase — não previsto. Custo: minutos.
