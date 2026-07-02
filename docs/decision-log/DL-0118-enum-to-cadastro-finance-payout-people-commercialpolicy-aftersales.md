# DL-0118 — enum→cadastro fatia 18d: Finance / Payout / People / CommercialPolicy / AfterSales (FECHA a Fase 18)

- **Fase:** 18d (conversão dos últimos enums de referência de Finance/Payout/People/CommercialPolicy/AfterSales — encerra a Fase 18)
- **Spec(s):** SPEC-0031 (cadastro); SPEC-0015 (Finance), SPEC-0017 (Payout), SPEC-0022 (People), SPEC-0014 (CommercialPolicy), SPEC-0018 (AfterSales)
- **ADR relacionado:** ADR-0019 (padrão enum→cadastro)
- **Data:** 2026-07-01
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

As fatias 18a/18b/18c criaram o módulo `cadastro` e o padrão enum→cadastro (DL-0115/DL-0116/DL-0117),
convertendo Admin/Assets/Billing, Marketing/Intelligence/Portfolio e
Sourcing/Exchange/Booking/Compliance. Faltavam os **últimos** grupos de referência —
**Finance, Payout, People, CommercialPolicy e AfterSales** — para **encerrar a Fase 18**, reusando
exatamente o mesmo mecanismo, sem quebrar o contrato de fio e **preservando a lógica de domínio que
ramifica** por valores específicos: a natureza AP/AR do `EntryType` (+ o mapa
kind→documento do Compliance, DL-0012), o fato de liquidação/repasse/reembolso do `PayoutKind` (+ a
armadilha do lojista, DL-0024/DL-0051), a orquestração do `CaseResolution` (Payout REFUND / Booking
cancel, DL-0054) e o parse/validação do valor do `ParameterValueType` (DL-0037).

## Decisão

Reusa integralmente o padrão de DL-0115. Enums convertidos nesta fatia:

| Módulo           | CadastroType           | Codes (semeados em V36)                                                                                          | Ramificação preservada |
|------------------|------------------------|-----------------------------------------------------------------------------------------------------------------|------------------------|
| finance          | `ENTRY_TYPE`           | COMMISSION_RECEIVABLE, COMMISSION_PAYABLE, PENALTY, UTILITY_EXPENSE, AUTONOMOUS_SERVICE, SUPPLIER_SETTLEMENT, REFUND, TAX_PAYABLE, SERVICE, OTHER_EXPENSE | `EntryTypeCodes` — a postagem AP/AR + o `DocumentRequirementDirectory` do Compliance (DL-0012) leem o code |
| finance          | `PARTY_TYPE`           | AGENCY, AGENT, SUPPLIER, OTHER                                                                                   | `PartyTypeCodes` (contraparte gravada pelos produtores internos) |
| payout           | `PAYEE_TYPE`           | AGENT, SUPPLIER, CUSTOMER                                                                                        | `PayeeTypeCodes` (favorecido; dirige o tipo de comprovante) |
| payout           | `PAYOUT_KIND`          | AGENT_COMMISSION, SUPPLIER_SETTLEMENT, REFUND                                                                    | `PayoutKindCodes` — o `switch` do `publishExecuted` (fato de liquidação/repasse/reembolso) + o REFUND que exige origem e NUNCA compensa a obrigação do fornecedor (armadilha do lojista, DL-0024/DL-0051) |
| people           | `DISCREPANCY_KIND`     | ODD_PUNCH, MISSING_PUNCH, INCOHERENT_JOURNAL                                                                     | `DiscrepancyKindCodes` (produzido pelo `JourneyCalculator`; NÃO validado na escrita) |
| commercialpolicy | `PARAMETER_VALUE_TYPE` | NUMBER, PERCENT, MONEY, BOOL                                                                                     | `ParameterValueTypeCodes` — o parse/validação do `value_text` (isValid/asDecimal, DL-0037) |
| aftersales       | `SUPPORT_CASE_TYPE`    | COMPLAINT, CHANGE_REQUEST, CANCELLATION_REQUEST, REFUND_REQUEST, INFO                                            | `SupportCaseTypeCodes.usesRefundSla` — seleção do SLA governado (48h vs 72h, DL-0052) |
| aftersales       | `CASE_RESOLUTION`      | REFUND_APPROVED, CANCEL_APPROVED, RESOLVED_NO_ACTION, REJECTED                                                   | `CaseResolutionCodes.triggersRefund/triggersCancellation` — orquestra Payout REFUND / Booking cancel (DL-0054) |

