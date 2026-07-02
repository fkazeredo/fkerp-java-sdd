# 0016 - Billing (NF de Comissão, ISS e Retenções)

Status: Delivered (Fase 8c — release 0.11.0; ver DL-0044..DL-0047)
Related ADRs: 0011, 0012, 0014, 0015

> Convenções herdadas da **SPEC-0001**. Assinatura/transmissão usam o **certificado e-CNPJ custodiado
> pelo Platform (SPEC-0023)**; o documento emitido é guardado no **Compliance (SPEC-0008)**; a base
> vem dos lançamentos do **Finance (SPEC-0015)**. Integração com a prefeitura segue ACL/resiliência.

## Goal

Emitir a **Nota Fiscal de Serviço (NFS-e) sobre a comissão** — a receita real da Acme — calculando
**ISS** e **retenções**, transmitindo ao webservice municipal com o e-CNPJ, e arquivando o documento
fiscal no cofre. A **base é só a comissão** (não o valor do pacote), conforme redesenho 7.7/linha 153.

## Scope

**Em escopo:** o agregado `CommissionInvoice` (referência ao lançamento de comissão, base, ISS,
retenções, status RASCUNHO/EMITIDA/CANCELADA, número/verificação); cálculo de ISS pela alíquota do
município/serviço e das retenções aplicáveis; um **adaptador ACL** para o webservice de NFS-e
(emissão/consulta/cancelamento, assinatura com e-CNPJ via Platform); arquivamento do XML/DANFSe como
`Document` (NFSE) no Compliance.

**Fora de escopo:** o **regime tributário e quem emite** (Simples/Presumido/Real) — **Q7 em aberto**;
NF-e de mercadoria (a Acme vende serviço/comissão, não mercadoria); SPED/obrigações acessórias
(comprar/integrar — Finance é genérico).

## Business Context

A Acme ganha **comissão**; a NF incide sobre **ela**, não sobre o pacote do fornecedor. ISS é municipal
(alíquota e local de incidência variam) e há **retenções** conforme o tomador/regime. Emitir NFS-e exige
**e-CNPJ** e integração com a prefeitura — por isso depende do Platform (certificado) e é uma ACL séria
(contrato externo, assinatura, idempotência).

## Business Rules

```txt
BR1  CommissionInvoice MUST referenciar o lançamento de comissão (Finance) e ter base = valor da
     comissão (Money), nunca o valor do pacote.
BR2  O ISS MUST ser calculado pela alíquota do serviço/município aplicável; as retenções (ex.: IRRF,
     PIS/COFINS/CSLL, ISS retido) conforme tomador/regime.
BR2a ASSUMIDO (ver DL-0044): regime tributário default = **Simples Nacional**; emitente = a própria
     Acme; o cálculo de ISS/retenções é **parametrizado por regime (Simples/Presumido/Real) e
     município**, atrás de uma estratégia trocável (`TaxRegimeStrategy`). No Simples (v1): ISS =
     `issRate(município) × base_comissão` (default 5%, teto da LC 116/2003; São Paulo `3550308` = 2%);
     **retenções federais = nenhuma** (optante do Simples; IN RFB 1.234/2012). Trocar o regime real
     do contador = plugar outra estratégia, sem refator. **Q7 segue como incógnita de negócio até o
     contador confirmar** (Confiança=Baixa, Reversibilidade=Cara).
BR3  Emissão MUST assinar com o e-CNPJ (via porta do Platform) e transmitir ao webservice municipal;
     resposta com número/código de verificação MUST ser persistida.
BR4  Emissão MUST ser idempotente por (lançamento de comissão): não emite duas NFs para a mesma
     comissão; reemissão só após cancelamento.
BR5  Ao emitir, o XML/DANFSe MUST ser arquivado como Document (NFSE) no Compliance (retenção fiscal:
     5 anos; XML DF-e: 11 anos) e vinculado ao lançamento (satisfaz o DocumentRequirement).
BR6  Cancelamento da NFS-e MUST seguir o fluxo do município e atualizar status + Compliance.
BR7  Falha do webservice MUST ser classificada (TIMEOUT/REJECTED/UNAVAILABLE) — sem "emitida" falsa.
BR8  ASSUMIDO (2026-07-02, ver DL-0121 — Fase 19b, refina a DL-0044): **nuance do enquadramento
     para a conversa com o contador (Q7):** agência de viagens (CNAE 7911-2/00) tributa pelo
     **Anexo III sem Fator R**, mas receita de **representação comercial/intermediação** (o
     faturamento típico de uma GSA) cai no **Anexo V com Fator R** (folha ≥ 28% da receita ⇒
     Anexo III). O anexo afeta o DAS da própria Acme (fora do escopo do Billing — o ERP calcula o
     ISS da NF, não o DAS), por isso NENHUMA tabela de anexo é implementada; a nuance fica
     registrada para o contador. **Flag de confirmação:** `billing.tax.regime-confirmed`
     (default `false`) — enquanto não confirmada pelo contador, o validador de prontidão de
     produção (Fase 19c) MUST impedir o perfil `prod` de subir apto a emitir NFS-e real.
     Dev/test não mudam (gateway = mock rastreável, DL-0046).
```

