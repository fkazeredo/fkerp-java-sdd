# DL-0084 — Admin é módulo Modulith próprio, registro enxuto; procurement = comprar (fronteira)

- **Fase:** 8l (Admin)
- **Spec(s):** SPEC-0025 (Admin); OVERVIEW Parte 5 (linha 138/165 — genérico); relacionada à DL-0064 (Assets, mesmo padrão de genérico enxuto)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0025 é um **subdomínio genérico** ("avaliar comprar", OVERVIEW linha 138/165). A *Open
Question* pergunta se o **procurement completo** (cotação/aprovação de compra) deve ser construído.
Sem decidir, não dá para saber o escopo do módulo (cadastro enxuto × sistema de compras) nem se
Admin é um módulo Modulith próprio ou parte de outro (Finance? Platform?).

## Decisão

**Novo módulo Spring Modulith próprio** `com.fksoft.domain.admin` (o **22º** módulo de negócio),
entregue como **registro enxuto** — o "balcão administrativo": o agregado `AdminSupplier`
(fornecedor administrativo: UTILITY | SOFTWARE | SERVICE | OTHER), o `AdminContract` (vigência,
recorrência, valor, documento de contrato no Compliance por valor) e o registro de **despesa
recorrente** que **gera o lançamento** no Finance e **referencia** o documento exigido pelo
Compliance. **Procurement completo (cotação, aprovação de compra, ordem de compra) fica FORA de
escopo** — se exigido, **compra-se** um sistema de compras; este módulo permanece como
cadastro/seam que alimenta Finance/Compliance.

## Justificativa

- **SPEC-0025 *Scope*/*Out of Scope*/*Open Questions*:** explicitamente "**Fora de escopo:**
  compras/cotação de fornecedores (procurement completo — comprar, se exigido)" e a Open Question
  registra "se exigido, **comprar**; este módulo fica como cadastro/seam — decisão do dono".
- **Regra Zero (`CLAUDE.md` invariante 1; `core-principles.md`):** entrega só o que a spec pede;
  um motor de compras é complexidade especulativa que não resolve um problema atual.
- **Consistência com os genéricos já entregues (Finance/DL-0014, Assets/DL-0064, Identity/DL-0080):**
  o padrão do projeto para genéricos é **fronteira + seam + decisão comprar-vs-construir**, não um
  sistema caseiro completo.
- **OVERVIEW Parte 5:** lista `Admin` (Generic) como linha distinta do mapa de subdomínios
  ("contratos, fornecedores administrativos"), com linguagem/ciclo/dono próprios — módulo próprio.

## Alternativas descartadas

- **Construir procurement completo (cotação/aprovação/ordem de compra):** complexidade alta,
  especulativa, fora do escopo da spec; viola a Regra Zero e a Open Question (que manda comprar).
- **Sub-pacote de Finance ou Platform:** Admin tem identidade, ciclo de vida (fornecedor/contrato/
  despesa), eventos e linguagem ubíqua próprios; seria um módulo de negócio disfarçado, ferindo a
  clareza de fronteira. Finance é dono do **razão**, não do **cadastro de fornecedor administrativo**.

## Impacto

- **Specs:** SPEC-0025 — mover a Open Question de procurement de *Open Questions* para *Business Rules*
  ("ASSUMIDO (ver DL-0084)").
- **Arquivos:** novo módulo `com.fksoft.domain.admin` (+ `package-info.java` com `@ApplicationModule`),
  `AdminController` em `application.api`, DTOs em `application.api.dto`.
- **Migração:** `V30__create_admin.sql` (tabelas `admin_suppliers`, `admin_contracts`, `admin_expenses`).
- **Contratos:** novos endpoints `/api/admin/*`; OpenAPI atualizada.
- **Modulith:** 22º módulo de negócio; grafo deve permanecer acíclico (Admin depende de Finance/
  Compliance por fachada/porta; nenhum depende de volta — ver DL-0086).

## Como reverter

Caso o dono decida construir/comprar procurement completo: o cadastro de fornecedor/contrato/despesa
permanece como a **base** e ganha um módulo de compras por cima (cotação → ordem de compra →
recebimento), que **publica** os mesmos fatos de despesa que Admin hoje gera. Refactoring
**moderado** (novo módulo adjacente, sem reescrever o cadastro). Se for compra de pacote externo,
Admin vira o **adaptador** de sincronização. O seam (geração de lançamento + referência de documento)
sobrevive à troca.