1. **Representação persistida:** cada campo `@Enumerated(STRING)` vira **`String code`** (mesma
   coluna, mesmo valor = nome do antigo constante). Contrato JSON **idêntico** (era string de enum,
   continua string). Os eventos (`LedgerEntryRegistered` já era `String`; `SupportCaseOpened`/
   `SupportCaseResolved`/`JourneyDiscrepancy`) e views passam a `String`.
2. **DTOs/views/eventos:** os campos viram `String`. Requests de escrita usam `@NotBlank String`
   (Finance entryType/party.type; Payout kind/payee.type; CommercialPolicy value type; AfterSales
   case type/resolution).
3. **Validação (só onde há escrita a partir do fio):** Finance valida `ENTRY_TYPE` e `PARTY_TYPE` no
   `register` (o `POST /api/finance/entries`); Payout valida `PAYOUT_KIND` e `PAYEE_TYPE` no `create`;
   CommercialPolicy valida `PARAMETER_VALUE_TYPE` no `defineRule`; AfterSales valida `SUPPORT_CASE_TYPE`
   no `open` e `CASE_RESOLUTION` no `resolve`. Tudo via a porta `CadastroValidator` (código
   inválido/inativo → `CadastroCodeInvalidException`, 422). Os **produtores internos**
   (BookingChargeEventsListener/PayoutEventsListener/CommissionInvoiceEventsListener/AdminService/
   BillingIssuanceService) usam as constantes `*Codes` e passam pelo `register` (que valida) — os codes
   cablados estão sempre ativos no seed, então não há regressão.
4. **Produzido pelo sistema (sem validação na escrita):** `DISCREPANCY_KIND` — o `JourneyCalculator`
   cunha o code da análise da jornada; nunca chega como payload. É cadastro só para o rótulo ser
   editável e as telas mostrarem o label (mesmo precedente dos `INSIGHT_*`/`MARKET_RATE_SOURCE`).
5. **Direção da dependência (grafo acíclico):** `finance`/`payout`/`people`/`commercialpolicy`/
   `aftersales` → `cadastro` (porta). O `cadastro` continua folha; Modulith acíclico (ArchUnit verde
   com o 23º módulo, 17 testes de arquitetura).
6. **Lógica que ramifica (preservada por constantes):** o `switch` exaustivo do `PayoutService.publishExecuted`
   sobre o enum vira `switch` sobre as constantes de code com `default` seguro (kind desconhecido não
   publica fato — dado puro). `EntryTypeCodes`/`PartyTypeCodes` guardam os codes que os listeners
   emitem; `AdminExpenseCodes.entryTypeFor` agora mapeia code→code. `ParameterValueTypeCodes` guarda o
   parse/validação (isValid/asDecimal) que o enum carregava. `SupportCaseTypeCodes.usesRefundSla` e
   `CaseResolutionCodes.triggersRefund/triggersCancellation` guardam o comportamento cablado.
7. **Rótulo nas telas:** o `CadastroLabelPipe`/`CadastroLabelService` (18b) é aplicado nas telas
   Finance (entryType/party.type), Payout (kind/payee.type), People (discrepancy kind), CommercialPolicy
   (value type) e AfterSales (case type/resolution). As tags de status/severidade continuam usando o
   **code/enum** cru (status são máquinas de estado, não convertidas).
8. **Migração V36** semeia os 8 tipos (35 itens) como itens (`code`=nome do enum, `label` pt-BR),
   idempotente (`ON CONFLICT DO NOTHING`).

## Decisões de fronteira (aggressive criterion) — manter vs converter

- **`LedgerDirection` (PAYABLE/RECEIVABLE) — MANTIDO enum.** É o eixo binário fundamental da partida
  dobrada contábil: dirige o lado AP/AR e a apuração do net (switch exaustivo em `trialBalance`). Não
  é dado de referência que o operador queira editar/renomear — é um invariante contábil fixo. Converter
  não agrega e perderia a exaustividade do compilador na apuração. (ADR-0019: máquinas/eixos fixos não
  viram cadastro.)
