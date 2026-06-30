# DL-0072 — Holerite/espelho arquivado no Compliance (PAYROLL, retenção 5 anos), referenciado por valor

- **Fase:** 8i
- **Spec(s):** SPEC-0022 (BR1/BR5/BR6), SPEC-0008 (cofre)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

BR5 manda que "holerite/espelho processado seja arquivável no Compliance (PAYROLL) com retenção de
5 anos" e BR1 que "o contrato de trabalho é documento no Compliance (retenção indeterminada)". A
spec não detalha **como** People arquiva sem virar cofre nem furar a fronteira (BR6: o legal vive no
Compliance, não em People).

## Decisão

1. **People não arquiva direto:** um **orquestrador em `infra`** (driving) recebe o upload e chama
   a fachada `ComplianceService.upload(DocumentType.PAYROLL, bytes, filename, contentType, issuedAt,
   signedFormat=null, hasPersonalData=true, …)`. O Compliance já calcula `retentionUntil = +5 anos`
   (PAYROLL → FISCAL na `RetentionPolicy`) e o hash; People só guarda o `documentId` retornado
   **por valor** (coluna `payslip_document_id`/uso na journey ou referência avulsa), nunca um FK.
2. **`hasPersonalData=true`** sempre (holerite = dado pessoal): o Compliance já audita acesso
   (LGPD). People **não** loga PII (BR Observability).
3. **Contrato de trabalho (BR1):** referenciado por valor em `employees.contract_document_id`
   (uuid, valor) — o documento (retenção indeterminada/contrato) é ingerido no Compliance pelo fluxo
   de documentos existente; People só guarda o id.
4. **Endpoint:** `POST /api/people/employees/{id}/payslip` (multipart: arquivo + `period`), que
   delega ao orquestrador `infra` e devolve o `documentId` arquivado.

## Justificativa

- **SPEC-0022 BR6 / Modulith:** o legal/retentção é do Compliance; People referencia por valor, sem
  FK cross-contexto. Espelha o padrão já usado em Billing→Compliance (DL-0047) e Assets→Compliance
  (DL-0064/0065): **orquestrador em `infra` chama a fachada do cofre**, módulo de negócio não vira
  cofre.
- **Compliance pronto:** `DocumentType.PAYROLL` e a `RetentionPolicy` (5 anos) **já existem** —
  Regra Zero (reusar, não duplicar).
- **LGPD (`architecture/security.md`):** `hasPersonalData=true` aciona a trilha de acesso do cofre;
  People mantém logs sem PII.

## Alternativas descartadas

- **People guardar o binário/retenção:** duplicaria o cofre e furaria BR6 (o legal viraria
  responsabilidade de RH).
- **FK `employees → documents`:** proibido (cross-context FK / Modulith); referência é valor.
- **People chamar `ComplianceService` direto do domínio:** acoplaria o domínio People à fachada de
  outro módulo de comando de forma desnecessária; o orquestrador `infra` mantém o domínio limpo
  (mesmo padrão de Billing/Assets).

## Impacto

- **Specs:** SPEC-0022 — BR1/BR5 viram "ASSUMIDO (ver DL-0072)".
- **Arquivos:** orquestrador `infra` (ex.: `PayslipArchivingService`); controller People
  (`/payslip`); coluna `employees.contract_document_id uuid`.
- **Migração:** `employees(... contract_document_id uuid null)`.
- **Contratos:** `POST /api/people/employees/{id}/payslip`.

## Como reverter

Trocar o destino do arquivo (outro cofre/sistema) muda só o orquestrador `infra` (a porta
`ComplianceService`/`FileStorage` já abstrai o storage). Reversão **Barata** e localizada.
