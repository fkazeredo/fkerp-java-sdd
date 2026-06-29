# Plano — Fase 8a: CommercialPolicy (parâmetros governados + motor de precedência) — SPEC-0014

> Modo autônomo (RUN-PHASE, FASE-ALVO=8, **escopo restrito à SPEC-0014**). **Não** implementa nenhuma
> outra SPEC-0015..0025. Esta fatia **gradua** o stub de markup da Fase 1 (`SystemDefaultMarkupProvider`)
> no **motor real de parâmetros governados** com **precedência + proveniência**
> (`Diretiva > Promoção > Contrato > Política > Padrão`), **mantendo o contrato `MarkupProvider`** para
> que o Quoting continue compilando e com todos os testes verdes.

## Objetivo

- **Agregado `ParameterRule`** parametrizado por **camada** (DIRECTIVE/PROMOTION/CONTRACT/POLICY/
  SYSTEM_DEFAULT), **escopo** (global / conta / produto / canal) e **vigência** (validFrom/validUntil),
  com autor/auditoria (BR1).
- **Motor `resolve(key, scope) → {value, provenance}`** que percorre a precedência por camada e, dentro
  da camada, por especificidade de escopo, com **desempate determinístico** (BR2/BR3/BR8, DL-0037). A
  proveniência diz **qual camada venceu, quem definiu e quando** (redesenho 7.3).
- **SYSTEM_DEFAULT sempre presente** para toda key usada (BR4) — seed de `MARKUP_PCT`, `FX_DRIFT_LIMIT`,
  `RECON_DISCREPANCY_TOL` (DL-0039).
- **DIRECTIVE** = topo, com **auditoria reforçada** (papel diretor + justificativa + evento, BR5/BR7,
  DL-0038); **self-service em runtime** para parâmetros/diretivas (Q8 = recomendação do ROADMAP).
- **Graduação do `MarkupProvider`** (DL-0040): a porta passa a resolver o markup pelo motor; `source` =
  **camada vencedora** (não mais sempre SYSTEM_DEFAULT). Sem regra acima do default → `pct=0`,
  `source=SYSTEM_DEFAULT` (**back-compat** idêntica ao stub).
- **APIs** Open-Host: `GET /resolve`, `POST /rules`, `POST /directives`, `GET /rules`; OpenAPI/i18n.

## Decisões registradas antes do código (decision-log)

| DL | Lacuna (Open Question) | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| DL-0037 | Modelagem da regra + ordem de desempate dentro da camada | Colunas de escopo (não jsonb); especificidade = nº de dimensões casadas; ordenação `(layer.rank, specificity DESC, validFrom DESC, createdAt DESC, id ASC)` | Alta | Moderada |
| DL-0038 | Q8: quem edita regra em runtime; autorização/auditoria | Self-service (diretor edita diretiva c/ papel+justificativa+evento; admin edita regra); fluxos não; reflete imediatamente | Média | Barata |
| DL-0039 | Conjunto de `parameterKey` + Q5 (comissão do agente) | Seed só das chaves já usadas (MARKUP_PCT=0, FX_DRIFT_LIMIT=2%, RECON_DISCREPANCY_TOL=R$1,00); Q5 comportada pelo motor por escopo, sem implementar Commissioning | Média | Barata |
| DL-0040 | Como graduar o `MarkupProvider` sem quebrar o Quoting | Manter porta; add sobrecarga `currentMarkup(scope)`; `source`=camada vencedora; sem regra → SYSTEM_DEFAULT (back-compat); remove o stub; `CommercialPolicyService` implementa a porta | Alta | Moderada |

## Fatia única — Slice 8a1 · `feature/slice-8a-commercial-policy`

> Uma fatia coesa (o motor e a graduação são indissociáveis: graduar sem o motor não faz sentido, e o
> motor sem graduar deixaria o stub vivo). Commits pequenos em Conventional Commits ao longo do laço.