- **`ParameterLayer` (DIRECTIVE > PROMOTION > CONTRACT > POLICY > SYSTEM_DEFAULT) — MANTIDO enum.** É
  uma **hierarquia de precedência fixa** cujo `rank()` (ordinal) governa a ordenação determinística do
  motor de resolução (DL-0037) e cuja autorização ramifica em `isDirective()` (DL-0038). Não é conjunto
  extensível pelo operador: acrescentar uma camada exigiria mudar o motor de precedência e o auth.
  Convertê-lo quebraria a garantia de ordenação. Mantido enum (borderline resolvido por
  "comportamento/ordem fixa por design", ADR-0019).
- **`ParameterValueType` — CONVERTIDO** (com `ParameterValueTypeCodes` guardando isValid/asDecimal).
  Borderline: tem comportamento (parse), mas o **conjunto** de tipos de valor é plausível de crescer
  (ex.: DATE, DURATION) e o rótulo é útil na tela; o comportamento cablado fica nas constantes, o
  conjunto+rótulo no cadastro — o mesmo compromisso de DL-0115. Um novo code sem parse cablado é
  rejeitado no `isValid` (não se grava regra com valor ininterpretável).

**KEEP (fora de escopo, por ADR-0019):** máquinas de estado (`EntryStatus`, `PeriodStatus`,
`PayoutStatus`, `DiscrepancyStatus`, `SupportCaseStatus`, `EmployeeStatus`, `CrawlRunStatus`),
técnicos (`PaymentOutcome`, `PointFailureClass`) e `LedgerDirection`/`ParameterLayer` (acima).

## Justificativa

- **Invariante do dono:** "o valor persistido vira `code` validado com `code`=nome do enum ⇒ JSON de
  contrato inalterado". Mantido byte-a-byte (provado por testes de round-trip).
- **Regra Zero:** um único mecanismo (registry + porta) cobre também estes grupos; as constantes
  `*Codes` existem só onde há ramificação real. Nenhuma tabela por enum.
- **Confiança=Alta:** mecânico e testável (round-trip idêntico; rejeição de código inválido/inativo;
  ramificação preservada — postagem AP/AR do EntryType, fato do PayoutKind, orquestração do
  CaseResolution). Backend `./mvnw verify` verde: **513 testes** (507 + 6 do novo teste de invariante
  18d), cobertura e ArchUnit intactos.

## Alternativas descartadas

- **Converter `LedgerDirection`/`ParameterLayer`.** Descartada: eixo contábil binário fixo e hierarquia
  de precedência fixa (ordinal governa a ordenação/autorização) — não são dado de referência editável;
  converter perderia exaustividade/ordem determinística sem ganho (ADR-0019).
- **Validar `DISCREPANCY_KIND` na escrita.** Descartada: não há escrita a partir do fio (o calculador o
  produz); validar não agrega.
- **Converter a ramificação (kind/resolution/value-type) para dado sem constantes.** Descartada:
  perderia o comportamento determinístico. Mesmo compromisso de DL-0115: cadastro = conjunto+rótulos;
  constantes = comportamento cablado.

## Impacto

- **Specs:** SPEC-0031 (tabela de tipos 18d marcada como entregue → **Fase 18 completa**).
- **Arquivos:** enums removidos + `*Codes` novos em `finance`/`payout`/`people`/`commercialpolicy`/
  `aftersales`; entidades/DTOs/views/eventos/repos/serviços/controllers retipados para `String`;
  `CadastroType` +8 valores; infra (`BillingIssuanceService`, `PayoutExecutionService`) e os listeners
  do Finance usam os codes. Frontend: `cadastro.models` (+8 tipos), telas
  Finance/Payout/People/CommercialPolicy/AfterSales com o pipe.
- **Migração:** **V36** semeia os 8 tipos (35 itens), idempotente.
- **Contratos:** **sem mudança de fio** — campos convertidos continuam `string`; sem novos endpoints.
  OpenAPI/pom → 0.32.0 (MINOR, ADR-0015). Tag `0.32.0` FECHA a Fase 18.

## Como reverter

Retipar os campos/DTOs de volta aos enums, remover as constantes `*Codes` e a validação, e apagar as
linhas do seed em V36 (migração de baixa). Moderada: os valores no banco são idênticos aos nomes dos
enums, então não há backfill — só refator de tipos.
