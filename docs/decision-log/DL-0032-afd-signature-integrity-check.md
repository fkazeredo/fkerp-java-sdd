# DL-0032 — Verificação de assinatura/integridade do AFD/AEJ na ingestão: validar o envelope CAdES/PKCS#7 e o hash; preservar o `.p7s` original; reuso do cofre da Compliance

- **Fase:** 6 (Crawler de ponto)
- **Spec(s):** SPEC-0012 (BR4 "preservar o arquivo assinado original; signedFormat=CAdES_P7S; retenção 5 anos;
  a integridade (hash/assinatura) MUST ser verificada na ingestão"; Error `point.afd.invalid` → 400; Tests
  Required "verificação de assinatura; AFD inválido → 400")
- **Spec(s) reusada:** SPEC-0008 (cofre `Document`, `RetentionPolicy`, `FileStorage`, `SignedFormat.CAdES_P7S`,
  `DocumentType.TIME_RECORD_AFD`/`PROCESSED_JOURNAL_AEJ`)
- **ADR relacionado:** 0011 (registro de exceção → status), 0010 (porta de verificação)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0012 exige verificar **assinatura/integridade** do AFD/AEJ na ingestão e rejeitar o **adulterado/inválido**
com `point.afd.invalid` (400), mas não diz **quão fundo** validar (validação criptográfica completa da cadeia
ICP-Brasil × verificação estrutural do envelope) nem como evitar overengineering. O certificado ICP-Brasil de
verdade é custódia do `Platform` (SPEC-0023, fora de escopo).

## Decisão

- **Verificar, na ingestão, a integridade estrutural e o hash do envelope CAdES/PKCS#7 (`.p7s`)** por uma porta
  `AfdSignatureVerifier`:
  1. o arquivo é um **envelope PKCS#7/CAdES `signed-data` bem-formado** (cabeçalho/estrutura ASN.1 reconhecível);
  2. o **conteúdo assinado casa com o hash esperado** informado nos metadados do upload (o operador/exportação
     informa o `expectedContentHash` sha-256); divergência = **adulterado** → rejeita;
  3. há **pelo menos um `SignerInfo`** (existe assinatura) e o **digest do conteúdo bate** com o do envelope.
- **Rejeitar** com `PointAfdInvalidException` (`point.afd.invalid`, **400**) quando qualquer item falha (envelope
  malformado, sem assinatura, hash divergente).
- **Preservar o `.p7s` original** (BR4 — não regerar): o arquivo assinado é o que vai para o `FileStorage`; o
  `Document` no cofre guarda `signedFormat=CAdES_P7S`, `hash` do conteúdo e `retentionUntil = +5 anos`
  (`RetentionPolicy` já mapeia `TIME_RECORD_AFD`/`PROCESSED_JOURNAL_AEJ` para 5 anos).
- A **validação criptográfica completa da cadeia ICP-Brasil** (validar certificado contra a AC raiz, CRL/OCSP,
  carimbo de tempo) **fica para o `Platform` (SPEC-0023)**, que custodia o certificado — marcado como costura
  rastreável, sem lógica falsa.

## Justificativa

- **`messaging-and-integrations.md` (§Files):** uploads validam tipo/tamanho/conteúdo e **integridade**; nunca
  confiar na extensão. A verificação estrutural do envelope + conferência de hash é a checagem de integridade
  proporcional ao escopo desta fase (o cofre já confere hash; aqui acrescenta-se "é um `.p7s` assinado e o
  conteúdo não foi adulterado").
- **Regra Zero:** validar a cadeia ICP-Brasil completa exige o certificado e a infra de PKI que são do `Platform`
  (SPEC-0023); fazê-lo aqui seria antecipar escopo e duplicar custódia. A spec coloca certificado/credenciais
  **fora de escopo** desta fatia.
- **Testabilidade:** o teste injeta um `.p7s` válido (envelope bem-formado + hash batendo) → guarda no cofre com
  retenção 5 anos; e um **adulterado** (hash divergente / envelope quebrado) → 400, nada guardado. Determinístico,
  sem PKI externa.

## Alternativas descartadas

- **Validação criptográfica completa ICP-Brasil aqui.** Descartada: depende do certificado custodiado pelo
  `Platform` (SPEC-0023, fora de escopo); seria overengineering e custódia duplicada nesta fase.
- **Só conferir o hash, sem olhar o envelope.** Descartada: não distinguiria um `.txt` qualquer de um AFD
  **assinado**; a spec pede verificação de **assinatura** (BR4), então exige reconhecer o envelope CAdES e a
  presença de `SignerInfo`.
- **Aceitar o upload sem verificação e validar depois.** Descartada: BR4 exige verificação **na ingestão**; um
  artefato legal adulterado não pode entrar no cofre.

## Impacto

- **Arquivos:** porta `AfdSignatureVerifier` + adaptador `Pkcs7AfdSignatureVerifier` (em `infra.integration` ou
  no caso de uso de ingestão), `PointAfdInvalidException` (registrada em `HttpErrorMapping` → 400), i18n
  `point.afd.invalid`.
- **Endpoint:** `POST /api/integration/point/afd` (multipart: `.p7s` + `type` + `issuedAt` + `periodRef` +
  `expectedContentHash`) → `Document` no cofre (reuso de `ComplianceService.upload`) → evento
  `LegalTimeRecordArchived`.
- **Migrações:** nenhuma (AFD é `Document` no cofre existente, sem tabela própria — SPEC-0012 Persistence).

## Como reverter

Reversão **moderada**: ligar a validação ICP-Brasil completa (quando `Platform`/SPEC-0023 custodiar o
certificado) é estender o `AfdSignatureVerifier` sem mexer no endpoint, no cofre ou no contrato — a porta foi
desenhada para receber a verificação mais forte depois. Afrouxar a verificação não é opção (BR4).
