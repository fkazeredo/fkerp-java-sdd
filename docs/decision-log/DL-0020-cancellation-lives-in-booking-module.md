# DL-0020 — Cancelamento rico vive no módulo `booking` (sem módulo `cancellation`/`policy` novo)

- **Fase:** 4 (Cancelamento como objeto + armadilha do merchant)
- **Spec(s):** SPEC-0010 (Scope: "`CancellationPolicy` … **congelada na Booking** na confirmação";
  BR1; BR5); SPEC-0006 (ciclo de vida da Booking)
- **ADR relacionado:** 0012 (camadas/hexagonal), 0014 (conjunto inicial de módulos)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0010 introduz `CancellationPolicy` (objeto), `NoShowPolicy`, o cálculo da multa, o snapshot
congelado e os encargos resultantes (`SupplierCharge`, `CustomerRefund`, `PenaltyCharge`). Falta
decidir **onde** isso mora: no módulo `booking` existente (10 módulos hoje) ou num **novo** módulo
`cancellation`/`policy`.

## Decisão

**Vive no módulo `booking`.** Sem módulo novo. Concretamente:

- Os value objects da política (`CancellationPolicy`, `CancellationType`, `PenaltyWindow`,
  `CostBearer`, `NoShowPolicy`, `Charge`, `ChargeKind`) ficam no pacote público
  `com.fksoft.domain.booking`.
- O snapshot congelado na confirmação (`BookingCancellationSnapshot`), a fonte administrável da
  política (`CancellationPolicySource`) e os encargos (`CancellationCharge`) são agregados/entidades
  **module-private** em `com.fksoft.domain.booking.internal`.
- O cálculo da multa e a materialização dos encargos são **comportamento de domínio** da Booking
  (a transição CANCELLED/NO_SHOW já vive lá).

## Justificativa

- **Regra Zero (invariante 1 do CLAUDE.md):** "patterns, layers, abstractions e *modules* existem
  só quando resolvem um problema real". Um módulo é definido por **linguagem, regras, ciclo de vida,
  dono e razão de mudar próprios** (`modules-and-apis.md`). O cancelamento **não** tem isso: usa a
  linguagem da Booking, é uma **transição do ciclo de vida da Booking** (PENDING/CONFIRMED →
  CANCELLED/NO_SHOW), e o snapshot é congelado **na própria Booking** (a spec diz isso na letra).
- **Coesão pela tese:** a SPEC-0010 declara que a política é "congelada na Booking" e que cancelar
  "soma a multa/obrigações" ao ciclo já existente — partir isso num módulo separado criaria
  dependência circular artificial (booking↔cancellation) e um bounded context sem dono distinto.
- **`workflow.md` / New project:** "MUST NOT create fake bounded contexts or placeholder classes".
  Um módulo `policy` especulativo seria exatamente isso — a precedência de parâmetros governados
  (CommercialPolicy/SPEC-0014) é que é um contexto de verdade, e **não** é esta fatia.
- **Preserva extração futura:** se algum dia o cancelamento virar produto próprio, o `internal` já
  isola o agregado; extrair é refactor moderado (mover pacote + porta), não reescrita.

## Alternativas descartadas

- **Novo módulo `cancellation`.** Descartado: sem linguagem/dono/ciclo próprios; criaria
  acoplamento booking↔cancellation e um 11º módulo vazio de identidade (anti-Regra-Zero).
- **Novo módulo `policy` genérico.** Descartado: confunde-se com `CommercialPolicy` (SPEC-0014, motor
  de precedência), que é o contexto real de "regra como dado governado" — e essa é outra fase.
- **Pôr só os value objects num kernel compartilhado.** Descartado: não há reuso cross-módulo hoje
  (Finance/Payout/Intelligence consomem os **eventos**, não os tipos); duplicação especulativa.

## Impacto

- `domain.booking` (público): novos value objects + eventos (`CancellationCharged`,
  `MerchantObligationIncurred`, `NoShowCharged`).
- `domain.booking.internal`: `CancellationPolicySource`, `BookingCancellationSnapshot`,
  `CancellationCharge` + repositórios; o `Booking` ganha `scopeRef`/`serviceCurrency` e congela o
  snapshot na confirmação.
- `application`: `CancellationPolicyAdminController`; `BookingController.cancel/no-show` enriquecidos.
- Migrações V12 (fonte) e V13 (snapshot + encargos). **Contagem de módulos Modulith permanece 10.**

## Como reverter

Extrair um módulo `cancellation` depois: mover o pacote `…booking` cancellation-related para
`…cancellation`, expor uma porta `CancellationPolicyDirectory` consumida pela Booking, e mover as
duas tabelas. Refactor **moderado** e mecânico (o `internal` já encapsula o agregado).
