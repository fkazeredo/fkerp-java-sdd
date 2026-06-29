# DL-0012 — Catálogo `entryType × DocumentRequirement` (seed inicial)

- **Fase:** 2 (Compliance mínimo)
- **Spec(s):** SPEC-0008 (Open Question "Catálogo final de tipos de lançamento × documento exigido");
  SPEC-0015 (Open Question "Mapa final entryType × DocumentRequirement")
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0008 e a SPEC-0015 deixam em aberto o **mapa completo** de qual documento é obrigatório por
tipo de lançamento financeiro. Sem ele, o `close-check` e o veto de fechamento não têm o que cobrar.

## Decisão

Adotar como **seed inicial** (dado de sistema, via Flyway) o mapa derivado da **tabela 7.7 do
redesenho (OVERVIEW.md)**, com uma **fase** por requisito (`AT_REGISTRATION` = exigido para o
lançamento ser conforme; `AT_SETTLEMENT` = exigido só na liquidação):

| entryType | documento exigido | fase |
|---|---|---|
| COMMISSION_RECEIVABLE | COMMISSION_INVOICE | AT_REGISTRATION |
| COMMISSION_PAYABLE | COMMISSION_INVOICE | AT_REGISTRATION |
| COMMISSION_PAYABLE | PAYMENT_PROOF | AT_SETTLEMENT |
| UTILITY_EXPENSE | UTILITY_BILL | AT_REGISTRATION |
| UTILITY_EXPENSE | PAYMENT_PROOF | AT_SETTLEMENT |
| AUTONOMOUS_SERVICE | RPA | AT_REGISTRATION |
| SUPPLIER_SETTLEMENT | NFE | AT_REGISTRATION |
| REFUND | REFUND_PROOF | AT_REGISTRATION |
| PENALTY | VOUCHER | AT_REGISTRATION |

O **close-check** considera apenas os requisitos da fase `AT_REGISTRATION` (o que falta para o mês
fechar); `AT_SETTLEMENT` fica modelado para quando o Payout (SPEC-0017) liquidar.

## Justificativa

- A tabela 7.7 é a fonte canônica de "documento hábil por operação" (NBC ITG 2000; Lei 8.846/1994).
- Modelar a **fase** evita travar o fechamento por um comprovante de pagamento que só existe na
  liquidação, mantendo a regra fiel ao negócio (registro provisório permitido — SPEC-0015 BR2).
- Seed é dado de sistema, fácil de estender quando a contabilidade do cliente confirmar o mapa real.

## Alternativas descartadas

- **Hardcode no domínio (enum→enum).** Descartado: o mapa muda por cliente/contabilidade; precisa ser
  dado (seed), não código, para o operador/admin ajustar sem deploy (alinha com a Q8 do ROADMAP).
- **Sem fase (tudo exigido no registro).** Descartado: travaria o mês por comprovante de pagamento
  inexistente no provisionamento; contraria a SPEC-0015 BR2 ("lançamento pode nascer PROVISIONAL").

## Impacto

- `compliance`: tabela `document_requirements` (entry_type, required_document_type, phase) + seed.
- `compliance.ComplianceService.closeCheck`: filtra requisitos por `AT_REGISTRATION`.
- Specs 0008 e 0015: Open Question correspondente vira ASSUMIDO (ver DL-0012).

## Como reverter

Alterar/estender as linhas de seed (nova migração Flyway idempotente) — mudança barata e localizada;
nenhum refactoring de código.
