# 0031 - Cadastro (Registro de Dados de Referência)

Status: Approved
Related ADRs: 0012, 0016, 0019
Related DLs: DL-0115 (padrão enum→cadastro, 18a); DL-0116 (enums Marketing/Intelligence/Portfolio,
18b); DL-0085 (kind→entryType); DL-0044 (regime→estratégia)

> Convenções herdadas da **SPEC-0001**. **Subdomínio de suporte** transversal: um **registry
> genérico** de dados de referência editáveis (`cadastro_item`) que substitui os enums de negócio que
> **não** são máquina de estado nem imutáveis por lei. O valor persistido pelos módulos vira um
> **`code` validado** (= nome do antigo constante do enum) ⇒ **o contrato REST/JSON não muda**.

## Goal

Transformar os **enums de referência** (dados que o negócio quer poder editar: naturezas de despesa,
tipos de fornecedor/ativo, retenções, regime tributário, …) em **cadastros administráveis** — com
rótulo em pt-BR, ativação/desativação e ordenação — sem quebrar nenhum contrato de fio e sem
introduzir FK cross-contexto nem ciclo no grafo Spring Modulith. Dar ao operador (papel admin) uma
tela **"Cadastros"** para listar os tipos e manter os itens de cada tipo.

## Scope

**Em escopo (fatia 18a):**

- O módulo `cadastro` (23º módulo Modulith): a entidade `CadastroItem`
  (`id, type, code, label, active, sortOrder, createdAt, …`, unique `(type, code)`), o catálogo
  `CadastroType` (o conjunto de tipos conversíveis), o repositório, o `CadastroService` (listar
  tipos; listar/criar/atualizar/desativar itens — `code` imutável após semeado; `label`/`active`/
  `sortOrder` editáveis), e a **porta pública `CadastroValidator`** que os demais módulos usam para
  validar um `code` (existe + ativo para o tipo).
- REST: `GET /api/cadastro/types`, `GET /api/cadastro/items?type=…`, `POST /api/cadastro/items`,
  `PUT /api/cadastro/items/{id}`, `DELETE /api/cadastro/items/{id}` (desativa). Escritas gated por
  **`ROLE_POLICY_ADMIN`** (papel existente — DL-0115, sem inventar papel novo).
- Migração **V33**: cria `cadastro_item` e **semeia** os valores atuais dos enums convertidos nesta
  fatia (Admin/Assets/Billing), `code`=nome do enum, `label`=rótulo pt-BR.
- **Conversão dos enums Admin/Assets/Billing** para `code` validado (ver *Business Rules*).
- Tela **"Cadastros"** no shell (nav gated pelo papel admin) e ajuste das telas Admin/Assets/Billing
  para exibir rótulo do cadastro.

**Entregue (fatia 18b):** os grupos **Marketing** (`ConsentPurpose`, `SubjectType`), **Intelligence**
(`SubjectKind`, `InsightType`, `Verdict`) e **Portfolio** (`GoalMetric`), convertidos reusando este
módulo (DL-0116). As telas Marketing/Intelligence/Portfolio passam a exibir o **rótulo** do cadastro
(lookup no frontend), retro-corrigindo o seam que 18a deixou (telas mostravam o code). Migração
**V34** semeia os valores atuais.

**Fora de escopo (18c–18d):** os demais grupos de enums (Compliance/People/Booking/Payout/
AfterSales/…), convertidos por grupo nas fatias seguintes, reusando este módulo.

**Nunca convertido:** máquinas de estado (`*Status`/lifecycle), técnicos (`*FailureClass`,
circuit-breaker) e fixados por lei (`LegalType`, `LegalBasis`). O `EntryType` do Finance permanece
enum (chave de contrato entre Finance e Compliance — não é dado de referência editável pelo operador).

## Business Context

