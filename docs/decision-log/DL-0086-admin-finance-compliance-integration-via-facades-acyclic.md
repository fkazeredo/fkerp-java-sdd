# DL-0086 — Admin integra Finance/Compliance por fachada/porta; idempotente; grafo acíclico

- **Fase:** 8l (Admin)
- **Spec(s):** SPEC-0025 (BR3/BR4); SPEC-0015 (Finance facade); SPEC-0008 (Compliance `DocumentRequirement`)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A BR3 manda Admin **criar** um `LedgerEntry` PAYABLE no Finance e **sinalizar** os documentos
exigidos pelo Compliance, mas a spec não fixa **como** (chamada síncrona × evento) nem como garantir
**idempotência** (não duplicar lançamento por despesa) nem como **listar** os documentos exigidos —
hoje o `ComplianceService.requiredDocumentTypes` é **privado** (não há porta pública). Também é
preciso garantir que o módulo novo não crie ciclo no grafo Modulith.

## Decisão

1. **Geração do lançamento — chamada síncrona à fachada do Finance.** O `AdminService` chama
   `FinanceService.register(PAYABLE, party, amount, entryType, period, actor)` (fachada pública já
   existente, SPEC-0015) e guarda o `financeEntryId` retornado **por valor** na `admin_expenses`
   (sem FK cross-módulo). Não há evento intermediário: a despesa administrativa registrada **é** o
   gatilho do lançamento, na mesma transação. (BR4: Admin **não** impõe documento nem fecha período —
   só gera o lançamento e referencia o documento.)
2. **Idempotência por despesa** — UNIQUE `(supplier_id, period, kind)` em `admin_expenses` +
   pré-check; uma segunda tentativa de registrar a mesma despesa do mesmo fornecedor/período/tipo →
   `admin.expense.duplicate` (409), **sem** criar um segundo lançamento. (SPEC-0025 *Validation*:
   "criação idempotente do lançamento — não duplica por despesa".)
3. **Documentos exigidos — nova porta de leitura do Compliance** `DocumentRequirementDirectory`
   (interface pública no módulo `compliance`, implementada pelo `ComplianceService`):
   `List<String> requiredAtRegistration(String entryType)`. O Admin a consome para devolver
   `requiredDocuments` na resposta da despesa. É **leitura** (espelha o padrão da porta
   `LedgerDirectory` do Finance consumida pelo Compliance) — Admin **referencia**, não impõe (o veto
   continua sendo Finance+Compliance).
4. **Admin é folha consumidora.** Depende das **fachadas** `FinanceService` + porta
   `DocumentRequirementDirectory` (Compliance) + kernels `money`/`error`; **nenhum** desses depende
   de volta de Admin. Grafo permanece **acíclico** (Spring Modulith verify). Admin **não** consome
   eventos de outros módulos para postar custo.

## Justificativa

- **`modules-and-apis.md` / Modulith:** colaboração síncrona é **só** por fachada/porta pública; é
  exatamente o que o Billing/AfterSales fazem ao chamar Finance/Payout. A `register` do Finance já é
  pública e usada pelos controllers — reusá-la evita um seam novo.
- **`persistence.md` (idempotência por constraint antes de infra complexa):** a UNIQUE
  `(supplier_id, period, kind)` é a defesa natural contra duplo-registro; o pré-check dá o erro de
  negócio traduzido (sem vazar exceção crua de banco).
- **Simetria com `LedgerDirectory`:** o Compliance já lê o Finance por porta de valor; expor uma
  porta de leitura simétrica para os requisitos é o caminho idiomático do projeto (sem tornar
  público o serviço inteiro, sem FK).
- **Acíclico:** Finance/Compliance não conhecem Admin; Admin só os chama. Mesma forma de
  `aftersales → {booking, payout, commercialpolicy}` (DL-0054) e `billing` folha (DL-0047).

## Alternativas descartadas

- **Postar via evento `AdminExpenseRegistered` consumido pelo Finance:** introduz consistência
  eventual e idempotência de listener sem necessidade — a despesa e o lançamento são **um** ato
  atômico do mesmo usuário; chamada síncrona é mais simples e correta aqui (Regra Zero). O evento
  `AdminExpenseRegistered` **ainda é publicado** (SPEC-0025 Events) para `compliance`/`intelligence`
  rastrearem, mas **não** é o mecanismo de criação do lançamento.
- **Tornar `ComplianceService` público inteiro ou ler a tabela `document_requirements` direto:**
  violaria a fronteira (outro módulo não toca a persistência do Compliance). A porta de leitura é a
  forma correta.
- **Idempotência por tabela `posted_event_entries` (como o Finance faz para eventos):** é para
  consumo de **eventos** re-entregues; aqui o gatilho é uma chamada de usuário única — a UNIQUE de
  negócio `(supplier, period, kind)` é suficiente e mais clara.

## Impacto

- **Specs:** SPEC-0025 BR3 — confirma chamada síncrona à fachada + idempotência por
  `(supplier, period, kind)`.
- **Arquivos:** nova porta `com.fksoft.domain.compliance.DocumentRequirementDirectory` (+
  implementação no `ComplianceService`); `AdminService` injeta `FinanceService` +
  `DocumentRequirementDirectory`.
- **Migração:** UNIQUE `ux_admin_expenses_supplier_period_kind` em `admin_expenses`.
- **Contratos:** `POST /api/admin/expenses` → 201 com `financeEntryId` + `requiredDocuments`; duplicada
  → 409 `admin.expense.duplicate`.
- **Modulith:** Admin (22º) é folha; `compliance` ganha um `@NamedInterface` de leitura novo
  (a porta), sem virar dependente de Admin.

## Como reverter

Se o dono preferir consistência eventual, troca-se a chamada síncrona por publicação de
`AdminExpenseRegistered` + listener idempotente no Finance (padrão DL-0041), mantendo a UNIQUE de
negócio. Refactoring **moderado** e localizado (o contrato REST não muda — `financeEntryId` passaria
a ser preenchido de forma assíncrona). A porta `DocumentRequirementDirectory` permanece útil em
qualquer cenário.
