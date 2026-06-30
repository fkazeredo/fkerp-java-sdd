# DL-0078 — `CertificateSigner` graduado para o Platform; stub do Billing delega à custódia

- **Fase:** 8j
- **Spec(s):** SPEC-0023 (BR1; Scope: a porta `CertificateSigner` e seu adaptador), SPEC-0016 (DL-0046)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

O Billing (SPEC-0016/DL-0046) já definiu a porta `com.fksoft.domain.billing.CertificateSigner` e um
**stub** (`StubECnpjCertificateSigner` em `infra.integration.nfse`) que "assina" anexando um marcador,
com o comentário explícito "a custódia real é da Platform (SPEC-0023)". Agora a Platform existe.
Precisamos decidir **onde** a porta de assinatura vive e **como** não quebrar o Billing.

## Decisão

1. **A porta de assinatura passa a ser do Platform:** `com.fksoft.domain.platform.CertificateSigner`
   (assina sem expor material, lendo a chave da custódia cifrada — DL-0074). É a porta que a SPEC-0023
   lista no Scope como dona da Platform.
2. **Back-compat sem quebrar contratos do Billing:** a porta `billing.CertificateSigner` **permanece**
   (o Billing depende dela); seu adaptador `StubECnpjCertificateSigner` passa a **delegar** ao
   `platform.CertificateSigner` quando houver certificado custodiado, caindo no comportamento stub
   determinístico quando não houver (dev/sem certificado). Assim o seam Billing→assinatura agora chega à
   custódia real, sem o domínio do Billing conhecer o Platform (a delegação é no adaptador `infra`).
3. **Material nunca exposto:** o `platform.CertificateSigner` recebe bytes e devolve bytes assinados; em
   nenhum ponto retorna a chave/senha; nada é logado além de metadados (security.md/BR1).

## Justificativa

- **SPEC-0023 Scope** atribui `CertificateSigner` à Platform; graduar a porta para lá cumpre a spec sem
  duplicar custódia.
- **Sem big-bang no Billing:** manter `billing.CertificateSigner` e delegar no adaptador evita refator do
  domínio do Billing e mantém os testes de NFS-e verdes (Regra Zero — mudança mínima).
- **Fronteira:** a ponte Billing→Platform vive em `infra` (adaptador), não no domínio — `domain.billing`
  não passa a depender de `domain.platform` (Modulith preservado).

## Alternativas descartadas

- **Apagar `billing.CertificateSigner` e fazer o Billing depender do Platform:** acoplaria dois domínios
  e quebraria os testes/contrato do Billing — desnecessário; a delegação no adaptador resolve.
- **Manter a assinatura só no stub do Billing:** deixaria a custódia real fora do fluxo, contrariando a
  SPEC-0023.

## Impacto

- **Specs:** SPEC-0023 — item de Scope (porta `CertificateSigner`) vira "ASSUMIDO (ver DL-0078)".
- **Arquivos:** nova `domain.platform.CertificateSigner` + adaptador em `infra.platform`;
  `StubECnpjCertificateSigner` (Billing) passa a delegar (mantém a porta `billing.CertificateSigner`).
- **Contratos:** nenhum REST muda; o seam de assinatura do Billing passa a alcançar a custódia.

## Como reverter

Desfazer a delegação (voltar o stub a assinar sozinho) é uma linha no adaptador do Billing; a porta do
Platform pode coexistir. Reversão **Moderada** e localizada em `infra`.