Enums de negócio são convenientes no código, mas **congelam o conjunto**: acrescentar uma natureza de
despesa ou um tipo de ativo exigiria recompilar. O dono quer que esses conjuntos sejam **dados
editáveis** — como cadastros — mantendo os rótulos em pt-BR. O desafio é fazê-lo **sem** quebrar os
contratos já publicados (o JSON continua o mesmo `string`) e **sem** acoplar os módulos (o `code` é um
**valor** validado por uma porta, nunca uma FK).

## Business Rules

```txt
BR1  CadastroItem MUST ter type (∈ CadastroType), code (imutável), label, active (bool),
     sortOrder (int) e createdAt. Unique (type, code). Nasce active=true.
BR2  Um code é IMUTÁVEL após criado; label, active e sortOrder são editáveis. Desativar (active=false)
     ou DELETE = soft (nunca apaga o registro semeado — preserva histórico e round-trip).
BR3  Validar um code (na escrita de outro módulo) = existe um CadastroItem (type, code) com active=true.
     Falha → CadastroCodeInvalidException (422, chave i18n cadastro.code.invalid).
BR4  Converter um enum de referência: o campo persistido vira String code (= nome do antigo constante);
     DTOs/views/eventos usam String. O contrato JSON NÃO muda (invariante DL-0115).
BR5  Lógica que ramifica por valor MUST preservar o comportamento com constantes de code (classes
     *Codes): AdminExpenseCodes.entryTypeFor (DL-0085); TaxRegimeCodes (regime→estratégia, DL-0044);
     WithholdingKindCodes (retenções federais). Um code novo sem lógica cablada funciona como dado puro.
BR6  As escritas do cadastro (POST/PUT/DELETE) MUST exigir ROLE_POLICY_ADMIN; as leituras exigem
     autenticação. O backend é a autoridade (security.md); a nav só esconde ruído de menu.
BR7  Sem FK cross-contexto: o code cruza a fronteira como valor, validado pela porta CadastroValidator.
     Direção da dependência: admin/assets/billing → cadastro (folha). Grafo acíclico (Modulith).
BR8  V33 semeia os valores atuais dos enums convertidos (Admin/Assets/Billing) com code=nome do enum e
     label pt-BR; migração idempotente; nunca editar uma migração aplicada.
```

### Enums convertidos nesta fatia (18a)

| Módulo  | CadastroType            | Codes (semeados)                                             | Ramificação preservada |
|---------|-------------------------|-------------------------------------------------------------|------------------------|
| admin   | `ADMIN_EXPENSE_KIND`    | UTILITY, AUTONOMOUS_SERVICE, SERVICE, OTHER                  | `AdminExpenseCodes.entryTypeFor` (DL-0085) |
| admin   | `ADMIN_RECURRENCE`      | MONTHLY, YEARLY, OTHER                                       | — (dado puro) |
| admin   | `ADMIN_SUPPLIER_TYPE`   | UTILITY, SOFTWARE, SERVICE, OTHER                            | — (dado puro) |
| assets  | `ASSET_TYPE`            | EQUIPMENT, SOFTWARE_LICENSE, OTHER                           | `SOFTWARE_LICENSE` exige `expiresAt` (BR1 SPEC-0021) |
| billing | `WITHHOLDING_KIND`      | IRRF, PIS, COFINS, CSLL, ISS_RETIDO                         | `WithholdingKindCodes` (codec) |
| billing | `TAX_REGIME`            | SIMPLES_NACIONAL, LUCRO_PRESUMIDO, LUCRO_REAL               | `TaxRegimeCodes`→estratégia (DL-0044) |

### Enums convertidos nesta fatia (18b — DL-0116)

