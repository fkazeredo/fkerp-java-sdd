# DL-0074 — Custódia do e-CNPJ: criptografia at-rest AES-256-GCM (envelope), chave fora do banco

- **Fase:** 8j
- **Spec(s):** SPEC-0023 (BR1, Open Questions: onde custodiar / A1×A3), `architecture/security.md`
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Cara

## Lacuna

A SPEC-0023 deixa **em aberto** (Open Questions) **onde** custodiar o e-CNPJ (KMS de nuvem × HSM ×
secret manager on-prem) e o tipo de certificado **A1 (arquivo)** × **A3 (token/cartão)** — explicitamente
"decisão de infra/segurança do dono". Mas BR1 é inegociável: o material (certificado + chave privada +
senha) **MUST NUNCA** aparecer em código, log, mensagem de erro ou banco em claro, e a assinatura é
operação de porta sem expor o material. Para entregar a fatia precisamos de um **mecanismo de custódia
defensável agora**, sem travar a escolha de infra do dono.

## Decisão

1. **Modelo de custódia atrás de uma porta trocável.** O domínio expõe `CertificateCustody` (guarda
   metadados + material cifrado, devolve só `CertificateView` com metadados) e `CertificateSigner`
   (assina sem expor material). O **adaptador** vive em `infra.platform`.
2. **Criptografia at-rest = AES-256-GCM (envelope/AEAD).** O material sigiloso é cifrado por uma porta
   `SecretCipher` (adaptador `AesGcmSecretCipher`): AES/GCM/NoPadding, IV de 96 bits aleatório por
   operação, tag de 128 bits; o registro guarda `iv || ciphertext || tag` (binário) + um `key_alias`
   (qual chave mestra cifrou). GCM é **autenticado** (AEAD): adulteração no banco é detectada na
   decifra.
3. **A chave mestra mora FORA do banco.** Injetada por ambiente (`PLATFORM_SECRET_KEY`, 32 bytes
   base64), nunca em migração/seed/log. Sem a chave, o `encrypted_material` é inútil — o banco sozinho
   não revela segredo. O `key_alias` permite rotação (cifrar novo material com a chave nova; os antigos
   seguem decifráveis pelo alias).
4. **Só metadados saem.** `platform_certificates` guarda em claro apenas `subject`, `holder_document`
   (CNPJ do titular — mascarado em log), `fingerprint` (SHA-256 do DER), `valid_from`/`valid_until`,
   `status`. O material vai **só** em `encrypted_material bytea`. Nenhum DTO/evento/log carrega material
   ou senha (ArchUnit + teste de regressão de segurança garantem).
5. **A1 como ponto de partida** (arquivo PFX/PEM cifrado no cofre), com a porta desenhada para A3 depois
   (um adaptador que delega a um token/HSM implementaria `CertificateSigner` sem material em trânsito) —
   sem refator de domínio.

## Justificativa

- **`security.md`:** "never log/store secrets without reason; mask sensitive values; never send secrets
  to the frontend" — atendido: material cifrado, chave fora do banco, exposição só de metadados.
- **AES-256-GCM** é a recomendação corrente do NIST SP 800-38D para cifragem autenticada e é o algoritmo
  que KMS de nuvem (AWS/GCP/Azure) usa por baixo para envelope encryption — então **migrar para um KMS
  real depois é trocar o adaptador `SecretCipher`**, não o esquema de dados.
- **Chave fora do banco** segue o princípio de separação de segredo (a base comprometida não basta para
  decifrar) — o degrau mínimo defensável enquanto o dono não escolhe KMS/HSM.
- **Porta trocável** honra a Open Question: a spec continua aberta sobre KMS×HSM×secret manager; quando o
  dono decidir, o adaptador muda e os metadados/contratos permanecem.

## Alternativas descartadas

- **Guardar o material em claro no banco / só com permissão de tabela:** viola BR1 frontalmente.
- **KMS de nuvem agora (AWS KMS/GCP KMS):** é a escolha de infra do dono (Open Question) e adicionaria
  dependência/credencial de nuvem antes da decisão — prematuro (Regra Zero). A porta deixa plugar depois.
- **HSM/A3 token agora:** exige hardware/infra que o dono ainda não definiu; modelado como evolução da
  porta.
- **Cifra simétrica sem AEAD (AES-CBC):** sem autenticação, não detecta adulteração; GCM é superior e
  padrão atual.

## Impacto

- **Specs:** SPEC-0023 — BR1 e a Open Question de custódia viram "ASSUMIDO (ver DL-0074)".
- **Arquivos:** `domain.platform`: `PlatformCertificate`, `CertificateCustody`, `CertificateSigner`,
  `SecretCipher` (porta), `CertificateView`, `CertificateStatus`, `CertificateUnavailableException`.
  `infra.platform`: `AesGcmSecretCipher`, adaptador de custódia/assinatura.
- **Migração:** `platform_certificates(... encrypted_material bytea NOT NULL, key_alias varchar, ...)`.
- **Config:** `PLATFORM_SECRET_KEY` (env; default de dev explícito e marcado como **não-produção**).
- **Contratos:** `GET /api/platform/certificate/status` retorna **só metadados** — nunca material.

## Como reverter

Trocar o mecanismo de custódia (para AWS/GCP KMS, HSM/A3, Vault) muda **só o adaptador** de
`SecretCipher`/`CertificateSigner` em `infra.platform` — o domínio e os contratos REST não mudam. Porém,
**o material já cifrado precisa ser re-cifrado/re-importado** pelo novo cofre e a chave mestra migrada
com cautela operacional: por isso a reversão é **Cara** (envolve segredo real, rotação e janela de
migração), ainda que o código de domínio fique intacto. A escolha A1×A3 e KMS×HSM é do dono; até lá, o
default é o envelope AES-GCM com chave por ambiente.

## Revisão — Fase 19b (2026-07-02)

**MANTIDA + recomendação registrada.** O envelope AES-256-GCM com chave por ambiente segue o
degrau defensável; para produção, a recomendação da revisão é (a) **certificado A1** (assinatura
server-side automatizada não funciona com A3/token unattended) e (b) migrar o `SecretCipher`
para KMS gerenciado quando a hospedagem for definida (checklist 19l). Registrado na SPEC-0023.
