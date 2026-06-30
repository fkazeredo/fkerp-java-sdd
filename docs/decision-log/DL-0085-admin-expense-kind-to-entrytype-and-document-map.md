# DL-0085 — Mapa despesa (kind) → entryType → DocumentRequirement do administrativo

- **Fase:** 8l (Admin)
- **Spec(s):** SPEC-0025 (Admin BR3); SPEC-0015 (Finance, `EntryType`); SPEC-0008 (Compliance, `DocumentRequirement` — seed V8); relacionada à DL-0012
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0025 *Open Questions* deixa em aberto o "**mapa final tipo de despesa → entryType →
DocumentRequirement** — compartilhado com Finance/Compliance". A BR3 exige que registrar uma despesa
recorrente crie o `LedgerEntry` com o `entryType` correto **pelo tipo** (UTILITY → UTILITY_EXPENSE;
autônomo → AUTONOMOUS_SERVICE; PJ → SERVICE) e sinalize o(s) documento(s) exigido(s). Mas o catálogo
de `EntryType` do Finance (DL-0012/V8) **não tem** um tipo `SERVICE`; e o exemplo "PJ → SERVICE" da
spec precisa casar com um `entryType` real já reconhecido pelo Compliance.

## Decisão

O `kind` da despesa administrativa é um enum **do Admin** (`AdminExpenseKind`: `UTILITY`,
`AUTONOMOUS_SERVICE`, `SERVICE`, `OTHER`), e mapeia para o `EntryType` do Finance e (via Compliance)
para os documentos exigidos assim:

| `AdminExpenseKind` | Finance `EntryType` | Documentos exigidos (AT_REGISTRATION) |
|---|---|---|
| `UTILITY` (água/luz/telefone) | `UTILITY_EXPENSE` | `UTILITY_BILL` (+ `PAYMENT_PROOF` na liquidação) |
| `AUTONOMOUS_SERVICE` (autônomo PF) | `AUTONOMOUS_SERVICE` | `RPA` |
| `SERVICE` (software/serviço PJ) | `SERVICE` | `NFSE` |
| `OTHER` | `OTHER_EXPENSE` | — (nenhum obrigatório no registro) |

Para isso, **adiciona-se ao catálogo do Finance** os `EntryType` `SERVICE` e `OTHER_EXPENSE`
(aditivo — não muda os existentes), e **ao seed do Compliance** (nova migração de seed, sem editar a
V8 já aplicada) as linhas `('SERVICE','NFSE','AT_REGISTRATION')` e
`('SERVICE','PAYMENT_PROOF','AT_SETTLEMENT')`. `UTILITY_EXPENSE`, `AUTONOMOUS_SERVICE` já existem na
V8 e são reusados. `OTHER_EXPENSE` deliberadamente **não** tem requirement (despesa genérica sem
documento hábil padronizado — o dono anexa o que for cabível).

## Justificativa

- **SPEC-0025 BR3** define o mapeamento por tipo (UTILITY → UTILITY_EXPENSE; autônomo →
  AUTONOMOUS_SERVICE; PJ → SERVICE); esta decisão só **materializa** os `entryType` que faltavam e
  amarra os documentos pela tabela 7.7 do redesenho (NF-e/NFS-e entre PJ; RPA para autônomo;
  fatura+comprovante para consumo).
- **DL-0012 / V8 (Compliance):** o catálogo é **dado de sistema, extensível por nova migração de
  seed sem mexer no código** — exatamente o caminho usado aqui (V30 acrescenta o seed do `SERVICE`).
  `UTILITY_EXPENSE`/`AUTONOMOUS_SERVICE` já estão seedados; reusá-los evita duplicar regra.
- **OVERVIEW tabela 7.7:** "Software/serviço PJ exige **NF**" → NFSE para serviço PJ municipal; conta
  de consumo "**fatura + comprovante**" → UTILITY_BILL (+ PAYMENT_PROOF na liquidação); autônomo →
  **RPA**.
- **Aditividade / `persistence.md`:** acrescentar valores de enum e linhas de seed é mudança
  retrocompatível (não altera lançamentos existentes nem o veto de fechamento dos tipos atuais).

## Alternativas descartadas

- **Reaproveitar `SUPPLIER_SETTLEMENT` para serviço PJ:** esse tipo é liquidação ao fornecedor de
  **turismo** (exige NFE no seed) e tem semântica de repasse comercial, não despesa administrativa
  recorrente; confundiria os relatórios e o veto.
- **Não criar `OTHER_EXPENSE` (mapear OTHER → algum tipo existente):** forçaria um documento
  inadequado ou um tipo enganoso; melhor um tipo explícito sem requirement.
- **Catálogo do `kind` no Finance (em vez de no Admin):** o `kind` é linguagem do balcão
  administrativo; o Finance só precisa do `EntryType` (valor que cruza a fronteira). Manter o `kind`
  no Admin respeita a fronteira (a tradução kind→EntryType mora no Admin).

## Impacto

- **Specs:** SPEC-0025 — mover a Open Question do mapa para *Business Rules* ("ASSUMIDO (ver DL-0085)");
  SPEC-0015 — nota de que o catálogo `EntryType` ganhou `SERVICE`/`OTHER_EXPENSE` (aditivo).
- **Arquivos:** `EntryType` (Finance) +2 valores; `AdminExpenseKind` (Admin) novo; tradução
  kind→EntryType no `AdminService`.
- **Migração:** `V30__create_admin.sql` inclui o `INSERT` no `document_requirements` do `SERVICE`
  (seed aditivo — a V8 não é editada).
- **Contratos:** `POST /api/admin/expenses` responde `requiredDocuments` derivados do mapa.

## Como reverter

Trocar o mapa é alterar a tabela acima (uma função pura `kind → EntryType` no `AdminService`) e o
seed de `document_requirements` (nova migração). Reversão **barata** e localizada — nenhum dado
histórico precisa migrar (lançamentos já criados guardam seu `entry_type`). Se a contabilidade do
cliente exigir outro casamento (ex.: serviço PJ municipal sem NFS-e, com NFE), troca-se a linha do
seed e o valor do mapa.