| Módulo       | CadastroType             | Codes (semeados em V34)              | Ramificação preservada |
|--------------|--------------------------|--------------------------------------|------------------------|
| marketing    | `CONSENT_PURPOSE`        | NEWSLETTER                           | `MarketingCodes.NEWSLETTER` (base de envio; varredura da anonimização LGPD) |
| marketing    | `MARKETING_SUBJECT_TYPE` | ACCOUNT, AGENT                       | — (valor/chave de consulta) |
| intelligence | `INSIGHT_SUBJECT_KIND`   | AGENCY, ROUTE, PRODUCT, SUPPLIER     | `IntelligenceCodes.AGENCY` (produzido em v1) |
| intelligence | `INSIGHT_TYPE`           | PROMO_FX_ADVISOR, OVERRIDE_NUDGE     | `IntelligenceCodes.PROMO_FX_ADVISOR` (chave do upsert) |
| intelligence | `INSIGHT_VERDICT`        | CONVERTE, QUEIMA_MARGEM              | `IntelligenceCodes` (regra da guardrail no `isValid`; narrator) |
| portfolio    | `GOAL_METRIC`            | VOLUME, REVENUE                      | `GoalMetricCodes` (VOLUME←BookingConfirmed, REVENUE←SpreadRealized; projeção DL-0062) |

> Os três tipos `INSIGHT_*` são **produzidos pelo sistema** (o DSS os cunha de eventos consumidos;
> nunca chegam como payload de criação): não há validação de escrita, mas são cadastros para que os
> rótulos sejam editáveis e as telas mostrem o label. Marketing (`CONSENT_PURPOSE`/
> `MARKETING_SUBJECT_TYPE`) e Portfolio (`GOAL_METRIC`) **são validados na escrita** pela porta
> `CadastroValidator` (422 em código inválido/inativo).

## Tests Required

- **Unit:** `CadastroItem` (code imutável; label/active editáveis; sortOrder); `CadastroService`
  (criar/atualizar/desativar; rejeita code duplicado; `validate` aceita ativo e rejeita
  inativo/inexistente); `AdminExpenseCodes.entryTypeFor` (todos os codes + fallback OTHER);
  `TaxRegimeCodes`/`WithholdingKindCodes` (round-trip do codec).
- **Integration (Testcontainers/Postgres):** CRUD de cadastro via REST (papel admin cria/edita/
  desativa; não-admin recebe 403); listagem de tipos e de itens por tipo; a conversão faz
  **round-trip do mesmo JSON** (registrar fornecedor/ativo/nota com o `code` retorna o mesmo string);
  **code inválido/inativo é rejeitado (422)**; a lógica que ramificava continua (despesa UTILITY →
  UTILITY_EXPENSE no Finance; Simples → sem retenções).
- **Arquitetura:** o grafo Modulith continua acíclico com o 23º módulo; `@ModuleInternal` da entidade
  não vaza; nenhum módulo depende do repositório do `cadastro` (só da porta).
- **E2E (Playwright):** admin edita um item na tela "Cadastros"; um não-admin vê o estado de permissão.

## Acceptance Criteria

- AC1: `GET /api/cadastro/types` lista os `CadastroType` desta fatia; `GET /api/cadastro/items?type=…`
  lista os itens semeados (ativos primeiro, por `sortOrder`).
- AC2: Com `ROLE_POLICY_ADMIN`, `POST`/`PUT`/`DELETE` criam/editam/desativam itens; sem o papel → 403.
- AC3: Registrar um `AdminSupplier`/`Asset`/`CommissionInvoice` com um `code` conhecido retorna o
  **mesmo JSON de antes** (contrato inalterado); um `code` desconhecido/inativo é **rejeitado (422)**.
- AC4: A despesa `UTILITY` continua gerando `UTILITY_EXPENSE` no Finance; o regime `SIMPLES_NACIONAL`
  continua sem retenções (ramificação preservada).
- AC5: A tela "Cadastros" (papel admin) lista tipos, lista/edita/ativa/desativa itens; sem o papel, a
  tela mostra o estado de permissão e o item some da navegação.

## Open Questions

- **Quais cadastros são obrigatórios/travam operações** (ex.: mínimo de itens ativos por tipo) — em
  aberto; hoje o cadastro é livre (o operador não pode desativar o último ativo? decisão adiada, não
  trava 18a). Registrar em fatia futura se o dono exigir trava.
- **Escopo por tenant/filial** dos cadastros — fora de escopo (monólito single-tenant hoje).
