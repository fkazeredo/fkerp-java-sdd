# DL-0038 — Q8: operador/diretor editam parâmetros e diretivas em runtime (self-service auditável); fluxos não

- **Fase:** 8a (CommercialPolicy)
- **Spec(s):** SPEC-0014 (Open Question **Q8**; BR5 DIRECTIVE com auditoria reforçada; Validation
  Rules "DIRECTIVE exige papel diretor + justificativa"; Error Behavior `policy.directive.forbidden`
  → 403)
- **ADR relacionado:** 0011 (exceções), 0012 (camadas) ; `architecture/security.md` (autorização de
  negócio; backend é a autoridade final)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A **Q8** da SPEC-0014 deixa em aberto se a criação de regras/diretivas é **self-service** (diretor/
admin mexem em runtime) ou **só por TI** (spec + deploy). Isso governa **autorização e auditoria** do
`POST /rules` e `POST /directives`.

## Decisão

Adotar a **Recomendação do ROADMAP (tabela "Recomendações para as Open Questions", Q8)**:

1. **Sim, self-service em runtime para parâmetros e diretivas** — diretor/admin criam regras e
   diretivas pela API, sem deploy. **Fluxos não** (máquinas de estado, integrações, schema)
   continuam governados por código + spec + deploy.
2. **Autorização por papel** (autoridade no backend, `security.md`):
   - `POST /api/commercial-policy/directives` exige papel **diretor** (`ROLE_DIRECTOR`); sem o papel
     → `policy.directive.forbidden` (403). Justificativa **obrigatória** (BR5).
   - `POST /api/commercial-policy/rules` (POLICY/PROMOTION/CONTRACT) exige papel **admin/curador**
     (`ROLE_POLICY_ADMIN` ou `ROLE_DIRECTOR`); sem papel → 403.
   - A checagem lê os papéis do `UserContextProvider` (stub dev hoje; **Identity/SPEC-0024** entrega
     o IdP real — mesmo seam dos demais módulos). O **dev stub** ganha os papéis necessários para os
     testes e2e exercitarem 201 e 403 sem depender de SPEC-0024.
3. **Toda criação é auditada** (campos `defined_by` + `created_at`/`updated_at` + `version`); a
   DIRECTIVE tem **auditoria reforçada**: `justification` obrigatória persistida + evento
   `DirectiveIssued{ruleId, key, definedBy, justification, occurredAt}` logado como evento de
   negócio (BR5, Observability). A regra criada **reflete imediatamente** na próxima resolução
   (consulta sobre a tabela — sem cache), provado por teste.

## Justificativa

- **Autoridade do ROADMAP** (CLAUDE.md: ordem de autoridade = spec > recomendação do dono): a
  recomendação é explícita — "Sim para parâmetros e diretivas (self-service p/ diretor/admin,
  auditável); não para fluxos". O motor de parâmetros governados **existe justamente** para o diretor
  "mexer nas regras no faro" (OVERVIEW 3.4/7.3) com rastro.
- **security.md:** autorização de negócio crítica/reutilizável vira regra explícita; o backend é a
  autoridade final, nunca o frontend.
- **Confiança=Média:** os **nomes/conjunto exato de papéis** (`ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN`)
  são do escopo de Identity (SPEC-0024) e podem ser renomeados lá; a *política* (diretor edita
  diretiva, admin edita regra, tudo auditado) é o que está decidido. Reversão **barata**: a checagem
  é um ponto único no serviço/controller.

## Alternativas descartadas

- **Só TI (spec + deploy).** Descartada: contraria a recomendação do ROADMAP e a tese do produto (o
  diretor decide no faro); transformaria cada exceção comercial em release.
- **Sem papel, qualquer usuário cria diretiva.** Descartada: BR5 exige papel diretor + justificativa;
  diretiva é a "ordem do diretor" e precisa de auditoria reforçada.
- **Cache da resolução.** Descartada nesta fatia: a spec exige "reflete imediatamente"; cache seria
  overengineering e arriscaria servir valor velho. Fica como otimização futura, se medida.

## Impacto

- Controllers `CommercialPolicyController` (resolve + rules + directives + list) com checagem de papel
  e `policy.directive.forbidden` (403) mapeado em `HttpErrorMapping`.
- `DevStubUserContextProvider` ganha `ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN` (stub, ainda SPEC-0024).
- Evento `DirectiveIssued` + log de evento de negócio. Auditoria nos campos da entidade.
- Teste e2e: 201 com papel, **403 sem papel**, e "diretiva passa a vencer imediatamente".

## Como reverter

Reversão **barata**: a autorização é um guard único por endpoint. Trocar nomes de papel = ajustar a
constante + o stub + o fixture do teste. Tornar "só TI" = remover os endpoints de escrita (a
resolução permanece). Sem mudança de schema para reverter a política de autorização.
