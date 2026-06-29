# 0014 - CommercialPolicy (Parâmetros Governados e Precedência)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. Esta spec **gradua o `MarkupProvider` stub** introduzido na
> SPEC-0005 no motor real de parâmetros governados (redesenho 7.3 e linha 121).

## Goal

Centralizar os **parâmetros governados** da operação (markup, promoção, diretivas, limites — **não
preços**) num motor de **precedência com proveniência**: `Diretiva do diretor > Promoção > Contrato >
Política > Padrão do sistema`. Cada valor entregue diz **qual camada venceu, quem definiu e quando**
(redesenho 7.3). É o que faz o Quoting compor com um markup **rastreável** em vez de um default fixo.

## Scope

**Em escopo:** o agregado de regra parametrizada por **camada** (DIRECTIVE, PROMOTION, CONTRACT,
POLICY, SYSTEM_DEFAULT), com **escopo** (global / por agência / por produto / por canal) e **vigência**;
o motor `resolve(parameterKey, scope) → {value, provenance}` que percorre a precedência; a definição
auditável de **diretivas do diretor** (camada de topo); a implementação real da porta `MarkupProvider`
consumida pelo Quoting; expõe parâmetros como **Open-Host** para outros contextos (limite de drift da
SPEC-0011, tolerância de discrepância da SPEC-0007).

**Fora de escopo:** **preços de produto** (não existem aqui — o ERP não é dono de preço, Parte 6/linha
101); o cálculo de comissão (Commissioning) e a composição (Quoting) — CommercialPolicy só **fornece os
parâmetros**.

## Business Context

O diretor precisa **mandar** sem que cada exceção vire gambiarra: uma diretiva sua bate qualquer
política. Mas tudo fica **rastreável** — auditoria mostra que o número saiu de uma diretiva (e não de um
bug). Os parâmetros são governados num lugar só para o resto do sistema (markup, limites, tolerâncias)
não espalhar "número mágico".

## Business Rules

```txt
BR1  Uma ParameterRule MUST ter: parameterKey (ex.: MARKUP_PCT, FX_DRIFT_LIMIT, RECON_DISCREPANCY_TOL),
     layer ∈ {DIRECTIVE, PROMOTION, CONTRACT, POLICY, SYSTEM_DEFAULT}, scope (matcher),
     value (tipado), validFrom/validUntil (vigência), e autor (quem/quando).
BR2  resolve(parameterKey, scope) MUST retornar o value da regra **ativa** (vigente em now) de **maior
     precedência** cujo scope casa, junto com a **proveniência** (layer, autor, quando, ruleId).
     Precedência: DIRECTIVE > PROMOTION > CONTRACT > POLICY > SYSTEM_DEFAULT.
BR3  Escopo mais específico vence dentro da MESMA camada (produto > agência > global); entre camadas,
     a camada manda (uma POLICY global não supera uma PROMOTION de produto — promoção é camada acima).
BR4  Sempre MUST existir um SYSTEM_DEFAULT para todo parameterKey usado (resolução nunca fica vazia).
BR5  Uma DIRECTIVE é o topo da precedência e MUST ser auditada de forma reforçada (quem, quando,
     justificativa) — é a "ordem do diretor".
BR6  A resolução é **pura/consultável** e não altera estado de outros contextos (Open-Host).
BR7  ASSUMIDO (ver DL-0038): a criação de regras/diretivas é **self-service em runtime** (diretor/
     admin), auditável; **fluxos** (máquinas de estado/integrações/schema) continuam por spec+deploy.
     `POST /directives` exige papel **diretor** + justificativa (403 `policy.directive.forbidden`);
     `POST /rules` exige papel **admin/curador** ou diretor. A regra criada reflete imediatamente.
BR8  ASSUMIDO (ver DL-0037): especificidade de escopo = nº de dimensões não-nulas casadas; ordenação
     total determinística `(layer.rank, specificity DESC, validFrom DESC, createdAt DESC, id ASC)`.
BR9  ASSUMIDO (ver DL-0039): o seed SYSTEM_DEFAULT cobre só as chaves já usadas (MARKUP_PCT=0,
     FX_DRIFT_LIMIT=0.02, RECON_DISCREPANCY_TOL=R$1,00); a comissão do agente (Q5) é comportada pelo
     mesmo motor **por escopo** (agência>produto>global), mas seu consumo é da SPEC-0004 (segue aberto lá).
```

## Input/Output Examples

```http
GET /api/commercial-policy/resolve?key=MARKUP_PCT&accountId=8f1c...&productRef=CAR-MCO
200 OK
{ "key":"MARKUP_PCT", "value":"12.00",
  "provenance": { "layer":"PROMOTION", "ruleId":"r77...", "definedBy":"diretor.ana",
                  "definedAt":"2026-06-01T09:00:00Z", "validUntil":"2026-06-30" } }
```

```http
POST /api/commercial-policy/directives
{ "key":"MARKUP_PCT", "value":"8.00", "scope":{"accountId":"8f1c..."},
  "validFrom":"2026-06-26", "justification":"fechar cliente estratégico" }
201 Created   # camada DIRECTIVE — passa a vencer as demais para esse escopo
```