## Input/Output Examples

```http
POST /api/billing/invoices
{ "commissionEntryId":"e91...", "serviceCode":"...", "municipality":"3550308" }
201 Created  { "id":"nf12...", "base":{"amount":"405.00","currency":"BRL"}, "status":"RASCUNHO" }

POST /api/billing/invoices/{id}/issue
200 OK
{ "id":"nf12...", "status":"EMITIDA", "number":"2026/000123", "verificationCode":"ABC123",
  "iss":{"amount":"20.25","currency":"BRL"}, "documentId":"d77..." }   # arquivada no Compliance
```

## API Contracts

- `POST /api/billing/invoices` — cria rascunho a partir do lançamento de comissão → 201.
- `POST /api/billing/invoices/{id}/issue` — calcula impostos, assina, transmite, arquiva → 200 | 502/409.
- `POST /api/billing/invoices/{id}/cancel` — cancela conforme município → 200.
- `GET /api/billing/invoices/{id}` → 200 | 404 `billing.invoice.not-found`.
- OpenAPI atualizada; contrato do webservice municipal isolado na ACL.

## Events

- `CommissionInvoiceIssued` — `{invoiceId, commissionEntryId, number, documentId, occurredAt}`.
  Produtor: `billing`. Consumidor: `finance` (lançamento conforme), `intelligence`.
- `CommissionInvoiceCancelled` — `{invoiceId, reason, occurredAt}`.

## Persistence Changes

```txt
V20__create_billing.sql   -- V16..V19 já usadas; esta migração começa em V20
  commission_invoices(
    id uuid PK, commission_entry_id uuid not null,               -- valor p/ Finance
    base_amount numeric(18,2) not null, base_currency varchar not null,
    iss_amount numeric(18,2) null, withholdings_json jsonb null,
    status varchar not null, number varchar null, verification_code varchar null,
    document_id uuid null,                                        -- valor p/ Compliance
    municipality varchar null, service_code varchar null, tax_regime varchar not null,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null
  )
  -- idempotência (BR4/BR6): no máximo uma nota VIVA por comissão; cancelada libera reemissão
  UNIQUE INDEX (commission_entry_id) WHERE status <> 'CANCELADA'
  municipal_iss_rates(municipality varchar PK, iss_rate numeric(5,4) not null)  -- seed: default + SP 2%
```

O cliente de NFS-e e o cálculo de tributos ficam em `infra/integration` (ACL) e em serviço de domínio;
a assinatura é **porta do Platform** (`CertificateSigner`). Vendor DTO da prefeitura **não vaza**.

## Validation Rules

- Integração: assinatura/transmissão/idempotência; resposta validada (número/código presentes).
- Domain: base = comissão (BR1); cálculo de ISS/retenções como serviço testável (depende de Q7).
- Application: existência do lançamento de comissão (Finance) e arquivamento no Compliance (BR5).

## Error Behavior

`billing.invoice.not-found` → 404; `billing.invoice.already-issued` → 409; `billing.municipality.rejected`
→ 422 (rejeição da prefeitura, com motivo); falha de webservice → 502 classificado. i18n em
`messages_pt_BR.properties`. **Nunca** logar o certificado/credenciais.

## Observability Requirements

- Logar emissão/cancelamento como evento de negócio + **log de integração** com a prefeitura (latência,
  classe de falha, correlation id), sem dados sensíveis. Métricas: `invoices_issued_total`,
  `invoices_rejected_total`, latência do webservice.

## Tests Required

- **Unit/domain:** base = comissão (não pacote); cálculo de ISS por alíquota; idempotência por comissão.
- **Integração (Testcontainers + prefeitura fake):** emitir gera número + arquiva Document no Compliance;
  reemissão sem cancelar → 409; rejeição da prefeitura → 422 classificado.
- **Regressão:** a NF emitida satisfaz o `DocumentRequirement` do lançamento de comissão no Finance
  (o mês passa a poder fechar) — falha antes, passa depois.

## Acceptance Criteria

- Emitir a NF da comissão de R$ 405 calcula ISS, transmite, retorna número/código e arquiva o XML no cofre.
- Não é possível emitir duas NFs para a mesma comissão.
- `./mvnw verify` verde.

## Open Questions

- **Q7 — regime tributário e quem emite** (Simples/Presumido/Real): **ASSUMIDO (ver DL-0044)** —
  Simples Nacional default, emitente = Acme, cálculo parametrizado por regime+município atrás de
  estratégia trocável. **Permanece como incógnita de negócio** (Confiança=Baixa, Reversibilidade=Cara):
  só o contador/diretor da Acme fecha o regime real; até lá, o default Simples vale e a estratégia é
  trocável sem refator.
- Município(s) de incidência e padrão de NFS-e (ABRASF/Nacional) — **parametrizado no adaptador ACL**
  (ver DL-0046): município/serviceCode são entrada; o padrão ABRASF é simulado pelo mock rastreável.
  Confirmar o município/padrão reais troca o adaptador, não o domínio.

## Out of Scope

NF-e de mercadoria; SPED/obrigações acessórias (comprar/integrar); regime tributário não decidido (Q7).
