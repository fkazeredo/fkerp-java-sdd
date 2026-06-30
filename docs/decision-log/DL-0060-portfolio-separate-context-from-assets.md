# DL-0060 — Portfolio é um contexto separado de Assets (Q2: dois contextos, não um)

- **Fase:** 8g (Portfolio — SPEC-0020)
- **Spec(s):** SPEC-0020 (Open Question Q2 "`Portfolio` + `Assets`: os dois ou um?"); SPEC-0021 (Assets).
- **ADR relacionado:** modules-and-apis.md (módulos por domínio de negócio: linguagem/regras/ciclo/dono
  próprios); core-principles.md (Regra Zero — não unir o que tem motivos diferentes para mudar);
  redesign Parte 5 (mapa de subdomínios: `Portfolio` Supporting × `Assets` Supporting/Generic).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0020 deixa em aberto (Q2) se `Portfolio` (o que a Acme **representa** comercialmente) e
`Assets` (o **patrimônio interno**: equipamentos, licenças) devem ser **um único** contexto de
"inventário" ou **dois** contextos distintos. A decisão define se este módulo nasce sozinho ou
fundido com a SPEC-0021.

## Decisão

**Dois contextos separados.** Esta fase entrega **apenas** `com.fksoft.domain.portfolio` (o 17º
módulo Modulith). O `Assets` (SPEC-0021) fica para uma fatia futura, como módulo próprio.

## Justificativa

- **ROADMAP "Recomendações para as Open Questions" (Q2)** recomenda **dois contextos separados**:
  "Perguntas diferentes (o que a Acme **representa** × **patrimônio** interno), linguagem/ciclo/dono
  distintos. Unir acoplaria comercial a TI. Assets (genérico) pode ser o último a entrar."
- modules-and-apis.md define módulo por **domínio de negócio** (linguagem, regras, ciclo de vida,
  dono e razões para mudar). Portfolio fala de **marca/contrato de representação/meta** (comercial,
  alimenta Quoting/Commissioning/DSS); Assets fala de **bem patrimonial/licença/depreciação** (TI/
  administrativo). São linguagens ubíquas e donos distintos.
- A própria SPEC-0020 (cabeçalho) já trata Assets como "distinto" e fora de escopo.

## Alternativas descartadas

- **Um único módulo `inventory` cobrindo representação + patrimônio.** Descartada: acoplaria o
  comercial (representação) ao administrativo de TI (patrimônio), violando a coesão por domínio e a
  Regra Zero (juntar o que muda por razões diferentes). Só faria sentido se o dono unificasse
  explicitamente "inventário" — não é o caso (a recomendação do arquiteto é manter separado).

## Impacto

- **Specs:** SPEC-0020 Q2 fechada como "ASSUMIDO (ver DL-0060): dois contextos". SPEC-0021 (Assets)
  permanece como fatia futura independente.
- **Arquivos:** novo pacote `com.fksoft.domain.portfolio` com `@ApplicationModule` (17º módulo).
- **Migração:** `V25__create_portfolio.sql` (só tabelas de Portfolio).
- **Modulith:** módulo `portfolio` independente; nenhuma fusão.

## Como reverter

Moderada: se o dono unificar "inventário", funde-se Portfolio e Assets num módulo; como ainda **não**
existe `Assets`, a reversão é só uma decisão de nomeação/empacotamento na fatia da SPEC-0021 (mover os
tipos de `portfolio` para um pacote comum) — sem migração de dado retroativa neste momento.
