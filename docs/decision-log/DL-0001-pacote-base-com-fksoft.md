# DL-0001 — Manter o pacote base `com.fksoft`

- **Fase:** 0 (Fundação)
- **Spec(s):** SPEC-0001
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Cara

## Lacuna

A SPEC-0001 (Open Questions) e o ROADMAP (FASE 0 → Travas) deixam em aberto se o
pacote base do template `com.fksoft` deve ser mantido ou renomeado para algo
específico do projeto (ex.: `com.acmetravel.erp`). É decisão do dono e "não bloqueia".

## Decisão

Manter o pacote base **`com.fksoft`** em todo o backend (`com.fksoft.domain`,
`com.fksoft.application`, `com.fksoft.infra`).

## Justificativa

- Toda a documentação de arquitetura e os ADRs (0001, 0010, 0011, 0012, 0013)
  usam `com.fksoft` de forma consistente como linguagem de referência. Manter o
  pacote evita divergência entre docs e código já no primeiro commit.
- O nome do pacote Java é um detalhe técnico sem impacto em regra de negócio; a
  "Acme Travel" e os fornecedores são placeholders anônimos (OVERVIEW.md), logo
  não há ganho de domínio em renomear agora.
- Renomear depois é um refactoring puramente mecânico (IDE move/rename), porém
  toca **todos** os arquivos e migrações/configs — por isso a reversibilidade é
  marcada **Cara** mesmo sendo de baixo risco conceitual: quanto mais código
  nascer sobre `com.fksoft`, maior o diff de um rename futuro.

## Alternativas descartadas

- **Renomear para `com.acmetravel.erp`** — descartada: "Acme Travel" é nome
  fictício/placeholder (OVERVIEW.md); fixaria um nome de marca falso no código.
- **Renomear para `dev.fkazeredo.erp`** — descartada: sem ganho funcional;
  diverge de toda a documentação existente.

## Impacto

- Specs: SPEC-0001 (item movido de Open Questions para Business Rules).
- Arquivos: estrutura inteira do backend (`backend/src/main/java/com/fksoft/...`),
  `pom.xml` (`groupId`), `application.yml`.
- Migrações/contratos: nenhum (o pacote Java não aparece em SQL nem na API).

## Como reverter

Rename do pacote raiz `com.fksoft` → novo nome via refactor da IDE, ajustar
`groupId`/`<mainClass>` no `pom.xml` e qualquer referência textual. Tamanho:
mecânico, porém amplo (todos os fontes). Recomenda-se fazer antes de a base de
código crescer; após várias fases, o diff é grande (daí Reversibilidade=Cara).
