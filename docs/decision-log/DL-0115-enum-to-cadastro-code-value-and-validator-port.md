# DL-0115 — Padrão enum→cadastro: valor persistido vira `code` (String) validado por porta pública do módulo `cadastro`

- **Fase:** 18a (módulo `cadastro` + conversão Admin/Assets/Billing)
- **Spec(s):** SPEC-0031 (nova); SPEC-0025 (Admin), SPEC-0021 (Assets), SPEC-0016 (Billing)
- **ADR relacionado:** ADR-0019 (padrão enum→cadastro)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

O dono decidiu que **todo enum de referência que não seja máquina de estado nem imutável por lei vira
cadastro** (dado editável em `cadastro_item`). Faltava definir **como** converter sem quebrar o
contrato de fio (REST/JSON) e sem introduzir FK cross-contexto nem ciclo no grafo Modulith, e o que
fazer com a lógica de domínio que **ramifica** por valores específicos (`AdminExpenseKind→EntryType`,
DL-0085; `TaxRegime→TaxRegimeStrategy`, DL-0044).

## Decisão

1. **Representação persistida:** o campo `@Enumerated(STRING)` do enum vira um **`String code`**
   (mesma coluna, mesmos valores — `code` = nome do constante do enum, ex.: `"UTILITY"`,
   `"SIMPLES_NACIONAL"`). O contrato JSON **fica idêntico** (era string de enum, continua string).
2. **DTOs (request/response), views e eventos:** os campos de enum viram **`String`**. Um request
   com `type: "UTILITY"` serializa igual; um `code` novo (sem lógica cablada) **passa como dado
   puro**. Sem `@NotNull enum` que rejeitaria códigos novos.
3. **Validação:** o módulo dono valida na escrita, contra o cadastro, que o `code` **existe e está
   ativo** para aquele `type`, via a porta pública **`CadastroValidator`** do módulo `cadastro`
   (injeção). Código inválido/inativo → `CadastroCodeInvalidException` (422, chave i18n). É um
   **valor** que cruza a fronteira — **sem FK cross-contexto** (persistence.md/modules-and-apis.md).
4. **Direção da dependência (grafo acíclico):** `admin`/`assets`/`billing` → `cadastro`
   (porta `CadastroValidator`). O módulo `cadastro` é **folha**: não depende de nenhum módulo de
   negócio. Isso mantém o Modulith acíclico (mesma direção de Admin→Finance/Compliance).
5. **Lógica que ramifica (preservada):** onde o domínio decide comportamento por valor, um pequeno
   **catálogo de constantes de `code`** (classe `*Codes` no próprio módulo) mantém o mapeamento:
   - `AdminExpenseCodes.entryTypeFor(code)` reproduz o antigo `AdminExpenseKind.entryType()`
     (DL-0085) — `UTILITY→UTILITY_EXPENSE`, `AUTONOMOUS_SERVICE→AUTONOMOUS_SERVICE`,
     `SERVICE→SERVICE`, `OTHER→OTHER_EXPENSE`. Um `code` novo sem mapa → `OTHER_EXPENSE`
     (fallback seguro, documentado — seam para 18+).
   - `TaxRegimeCodes` mantém `SIMPLES_NACIONAL` (o default DL-0044) e as constantes que a estratégia
     e o config port usam; a seleção de `TaxRegimeStrategy` continua por `code`.
   - `WithholdingKindCodes` guarda os cinco códigos federais (IRRF/PIS/COFINS/CSLL/ISS_RETIDO) que o
     `WithholdingsCodec` (de)serializa — a lista continua vazia no Simples (DL-0044).
6. **`code` imutável, `label`/`active`/`sort_order` editáveis:** um item semeado tem o `code`
   travado (não editável via API); o operador edita rótulo pt-BR, ativa/desativa e reordena. Novos
   códigos podem ser criados (dado puro) — se ninguém cablou lógica, funcionam como rótulo+valor.

## Justificativa

- **Invariante do dono (ROADMAP Fase 18):** "o valor persistido vira `code` validado com `code`=nome
  do enum ⇒ JSON de contrato inalterado". A decisão realiza isso literalmente.
- **Regra Zero:** um único mecanismo (registry `cadastro_item` + porta `CadastroValidator`) cobre
  todos os enums de referência; nada de tabela por enum. As constantes `*Codes` existem **só** onde
  há ramificação real — não se cria abstração sem problema.
- **Modulith/ArchUnit:** valor cruza a fronteira (`code:String`), porta pública valida; sem FK, sem
  ciclo — mesmo padrão já provado por `DocumentRequirementDirectory` (Compliance) e `FinanceService`.
- **Confiança=Alta:** o padrão é mecânico e testável (round-trip do JSON; rejeição de código
  inválido/inativo; ramificação preservada). **Reversibilidade=Moderada:** voltar a enum exigiria
  retipar campos/DTOs e uma migração, mas os valores no banco são idênticos.

## Alternativas descartadas

- **Manter o enum e só espelhar rótulos numa tabela.** Descartada: não torna o *conjunto* editável
  (novos códigos exigiriam recompilar), contrariando a decisão do dono.
- **FK de cada tabela para `cadastro_item(id)`.** Descartada: FK cross-contexto é proibida
  (Modulith/persistence.md); o `code` é valor, validado por porta.
- **Converter também os enums de ramificação para dado sem constantes.** Descartada: perderia a
  lógica determinística (kind→entryType, regime→strategy). O compromisso é: **cadastro é a fonte do
  conjunto extensível + rótulos; as constantes guardam só o comportamento cablado.**
- **Novo papel `ROLE_CADASTRO_ADMIN`.** Descartada: reusar **`ROLE_POLICY_ADMIN`** (já existe,
  DL-0082) evita mexer no auth logo após a Fase 17; cadastro de referência é governança de política.

## Impacto

- **Specs:** SPEC-0031 (nova). Notas em SPEC-0025/0021/0016 (campos passam a `code` validado).
- **Arquivos:** novo módulo `domain.cadastro` (entidade `CadastroItem`, `CadastroType`,
  `CadastroItemRepository`, `CadastroService`, porta `CadastroValidator`, exceções, views/commands);
  controller `CadastroController` + DTOs; `SecurityConfig` (matcher `POST/PUT/DELETE /api/cadastro/**`
  → `ROLE_POLICY_ADMIN`). Conversões em `admin`/`assets`/`billing` (entidades, DTOs, views, eventos,
  repos, serviços, `*Codes`). Frontend: feature `cadastro` + nav + rota; ajustes nas telas
  Admin/Assets/Billing para exibir rótulo do cadastro.
- **Migração:** **V33** cria `cadastro_item` (unique `(type, code)`) e semeia os valores atuais dos
  enums convertidos (Admin/Assets/Billing), `code`=nome do enum, `label` pt-BR.
- **Contratos:** **sem mudança de fio** — os campos convertidos continuam `string` no JSON; novos
  endpoints `/api/cadastro/*` documentados na OpenAPI.

## Como reverter

Retipar os campos/DTOs de volta para os enums, remover a porta/validação e a tabela `cadastro_item`
(migração de baixa) e apagar o módulo `cadastro`. Moderada: os valores persistidos são idênticos aos
nomes dos enums, então não há backfill de dados — só refator de tipos + drop de tabela.