## API Contracts

- `GET /api/commercial-policy/resolve?key=&accountId=&productRef=&channel=` → valor + proveniência →
  200 | 404 `policy.parameter.unknown` (key sem SYSTEM_DEFAULT).
- `POST /api/commercial-policy/rules` — cria regra de camada POLICY/PROMOTION/CONTRACT (autorização por
  papel) → 201.
- `POST /api/commercial-policy/directives` — cria DIRECTIVE (papel diretor; justificativa obrigatória) → 201.
- `GET /api/commercial-policy/rules?key=&layer=&scope=` → lista (auditoria/curadoria).
- OpenAPI atualizada. (O Quoting consome via **porta** `MarkupProvider`, não via HTTP.)

## Events

- `ParameterRuleDefined` — `{ruleId, key, layer, scope, occurredAt}`. Produtor: `commercial-policy`.
  Consumidor: `intelligence` (que diretiva mudou a margem).
- `DirectiveIssued` — `{ruleId, key, definedBy, justification, occurredAt}` (auditoria reforçada).

## Persistence Changes

```txt
V18__create_commercial_policy.sql   -- número real na sequência Flyway (V14..V17 já usadas); ver DL-0037
  parameter_rules(
    id uuid PK, parameter_key varchar not null, layer varchar not null,
    scope_account_id uuid null, scope_product_ref varchar null, scope_channel varchar null,  -- matcher
    value_text varchar not null, value_type varchar not null,         -- NUMBER/PERCENT/MONEY/BOOL
    valid_from date not null, valid_until date null,
    defined_by varchar not null, justification varchar null,
    created_at, updated_at timestamptz not null, version bigint not null,
    INDEX ix_param_rules_key_layer (parameter_key, layer)
  )
-- seed: SYSTEM_DEFAULT para MARKUP_PCT, FX_DRIFT_LIMIT, RECON_DISCREPANCY_TOL (dado de sistema, BR4)
```

A resolução é **read-model/consulta** sobre `parameter_rules`. A porta `MarkupProvider` (definida no
Quoting) é **implementada aqui**, fechando o stub da SPEC-0005 sem o Quoting saber de precedência.

## Validation Rules

- Application: toda key consultada tem SYSTEM_DEFAULT (BR4); vigências coerentes (`validFrom ≤ validUntil`).
- Domain: ordenação de precedência + especificidade de escopo (BR2/BR3) como invariante testável.
- Segurança: DIRECTIVE exige papel diretor + justificativa (BR5).

## Error Behavior

`policy.parameter.unknown` → 404 (sem SYSTEM_DEFAULT); `policy.rule.invalid` → 400 (vigência/valor
malformado); `policy.directive.forbidden` → 403 (sem papel). i18n em `messages_pt_BR.properties`.

## Observability Requirements

- Logar `DirectiveIssued`/`ParameterRuleDefined` como eventos de negócio (key, layer, autor, correlation
  id). Métrica `policy_resolutions_total{key,layer}` (qual camada está vencendo na prática).

## Tests Required

- **Unit/domain:** precedência entre camadas (DIRECTIVE bate POLICY mesmo com escopo menos específico);
  especificidade dentro da camada (produto > agência > global); fallback ao SYSTEM_DEFAULT.
- **Integração (Testcontainers):** `resolve` retorna valor + proveniência corretos; criar DIRECTIVE
  passa a vencer; key sem default → 404.
- **Quoting (regressão de fronteira):** o markup do Quoting passa a vir do motor (proveniência ≠
  SYSTEM_DEFAULT quando há promoção/diretiva) — substitui o stub da SPEC-0005.

## Acceptance Criteria

- `resolve(MARKUP_PCT, conta X)` retorna o valor da camada vencedora com proveniência.
- Uma diretiva do diretor passa a vencer promoção/contrato/política para o escopo definido, e fica auditada.
- O Quoting compõe usando esse markup (não mais o default fixo).
- `./mvnw verify` verde.

## Open Questions

- **Q5 (escopo da comissão do agente)** — ASSUMIDO **parcialmente** (ver DL-0039 / BR9): o motor
  desta spec **comporta** a comissão do agente como parâmetro governado por escopo (default global),
  mas **seu consumo no Commissioning é da SPEC-0004** — segue **aberto lá**, não nesta spec.
- ~~**Q8 (operador edita regra em runtime?)**~~ → **ASSUMIDO (ver DL-0038 / BR7)**: self-service para
  parâmetros e diretivas (auditável; papel diretor p/ diretiva); fluxos não.
- O **conjunto final de `parameterKey`** segue evoluindo por spec dona (SLA/ISS/etc. entram com
  SPEC-0018/0016…); o seed atual cobre as chaves **já usadas** (ASSUMIDO, ver DL-0039 / BR9).

## Out of Scope

Preços de produto (não existem aqui), cálculo de comissão (SPEC-0004), composição de cotação (SPEC-0005).
