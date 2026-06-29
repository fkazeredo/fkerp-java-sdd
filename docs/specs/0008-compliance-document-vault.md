# 0008 - Compliance (Cofre de Documentos, Anexo Obrigatório e Retenção)

Status: Approved
Related ADRs: 0010, 0012, 0014

> Convenções herdadas da **SPEC-0001 §"Convenções do projeto"**. Abstração de storage e regras de
> upload seguem `messaging-and-integrations.md` (§Files); a guarda legal segue a Parte 7.7 do redesenho.

## Goal

Tornar o **documento comprobatório cidadão de primeira classe**: um cofre que guarda os documentos
hábeis com **hash, formato assinado e prazo legal de guarda**, impõe que **todo lançamento financeiro
referencie o documento exigido**, e **impede o fechamento do mês** enquanto faltar documento
obrigatório (redesenho 7.7; NBC ITG 2000; Código Civil arts. 1.179–1.180).

## Scope

**Em escopo:** o agregado `Document` no cofre (tipo, referência de arquivo, hash, emissão,
`retentionUntil`, formato assinado opcional); upload validado via porta `FileStorage`; o vínculo
`DocumentAttachment` entre um documento e um lançamento financeiro (referenciado por id+tipo, **valor,
sem FK cross-módulo**); a política `DocumentRequirement` (por tipo de lançamento, qual documento é
obrigatório); o **guarda de fechamento** que responde "pode fechar o período?" para o Finance; a
política de **retenção** por tipo (tabela legal como dado de sistema via seed) e o bloqueio de expurgo
antes do prazo; job que sinaliza retenção vencendo.

**Fora de escopo:** o **razão AP/AR e a máquina de período/fechamento** são do **Finance (SPEC-0015)**
— Compliance só **veta** o fechamento; a **custódia do certificado e-CNPJ (ICP-Brasil)** é do
**Platform (SPEC-0023)**; emissão de NF é **Billing (SPEC-0016)**.

## Business Context

A contabilidade **não pode** lançar sem documento hábil, e o documento certo **depende da operação**
(NF-e entre PJ, RPA para autônomo, fatura+comprovante para conta de consumo, contrato para mútuo —
tabela 7.7). Recibo simples só vale para quem não é obrigado a emitir NF (Lei 8.846/1994); nota de
débito não é documento hábil. A retenção (5/10/20 anos conforme o tipo) conflita com a minimização da
LGPD, mas a base legal de "cumprimento de obrigação legal" autoriza guardar — com controle de acesso e
trilha para documentos com dado pessoal.

## Business Rules

```txt
BR1  Um Document MUST ter type ∈ catálogo (NFE, NFSE, RPA, UTILITY_BILL, LOAN_CONTRACT,
     COMMISSION_INVOICE, PAYMENT_PROOF, REFUND_PROOF, PAYROLL, TIME_RECORD_AFD, PROCESSED_JOURNAL_AEJ,
     VOUCHER, REPRESENTATION_CONTRACT, OTHER), fileRef, hash (do conteúdo) e issuedAt.
BR2  retentionUntil MUST ser calculado na ingestão a partir do type, pela tabela de retenção
     (RetentionPolicy, dado de sistema): fiscais 5 anos (CTN 173/174); XML de DF-e 11 anos
     (Ajuste SINIEF 2/2025); contábeis 10 anos (CC 205); folha/ponto 5 anos; ASO/PPP/PPRA 20 anos;
     contrato de trabalho indeterminado; contratos de representação enquanto vigentes + 5–10 anos.
BR3  Documentos assinados (AFD/AEJ) MUST registrar signedFormat (ex.: CAdES_P7S) e preservar o
     arquivo assinado original (não regerar).
BR4  DocumentRequirement (policy) define, por tipo de lançamento financeiro, qual(is) documento(s)
     é/são obrigatório(s). Ex.: COMMISSION_PAYABLE → COMMISSION_INVOICE (+ PAYMENT_PROOF na liquidação);
     UTILITY_EXPENSE → UTILITY_BILL + PAYMENT_PROOF; AUTONOMOUS_SERVICE → RPA.
BR5  Um lançamento financeiro está **conforme** quando todos os documentos exigidos por seu tipo
     estão anexados. Anexar publica DocumentAttached.
BR6  CLOSE GUARD: ao Finance solicitar o fechamento de um período, Compliance MUST responder
     "não pode fechar" se existir, no período, lançamento **não conforme**, e publicar RequirementUnmet
     listando os lançamentos pendentes. (A trava do período é do Finance; o veto é do Compliance.)
BR7  Expurgo de um Document MUST ser rejeitado enquanto now < retentionUntil
     => 409 compliance.retention.not-expired.
BR8  Documentos com dado pessoal MUST ter acesso controlado e cada acesso/baixa MUST ser auditado
     (LGPD — `security.md`).
```

## Input/Output Examples

```http
POST /api/compliance/documents          (multipart: file + metadados)
{ "type":"NFSE", "issuedAt":"2026-06-20", "linkedEntry": {"id":"e91...","type":"COMMISSION_RECEIVABLE"} }
201 Created
{ "id":"d44...", "type":"NFSE", "hash":"sha256:...", "retentionUntil":"2031-06-20",
  "signedFormat": null, "createdAt":"2026-06-26T14:00:00Z" }
# efeito: DocumentAttached -> lançamento e91 fica conforme
```

