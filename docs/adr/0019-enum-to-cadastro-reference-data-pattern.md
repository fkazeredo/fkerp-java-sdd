# ADR 0019: Enums de referência viram cadastros (registry `cadastro_item` + `code` validado por porta)

## Status

Accepted (Fase 18a — SPEC-0031; DL-0115). Sem mudança de contrato de fio. Refina ADR 0012
(camadas/fronteiras) e ADR 0016 (`@ModuleInternal`).

## Context

O código nasceu com muitos **enums de negócio** (`AdminExpenseKind`, `AdminSupplierType`,
`AdminRecurrence`, `AssetType`, `WithholdingKind`, `TaxRegime`, e outros nas fatias seguintes). Enum é
ótimo para **valores fixos por lei ou máquina de estado**, mas para **dados de referência que o negócio
quer editar** (naturezas, tipos, rótulos) ele **congela o conjunto**: acrescentar um valor exige
recompilar e redeployar. O dono decidiu (ROADMAP Fase 18): **todo enum que não seja máquina de estado
nem imutável por lei vira cadastro** — dado editável, com rótulo em pt-BR e ativação.

A restrição forte é o **contrato**: esses enums aparecem no JSON de REST já publicado (ex.:
`"type":"UTILITY"`, `"regime":"SIMPLES_NACIONAL"`). Converter **não pode** mudar o fio. E o desenho
não pode introduzir **FK cross-contexto** nem **ciclo** no grafo Spring Modulith.

Fatos técnicos que governam a decisão:

- Os enums são gravados como `@Enumerated(STRING)` — a coluna já guarda **o nome do constante**. Trocar
  o tipo do campo para `String` mantendo o mesmo valor é **transparente para o banco e para o JSON**.
- Parte da lógica **ramifica** por valor: `AdminExpenseKind→EntryType` (DL-0085) decide o documento
  exigido; `TaxRegime→TaxRegimeStrategy` (DL-0044) decide o cálculo de imposto; `WithholdingKind`
  dirige o codec de retenções. Essa lógica **não pode** virar dado — precisa de constantes.
- O padrão de "valor cruza a fronteira + porta pública valida" já é usado e provado no projeto
  (`DocumentRequirementDirectory` do Compliance; `FinanceService`/`LedgerDirectory`).

## Decision

1. **Módulo `cadastro` (23º Modulith), folha.** Uma tabela única `cadastro_item(id, type, code,
   label, active, sort_order, created_at, …)` com unique `(type, code)`; um catálogo `CadastroType`
   (enum técnico interno — o conjunto de tipos conversíveis, que **não** é dado de referência: é a
   chave do registry). `CadastroService` lista tipos e faz o CRUD dos itens; `code` é **imutável**
   após criado, `label`/`active`/`sort_order` são editáveis; `DELETE` **desativa** (soft), nunca apaga.

2. **Porta pública `CadastroValidator`.** Os módulos de negócio validam um `code` na escrita
   (`existe + ativo para o type`) via essa porta injetada — **valor**, nunca FK. Direção da
   dependência: `admin`/`assets`/`billing` → `cadastro`. O `cadastro` **não depende de nenhum módulo
   de negócio** → grafo acíclico.

3. **Conversão transparente (invariante).** O campo `@Enumerated` vira `String code` (mesma coluna,
   mesmos valores). Os DTOs (request/response), views e eventos passam a usar `String`. O **JSON não
   muda**. Um `code` novo (sem lógica cablada) **flui como dado puro** — não há mais `@NotNull enum`
   que rejeitaria valores fora do conjunto compilado.

4. **Ramificação preservada por constantes.** Onde o domínio decide comportamento por valor, uma
   classe `*Codes` no próprio módulo guarda **apenas** o conjunto que a lógica precisa:
   `AdminExpenseCodes.entryTypeFor(code)`, `TaxRegimeCodes`, `WithholdingKindCodes`. O cadastro é a
   **fonte do conjunto extensível + rótulos**; as constantes guardam **só o comportamento cablado**.
   Um `code` novo sem mapa cai num fallback seguro documentado (ex.: `OTHER_EXPENSE`).

5. **Autorização.** As escritas `POST/PUT/DELETE /api/cadastro/**` exigem **`ROLE_POLICY_ADMIN`**
   (papel já existente — DL-0082); não se inventa papel novo nem se toca o auth logo após a Fase 17.
   Cadastro de dados de referência é governança de política, coerente com o papel.

6. **Migração V33** cria a tabela e **semeia** os valores atuais dos enums convertidos na fatia
   (`code`=nome do enum, `label` pt-BR), idempotente.

## Consequences

**Positivas**

- O negócio passa a editar o conjunto e os rótulos sem redeploy; um único mecanismo (registry + porta)
  cobre todos os enums de referência — **Regra Zero** (sem tabela por enum).
- **Zero mudança de contrato**: o fio permanece `string`; a OpenAPI dos campos convertidos não muda
  (só entram os novos `/api/cadastro/*`).
- Fronteiras intactas: sem FK cross-contexto, grafo Modulith acíclico, encapsulação por
  `@ModuleInternal` — os mesmos portões da ADR 0012/0016 continuam de pé.

**Custos / limites**

- Perde-se a checagem de exaustividade do compilador (o `switch` sobre enum) onde havia ramificação —
  mitigado pelas classes `*Codes` com fallback explícito e testes que cobrem cada código.
- Um `code` inválido/inativo só é barrado **em runtime** (422 via `CadastroValidator`), não em
  compile-time — é o preço de tornar o conjunto editável (e é testado).
- As fatias 18b–18d convertem os demais grupos reusando este módulo; até lá, enums não convertidos
  continuam enums (coexistência sem dívida).

## Alternatives considered

- **Tabela por enum.** Rejeitada: multiplica schema e código sem ganho; o registry genérico basta.
- **FK de cada tabela para `cadastro_item(id)`.** Rejeitada: FK cross-contexto é proibida; o `code` é
  valor validado por porta.
- **Manter o enum e só espelhar rótulos.** Rejeitada: não torna o conjunto editável (novos valores
  exigiriam recompilar) — contraria a decisão do dono.
- **Converter também a lógica de ramificação para dado.** Rejeitada: perderia o comportamento
  determinístico (kind→documento, regime→imposto). Compromisso: cadastro = conjunto+rótulos;
  constantes = comportamento.
