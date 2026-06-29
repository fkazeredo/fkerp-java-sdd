# DL-0047 — Billing: idempotência de emissão, evento para o Finance e arquivamento no Compliance

- **Fase:** 8c (Billing — SPEC-0016)
- **Spec(s):** SPEC-0016 (BR4 idempotência por comissão; BR5 arquivar NFS-e no Compliance + satisfazer
  o DocumentRequirement; Events `CommissionInvoiceIssued`; Tests Required "regressão: a NF satisfaz o
  DocumentRequirement do lançamento de comissão")
- **ADR relacionado:** 0011, 0012 ; `architecture/messaging-and-integrations.md` (idempotência,
  eventos in-process) ; padrão da Fase 8b (DL-0041, posting dirigido por evento)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

Faltava decidir, para a emissão: (a) como provar **idempotência** ("não emite duas NFs para a mesma
comissão; reemissão só após cancelamento" — BR4); (b) **como** a nota emitida lança no Finance sem
acoplar (BR5 / Events); (c) **como** o XML/DANFSe é **arquivado no Compliance** e **vinculado** ao
lançamento de comissão para **satisfazer o DocumentRequirement** (BR5 + regressão dos Tests Required).

## Decisão

**Acaclicidade (decisão de fronteira, central):** o módulo `domain.billing` é **folha** — depende
**apenas** dos kernels (`money`, `error`) e das suas próprias portas (`NfseGateway`,
`CertificateSigner`). Ele **NÃO** importa `finance` nem `compliance`. A colaboração cross-módulo
acontece de dois jeitos, ambos **sem** criar ciclo:
- **Finance** consome o evento `CommissionInvoiceIssued` (`finance → billing`, igual a `finance →
  booking` do DL-0041);
- **Compliance** é chamado por um **orquestrador em `infra`** (`BillingIssuanceService` em
  `infra.integration.nfse`), exatamente como o `AfdIngestionService` da Fase 6 chama
  `ComplianceService.upload` — `infra` é **isento** da regra de ciclo entre módulos de domínio
  (infra → domínio é permitido). Assim o grafo permanece **acíclico**: billing → (nada de domínio);
  finance → billing; compliance → finance; infra.nfse → billing+compliance.

1. **Emissão idempotente por comissão (BR4), em duas camadas:**
   - **Estrutural:** `commission_entry_id` **UNIQUE** (DL-0045) — no máximo uma nota viva por comissão.
   - **De estado:** `issue` só transita `RASCUNHO → EMITIDA`. Reemitir uma nota **já EMITIDA** é
     **no-op idempotente** (retorna a mesma nota, mesmo número/código — não retransmite, não duplica
     no Compliance/Finance). Emitir após **CANCELADA** é permitido apenas por **nova nota** (a UNIQUE
     foi liberada pelo cancelamento — ver ponto 4). Prova: um teste emite duas vezes e verifica **um**
     número, **um** documento no Compliance, **um** lançamento de tributo no Finance.

2. **Arquivamento no Compliance (BR5) pelo orquestrador `infra`** (`BillingIssuanceService`), via a
   **fachada pública** `ComplianceService.upload(...)` (a mesma que o `AfdIngestionService` usa) —
   `infra.nfse → compliance` por fachada, **sem** tocar o repositório do Compliance e **sem** acoplar
   o módulo `billing` ao `compliance`. O documento é gravado como `DocumentType.NFSE` (retenção fiscal
   já modelada no `RetentionPolicy`), `signedFormat = XADES` (NFS-e é XML assinado), `hasPersonalData
   = true` (a nota traz CNPJ/identificação do tomador → dado tributário/pessoal; acesso auditado pelo
   cofre), e **anexado ao lançamento de comissão** (`entryId = commissionEntryId`, `entryType =
   COMMISSION_RECEIVABLE`). O `documentId` retornado é guardado na nota (valor) pelo orquestrador
   (`BillingService.markIssued(...)`). Assim a NF **satisfaz o `DocumentRequirement`** daquele
   lançamento → o mês pode fechar (regressão dos Tests Required: falha antes, passa depois). Como tudo
   roda na **mesma transação** do orquestrador, o `documentId` volta **síncrono** na resposta do
   `issue` (a spec o exige), sem precisar de evento para arquivar.

3. **Evento `CommissionInvoiceIssued`** (`{invoiceId, commissionEntryId, number, documentId,
   occurredAt}`) publicado in-process **na transação da emissão**. **O Finance consome o evento e
   lança** — Billing **não** chama o repositório do Finance (consistente com 8b/DL-0041). Novo
   listener module-internal no `finance` (`CommissionInvoiceEventsListener`) reusa
   `FinanceService.postFromCharge(...)` (idempotente por `(sourceRef, chargeKind)`), com:
   - `sourceRef = invoiceId.toString()`;
   - **ISS** → `chargeKind = "ISS"`, **PAYABLE**, `EntryType.UTILITY_EXPENSE`? **NÃO** — o ISS é
     tributo a recolher; reusa-se um `EntryType` existente sem inventar fluxo: o ISS entra como
     **PAYABLE** com `EntryType.SUPPLIER_SETTLEMENT`? Também não. **Decisão:** adiciona-se **um**
     `EntryType.TAX_PAYABLE` (o tributo a recolher é um tipo de lançamento legítimo e ausente hoje) —
     mudança mínima e dona de spec (Billing). A **comissão a receber** já é lançada pelo fluxo
     comercial; aqui o Billing **não** duplica a receita — lança **apenas o tributo** (ISS PAYABLE) e,
     quando houver, as **retenções** (PAYABLE). Party = a Acme/fisco (`PartyType` existente
     `SUPPLIER` como contraparte do recolhimento, id = município) — sem novo PartyType.
   - **Idempotência:** re-entrega do `CommissionInvoiceIssued` é no-op (a UNIQUE `(invoiceId, "ISS")`
     do `posted_event_entries` bloqueia o segundo lançamento), provado por teste.

4. **Cancelamento (BR6):** `cancel` transita `EMITIDA → CANCELADA`, chama `NfseGateway.cancel(...)`,
   publica `CommissionInvoiceCancelled` e **libera a comissão** para uma nova nota. Para que a UNIQUE
   `commission_entry_id` não impeça a reemissão, a coluna é **UNIQUE apenas para notas não-canceladas**
   (índice parcial `WHERE status <> 'CANCELADA'`) — uma nota cancelada não conta. O Compliance/Finance
   da nota cancelada permanecem (rastro fiscal; o estorno contábil é fluxo do Finance, fora desta
   spec — registrado como pendência).

## Justificativa

- **BR4/BR5 + Tests Required** pedem exatamente: idempotência, arquivamento que satisfaz o
  DocumentRequirement, e o evento de conformidade. O padrão **publicar evento que o Finance consome**
  é o **mesmo de 8b (DL-0041)** — Billing fica desacoplado (nunca chama o repo do Finance).
- **Índice parcial** é o jeito relacional limpo de "uma viva por comissão, reemissão após cancelar"
  (BR4/BR6) sem lógica de aplicação frágil (persistence.md: o banco impõe integridade).
- **`TAX_PAYABLE` novo** é justificado e mínimo: o ISS a recolher é um lançamento real que não existe
  no catálogo atual; não é especulação. As retenções, quando o regime as tiver (DL-0044), entram como
  PAYABLE adicionais pelo mesmo listener — costura pronta, sem refator.

## Alternativas descartadas

- **Billing chamar `FinanceService.register/postFromCharge` diretamente (sem evento).** Descartada:
  acoplaria Billing ao Finance e divergiria do padrão event-driven de 8b. Evento in-process mantém o
  desacoplamento e a idempotência no consumidor.
- **Lançar a comissão a receber no Finance a partir da nota.** Descartada: a receita de comissão é do
  fluxo comercial (Reconciliation/Booking), não do documento fiscal; a nota lança **só o tributo**
  (evita dupla contagem da receita).
- **UNIQUE total em `commission_entry_id`.** Descartada: impediria a reemissão pós-cancelamento (BR6).
  Índice parcial resolve.
- **Arquivar tocando o repositório do Compliance.** Descartada: proibido (cross-module); usa-se a
  fachada `ComplianceService.upload` (a porta pública, como o AFD da Fase 6).

## Impacto

- **Specs:** SPEC-0016 — BR4/BR5/BR6 concretizadas; Events confirmados.
- **Arquivos:** `BillingService.issue/cancel`; evento `CommissionInvoiceIssued`/`Cancelled`;
  `CommissionInvoiceEventsListener` em `finance.internal`; **novo** `EntryType.TAX_PAYABLE`; índice
  parcial na migração **V20**; i18n `billing.*`.
- **Modulith:** `domain.billing` é folha (só kernels + portas próprias). `finance → billing`
  (consome o evento `CommissionInvoiceIssued`, análogo a `finance → booking` do DL-0041). O
  arquivamento e a orquestração de emissão vivem em `infra.integration.nfse` (`infra → billing`,
  `infra → compliance`). **Grafo acíclico:** billing→∅; finance→{billing,booking}; compliance→finance;
  infra.nfse→{billing,compliance}. Nenhuma aresta volta para billing a partir dele mesmo.

## Como reverter

Reversão **moderada**: remover o `CommissionInvoiceEventsListener` (e o `TAX_PAYABLE` se nada mais o
usar), o evento e o passo de arquivamento. Notas já emitidas permanecem; documentos já arquivados
seguem no cofre (retenção). Nenhum contrato público de Finance/Compliance muda — eles só **ganham** um
consumidor/uso de fachada. Raio contido em `billing` + um listener em `finance.internal`.
