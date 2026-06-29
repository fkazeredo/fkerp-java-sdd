# DL-0010 — Booking: um Quote pode gerar várias Bookings (não 1:1) no v1

- **Fase:** 1 (Núcleo comercial manual)
- **Spec(s):** SPEC-0006
- **ADR relacionado:** 0014
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0006 deixa em aberto: o `Quote` é consumido **uma vez** por Booking (1:1) ou um Quote pode
gerar **várias** Bookings? A spec assume "1:1 no v1, confirmar". O ROADMAP não traz recomendação.

## Decisão

**Não impor 1:1 no v1.** Uma cotação pode lastrear mais de uma Booking; a **unicidade do
localizador** `(origin, code)` (BR3) é a única trava contra duplicação acidental. Não há índice
único em `quote_id`.

## Justificativa

- **Re-booking após cancelamento:** se uma Booking é cancelada (ex.: timeout de PENDING em 72h ou
  desistência), o operador pode **reabrir** a mesma cotação numa nova Booking. Um `UNIQUE(quote_id)`
  rígido **bloquearia** esse fluxo legítimo — pior do que permitir N.
- A trava real contra erro humano (digitar o mesmo voucher duas vezes) é o **localizador único**, que
  já existe.
- Evita inventar uma regra de negócio forte (1:1) sem confirmação do dono (`CLAUDE.md`, invariante 3);
  começar permissivo e apertar depois é mais barato que o contrário.

## Alternativas descartadas

- **`UNIQUE(quote_id)` (1:1 estrito).** Descartado no v1: impede re-booking após cancelamento; é
  decisão de negócio não confirmada.
- **1:1 só entre Bookings ativas** (permitir nova Booking se a anterior está CANCELLED). Boa ideia,
  mas exige índice parcial + regra de "ativa"; adiada até o dono confirmar a necessidade.

## Impacto

- `booking`: `create` valida só existência do Quote (fachada) + unicidade do localizador; sem
  checagem de quote já reservado. `V5__create_bookings.sql` sem `UNIQUE(quote_id)`.

## Como reverter

Adicionar `UNIQUE(quote_id)` (ou índice parcial por status ativo) + checagem no `create` com nova
exceção/i18n + migração. Moderado (toca schema e fluxo de criação).
