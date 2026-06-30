# Caderno de testes — Fatia 8j-1 (Custódia do e-CNPJ)

## Escopo

SPEC-0023, BR1 (custódia segura; segredo nunca em claro) e BR5 (alerta de expiração).
Acceptance Criteria cobertos: "a validade do certificado é monitorada e alerta"; "Billing assina via
porta sem o material vazar" (seam graduado, DL-0078); regressão de segurança "nenhum log/erro contém o
material do certificado". DLs: DL-0073 (módulo), DL-0074 (criptografia at-rest), DL-0078 (signer).

## Casos de teste

### Unitário — `AesGcmSecretCipherTest`
| Caso | Verifica | Regra |
|---|---|---|
| `roundTripsTheSecretMaterial` | cifra→decifra devolve o segredo; ciphertext não contém o plaintext | BR1/DL-0074 |
| `usesAFreshIvSoTheSameSecretEncryptsDifferently` | IV aleatório → mesmo segredo gera envelope diferente | DL-0074 |
| `detectsTamperingInsteadOfReturningCorruptMaterial` | bit flip na tag GCM → falha controlada (AEAD) | BR1 |
| `rejectsAKeyThatIsNotThirtyTwoBytes` | chave != 32 bytes é rejeitada (AES-256) | DL-0074 |
| `fallsBackToTheDevKeyWhenUnsetButStillRoundTrips` | sem `PLATFORM_SECRET_KEY` usa default de dev e ainda round-trips | DL-0074 |

### Integração (Testcontainers/Postgres) — `CertificateCustodyIntegrationTest`
| Caso | Verifica | Regra |
|---|---|---|
| `statusReturnsOnlyMetadataAndTheMaterialIsNeverStoredInClear` | status = só metadados; coluna `encrypted_material` é ciphertext (não o plaintext); cipher round-trips | BR1/DL-0074 |
| `statusWithoutAnyCertificateIs404` | sem certificado custodiado → `CertificateNotFoundException` (404) | Error Behavior |
| `expirySweepFlagsAnExpiringCertificateOnceAndAlerts` | certificado a 10 dias → EXPIRING, alerta 1×, 2ª varredura idempotente (0) | BR5 |
| `theSecretMaterialNeverAppearsInTheSystemAuditOrCertificateRows` | **regressão de segurança**: segredo não aparece em nenhuma linha persistida | BR1 |

### Arquitetura — `ArchitectureTest` + `ArchitectureRulesHaveTeethTest`
| Caso | Verifica | Regra |
|---|---|---|
| `PLATFORM_ORCHESTRATES_NEVER_OWNS_DOMAIN_RULES` | Platform não depende de `*Service`/`internal` de outro módulo de negócio | BR6 |
| `platformRuleFailsWhenPlatformDependsOnACommandFacade` | a regra tem **dentes**: o fixture que toca `BookingService` falha | BR6 |
| `ModularityTests.verifiesModularStructure` | grafo Modulith acíclico com o novo módulo `platform` (20º) | DL-0073 |
| `HttpErrorMappingCompletenessTest` | `CertificateNotFound`(404)/`CertificateUnavailable`(503) mapeadas | ADR 0011 |

## Resultado

`./mvnw -o test -Dtest='AesGcmSecretCipherTest,CertificateCustodyIntegrationTest,ArchitectureTest,
ArchitectureRulesHaveTeethTest,ModularityTests,HttpErrorMappingCompletenessTest'` →
**Tests run: 29, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS.** (`./mvnw verify` completo roda na
integração da fatia.) Migração V28 aplicada (Flyway "now at version v28"). Spotless aplicado.

## Cobertura / não coberto

- **Não coberto:** parsing real de PFX/PEM ICP-Brasil e assinatura CAdES/XAdES com a chave privada real —
  é a Open Question A1×A3/KMS do dono (DL-0074); o signer usa o material custodiado via HMAC para provar
  o seam sem expor segredo. A custódia real (KMS/HSM) troca só o adaptador `SecretCipher`/`CertificateSigner`.

## Como reproduzir

```
cd backend && ./mvnw -o test -Dtest=AesGcmSecretCipherTest                 # unit cipher
cd backend && ./mvnw -o test -Dtest=CertificateCustodyIntegrationTest      # integração (Docker up)
cd backend && ./mvnw verify                                                # tudo + portões
```