```http
GET /api/compliance/close-check?period=2026-06
200 OK
{ "period":"2026-06", "canClose": false,
  "pending": [ {"entryId":"e07...","type":"UTILITY_EXPENSE","missing":["UTILITY_BILL"]} ] }
```

## API Contracts

- `POST /api/compliance/documents` — upload (multipart) + metadados + vínculo opcional. Valida
  tamanho, tipo, extensão, content-type e nome (nunca confia na extensão) → 201.
- `POST /api/compliance/documents/{id}/attach` — vincula a um lançamento `{entryId, entryType}` → 200.
- `GET /api/compliance/documents/{id}` (metadados) e `.../content` (download autorizado/auditado) →
  200 | 404 `compliance.document.not-found`.
- `GET /api/compliance/close-check?period=YYYY-MM` → relatório de conformidade do período (consumido
  pelo Finance no fechamento).
- `DELETE /api/compliance/documents/{id}` → 204 | 409 `compliance.retention.not-expired`.
- OpenAPI atualizada.

## Events

- `DocumentAttached` — `{documentId, entryId, entryType, occurredAt}`. Produtor: `compliance`.
- `RequirementUnmet` — `{period, pendingEntries[], occurredAt}`. Consumidores: `finance` (veto de
  fechamento), `intelligence` (8.2-H: o que falta anexar para fechar o mês).
- `RetentionExpiring` — `{documentId, retentionUntil, occurredAt}` (job). Consumidor: `intelligence`
  (higiene do cofre).

## Persistence Changes

```txt
V7__create_compliance.sql
  documents(
    id uuid PK, type varchar not null, file_ref varchar not null, hash varchar not null,
    issued_at date not null, retention_until date not null, signed_format varchar null,
    has_personal_data boolean not null default false,
    created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null
  )
  document_attachments(
    id uuid PK, document_id uuid not null REFERENCES documents(id),
    entry_id uuid not null, entry_type varchar not null,        -- referência a Finance (valor)
    attached_at timestamptz not null, attached_by varchar null,
    UNIQUE (document_id, entry_id)
  )
  document_requirements(                                        -- policy/seed (dado de sistema)
    entry_type varchar not null, required_document_type varchar not null, phase varchar not null,
    PRIMARY KEY (entry_type, required_document_type, phase)
  )
-- seed da tabela de retenção (RetentionPolicy) e dos requirements: Flyway seed (dado essencial de sistema)
```

`FileStorage` é **porta** (interface no módulo); o adaptador (filesystem/S3/…) vive em
`com.fksoft.infra.integration` (`messaging-and-integrations.md`: lógica não depende de SDK de storage).

## Validation Rules

- Delivery: validação de upload (tamanho/tipo/extensão/content-type/nome/autorização).
- Application: existência de documento nas ações; idempotência do attach (UNIQUE).
- Domain: cálculo de `retentionUntil` (BR2) e veto de expurgo (BR7) como invariantes.
- Integração: `close-check` é consistente com os lançamentos do Finance (referência por valor).

## Error Behavior

`compliance.document.not-found` → 404; `compliance.retention.not-expired` → 409;
`compliance.upload.invalid` → 400 (tipo/tamanho/conteúdo). i18n em `messages_pt_BR.properties`.
Erros **não** expõem caminho de arquivo nem dado sensível (`security.md`).

## Observability Requirements

- Logar `DocumentAttached`, acessos a documento com dado pessoal e expurgos como eventos de negócio
  (documentId, quem, correlation id) — **sem vazar conteúdo**.
- Métricas: `documents_total` por type, `close_blocked_total`, `retention_expiring_total`.
- Job de retenção: idempotência/locking/histórico (`messaging-and-integrations.md`).

## Tests Required

- **Unit/domain:** cálculo de `retentionUntil` por type (tabela 7.7, datas testáveis); veto de expurgo
  antes do prazo; conformidade de lançamento conforme `DocumentRequirement`.
- **Integração (Testcontainers):** upload válido 201; upload inválido 400; attach idempotente;
  `close-check` retorna `canClose=false` com pendência; expurgo no prazo → 409.
- **Regressão:** lançamento sem documento exigido bloqueia o fechamento (falha antes, passa depois).

## Acceptance Criteria

- Anexar a NFS-e da comissão deixa o lançamento conforme e define `retentionUntil` = +5 anos.
- Um período com um lançamento sem documento obrigatório retorna `canClose=false` e a lista do que falta.
- Tentar expurgar um fiscal dentro dos 5 anos retorna 409.
- `./mvnw verify` verde.

## Open Questions

- Catálogo final de **tipos de lançamento × documento exigido** (mapa completo) — confirmar com a
  contabilidade do cliente; o seed inicial cobre os casos da tabela 7.7.
- Política de **versão/substituição** de documento (NF cancelada e reemitida) — adiada.
- Necessidade de **carimbo do tempo/assinatura** própria ao ingerir (além do que já vem assinado) —
  adiada (depende do certificado, SPEC-0023).

## Out of Scope

Razão AP/AR e máquina de fechamento (SPEC-0015), custódia de certificado (SPEC-0023), emissão de NF
(SPEC-0016).