- **Domínio (`com.fksoft.domain.commercialpolicy`)** — graduando o módulo existente (não cria módulo):
  - Enums com comportamento: `ParameterLayer` (rank de precedência) e `ParameterValueType`
    (NUMBER/PERCENT/MONEY/BOOL, com parse/validação do `value_text`).
  - Value objects: `ParameterKey` (formato `A-Z_`), `ParameterScope` (account/product/channel
    opcionais + `global()` + cálculo de especificidade/match), `ResolvedParameter` (value+type+
    provenance), `Provenance` (layer/ruleId/definedBy/definedAt/validUntil).
  - `internal.ParameterRule` (entidade JPA, audit fields, `matches(scope)`, `specificity()`,
    `toResolved()`); `internal.ParameterRuleRepository` (módulo-privado).
  - `CommercialPolicyService` (`@Service`): `resolve(key, scope)` (motor de precedência, BR2/BR3,
    DL-0037), `defineRule(...)`, `issueDirective(...)` (auditoria reforçada + evento), `listRules(...)`.
    **Implementa `MarkupProvider`**: `currentMarkup(scope)` resolve `MARKUP_PCT`; `currentMarkup()`
    delega para `global()`.
  - Exceções de domínio (code = chave i18n): `policy.parameter.unknown` (404),
    `policy.rule.invalid` (400), `policy.directive.forbidden` (403).
  - Eventos: `ParameterRuleDefined`, `DirectiveIssued` (in-process, logados como evento de negócio).
  - **Remove** `SystemDefaultMarkupProvider` (stub graduado); `MarkupDecision` ganha constantes de
    source; `MarkupProvider` ganha `currentMarkup(MarkupScope)`.
- **Quoting:** `QuoteService.compose` chama `currentMarkup(scope)` com o escopo da cotação (accountId +
  productRef) — única mudança; testes herdados seguem verdes (back-compat).
- **Persistência:** `V18__create_commercial_policy.sql` — `parameter_rules` (matcher por colunas,
  índice `(parameter_key, layer)`) + seed dos 3 SYSTEM_DEFAULT (BR4, DL-0039).
- **Delivery:** `CommercialPolicyController` (`/api/commercial-policy`): `GET /resolve`, `POST /rules`,
  `POST /directives` (papel diretor), `GET /rules`; DTOs request/response; `HttpErrorMapping` atualizado.
- **Segurança:** checagem de papel (`UserContextProvider`); `DevStubUserContextProvider` ganha
  `ROLE_DIRECTOR`/`ROLE_POLICY_ADMIN` (stub, ainda SPEC-0024) p/ e2e exercitar 201 e 403.

## Testes (Tests Required + Acceptance Criteria da spec)

- **Unit/domínio (precedência):** DIRECTIVE bate POLICY mesmo com escopo menos específico;
  PROMOTION de produto bate POLICY global; especificidade dentro da camada (produto > agência >
  global); empate de mesma camada+especificidade → desempate determinístico (validFrom/createdAt/id);
  fallback ao SYSTEM_DEFAULT; key sem default → exceção. **Fixtures exatas**.
- **Integração (Testcontainers/Postgres):** `GET /resolve` retorna valor + proveniência corretos;
  `POST /directives` passa a vencer **imediatamente**; `POST /directives` sem papel → 403; key sem
  default → 404; `POST /rules` cria POLICY/PROMOTION e reflete; auditoria persistida.
- **Quoting (regressão de fronteira — graduação):** com PROMOTION/DIRECTIVE de `MARKUP_PCT` para a
  conta, a Quote composta aplica markup **≠ 0** e proveniência **≠ SYSTEM_DEFAULT**; **sem** regra, a
  Quote ainda sai com `SYSTEM_DEFAULT`/markup 0 (back-compat — o teste e2e herdado do Quoting passa).
- **Arquitetura:** Spring Modulith **acíclico** (commercialpolicy não cria ciclo com quoting — quoting
  já depende dele); ArchUnit/Spotless/Checkstyle verdes; `HttpErrorMappingCompletenessTest` cobre as 3
  novas exceções.

## Portões / Definition of Done

- `cd backend && ./mvnw spotless:apply && ./mvnw verify` **verde** (Docker no ar): ArchUnit + Modulith
  (12 módulos, acíclico) + Spotless + Checkstyle.
- Migração `V18` aplicada e validada (Postgres real). i18n pt-BR + fallback. OpenAPI atualizada.
- Caderno de testes `docs/test-report/slice-8a-commercial-policy.md` (+ INDEX).
- Merge `--no-ff` em `develop`; push. Release **0.9.0** (ADR 0015 = próximo MINOR): bump pom, release
  note, release branch → main+develop → tag → push.

## Fora de escopo (explicitamente — simulation-and-mocking.md)

- Consumo de `AGENT_COMMISSION_PCT` no Commissioning (Q5 segue na SPEC-0004); migração de Exchange/
  Reconciliation de constante para parâmetro governado (Open-Host só **expõe**); telas Angular; SPEC-0015..0025.
