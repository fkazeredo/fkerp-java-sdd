# Caderno de testes — Slice 11c: Ingestão do AFD/AEJ assinado → cofre (SPEC-0012, BR4)

## Escopo

O caminho **legal** do ponto: ingestão do **AFD/AEJ assinado** (CAdES `.p7s`) pela **exportação
oficial** (upload — DL-0029), com **verificação de assinatura/integridade** na entrada (DL-0032) e
guarda no **cofre da Compliance** (reuso de SPEC-0008: `FileStorage` + `RetentionPolicy` + `Document`),
preservando o arquivo original com `signedFormat=CAdES_P7S` e **retenção de 5 anos**. Endpoint
`POST /api/integration/point/afd`; evento `LegalTimeRecordArchived`. Cobre o Acceptance Criteria "O
AFD/AEJ assinado é guardado no cofre com `signedFormat=CAdES_P7S` e retenção de 5 anos" e o Error
Behavior `point.afd.invalid` → 400.

## Casos de teste

### Unitário — `Pkcs7AfdSignatureVerifierTest` (verificação de assinatura/integridade)
| Caso | Verifica | Regra |
|---|---|---|
| `acceptsAWellFormedSignedEnvelopeWithMatchingContentHash` | envelope PKCS#7 signed-data + SignerInfo + hash do conteúdo confere → **válido** | BR4 |
| `rejectsTamperedContentWhoseHashDoesNotMatch` | conteúdo adulterado (hash ≠ declarado) → **rejeita** | BR4 (tamper) |
| `rejectsAnUnsignedEnvelopeWithoutSignerInfo` | envelope sem SignerInfo → rejeita | BR4 (assinatura presente) |
| `rejectsAFileThatIsNotAPkcs7Envelope` | arquivo que não é envelope assinado → rejeita | BR4 |
| `rejectsNullOrEmptyInput` | nulo/vazio/sem hash → rejeita | validação |

### Integração (Testcontainers) — `AfdIngestionIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `validSignedAfdIsArchivedInTheVaultWithFiveYearRetention` | AFD válido → 201 `Document` com `signedFormat=CAdES_P7S`, `hasPersonalData=true`, `retentionUntil=+5 anos` (2026-06-30 → 2031-06-30); 1 doc no cofre | **BR4 / Acceptance** |
| `aejIsArchivedToo` | AEJ válido → 201 `Document` `PROCESSED_JOURNAL_AEJ` + CAdES_P7S | BR4 |
| `tamperedAfdIsRejectedWith400AndNothingIsStored` | AFD adulterado → 400 `point.afd.invalid`; **nada guardado** | BR4 (regressão) |
| `aFileThatIsNotASignedEnvelopeIsRejectedWith400` | arquivo não assinado → 400; nada guardado | BR4 |

### Arquitetura
A ingestão vive em `infra.integration.pointclock` (ACL) e roteia para o cofre via
`ComplianceService.upload` (reuso, sem duplicar). `people → compliance` (evento
`LegalTimeRecordArchived` referencia `DocumentType` por valor) — Spring Modulith `verify()` verde, **sem
ciclo** (compliance não depende de people). `PointAfdInvalidException` registrada em `HttpErrorMapping`
(400; teste de completude verde). i18n pt-BR + fallback. LGPD: AFD marcado `hasPersonalData=true`
(acesso auditado pelo cofre); nunca logar conteúdo/credenciais.

## Resultado

`cd backend && ./mvnw verify` → **BUILD SUCCESS**. `Tests run: 206` (slice 11b: 197 → +9). Spotless
clean, **0 Checkstyle violations**, ArchUnit (9 regras) + Spring Modulith (11 módulos) verdes. Sem
migração nova (AFD é `Document` no cofre existente).

## Cobertura — o que NÃO está coberto e por quê

- **Validação criptográfica completa ICP-Brasil** (cadeia de certificado, CRL/OCSP, carimbo de tempo)
  — fora de escopo (DL-0032): depende do certificado custodiado pelo `Platform` (SPEC-0023). A porta
  `AfdSignatureVerifier` foi desenhada para receber essa verificação mais forte depois sem mudar os
  chamadores. Aqui prova-se a verificação estrutural (envelope + assinatura presente + hash do conteúdo).
- **Tratamento de jornada** (banco de horas, divergências) — é SPEC-0022 (People consome o snapshot);
  fora de escopo.
- **Tela Angular** — backend-first.

## Como reproduzir

```bash
cd backend && ./mvnw verify
./mvnw test -Dtest=Pkcs7AfdSignatureVerifierTest,AfdIngestionIntegrationTest
```
