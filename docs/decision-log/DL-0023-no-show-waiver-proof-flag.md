# DL-0023 — No-show: dispensa por voo cancelado via flag de prova rastreável (conformidade = Compliance)

- **Fase:** 4 (Cancelamento como objeto + armadilha do merchant)
- **Spec(s):** SPEC-0010 (BR6; "a prova é documento — Compliance"; Validation Rules: "a dispensa por
  voo cancelado exige documento conforme (Compliance)")
- **ADR relacionado:** 0014; relaciona-se com Compliance (SPEC-0008)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

BR6: em NO_SHOW de carro, cobra-se `fee`; se `waivedIfFlightCancelled=true` **e** houver **prova de
voo cancelado**, a fee é dispensada. A "prova" é um **documento conforme** cuja verificação pertence
ao **Compliance** (SPEC-0008) — que nesta fatia **não** está no escopo do no-show. Falta decidir como
representar a prova sem puxar a verificação de conformidade do documento para cá.

## Decisão

- O endpoint `POST /api/bookings/{id}/no-show` aceita um campo opcional **`flightCancelledProof`**
  (um identificador/referência do documento, ou um booleano `flightCancelled`), que é um **mock
  rastreável** (`simulation-and-mocking.md`) apontando para a verificação real do Compliance.
- Regra aplicada (BR6):
  - sem prova **ou** `waivedIfFlightCancelled=false` ⇒ cobra `fee` (grava `Charge` `NO_SHOW`);
  - com prova **e** `waivedIfFlightCancelled=true` ⇒ **dispensa** (`waived=true`, sem `Charge`).
- A **conformidade** do documento (é hábil? está no cofre? dentro da retenção?) é **explicitamente
  fora de escopo** (Compliance/SPEC-0008); aqui a prova é aceita como fato informado, com auditoria.
- `NoShowCharged {bookingId, fee, waived, occurredAt}` é publicado em ambos os casos (com `waived`).

## Justificativa

- **SPEC-0010 manda:** "a prova é documento — Compliance" e marca a verificação de conformidade como
  responsabilidade de outro contexto. Representar a prova como flag/ref rastreável honra a fronteira
  sem inventar verificação de documento aqui (Regra Zero / `simulation-and-mocking.md`: stub explícito
  que referencia a spec futura, nunca lógica falsa).
- Mantém o no-show **testável** (com e sem dispensa) sem depender do cofre, que é uma fatia anterior
  mas cuja **regra de prova de voo** não foi especificada para integrar aqui.

## Alternativas descartadas

- **Exigir um `Document` real do Compliance já agora.** Descartada: acopla o no-show ao cofre e exige
  uma regra de "documento prova voo cancelado" que **não** está especificada — puxaria escopo.
- **Ignorar a dispensa (sempre cobrar).** Descartada: violaria BR6 (a dispensa é regra de negócio
  explícita).

## Impacto

- `NoShowPolicy {fee: Money, waivedIfFlightCancelled: boolean}` no snapshot.
- DTO `NoShowRequest {flightCancelledProof?}`; `BookingService.noShow(...)` aplica BR6.
- Evento `NoShowCharged`. Sem dependência de `compliance` no `booking`.

## Como reverter

Quando a regra de "prova conforme de voo cancelado" for especificada, troca-se a aceitação do flag por
uma consulta à porta do Compliance (`ProofVerifier`) — mudança **barata e localizada** no serviço.
