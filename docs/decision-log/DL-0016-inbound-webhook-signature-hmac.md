# DL-0016 — Webhook de entrada: assinatura HMAC-SHA256 com segredo compartilhado

- **Fase:** 3 (Primeira integração real — ACL)
- **Spec(s):** SPEC-0009 (BR3 "validar a assinatura do webhook"; Open Question "Versão do contrato … e
  política de evolução — confirmar com o parceiro")
- **ADR relacionado:** 0010 (infra centralizada), 0011 (exceções sem transporte)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0009 exige que a ACL de entrada **valide a assinatura do webhook** (BR3) e cita "versão no
header/url", mas **não fixa o esquema de assinatura** nem o formato da versão do contrato — são itens
da Open Question a confirmar com o parceiro (site de cotação).

## Decisão

- **Assinatura = HMAC-SHA256** do corpo bruto da requisição, com um **segredo compartilhado**
  (`integration.quotation-site.secret`, default de DEV apenas), enviada no header **`X-Signature`** em
  hex minúsculo (opcionalmente prefixada `sha256=`, aceita ambos).
- **Comparação em tempo constante** (`MessageDigest.isEqual`) para evitar *timing attack*.
- Header **ausente** ou assinatura **divergente** ⇒ `integration.signature.invalid` ⇒ **401** e
  **nada é criado** (BR3).
- **Versão do contrato** trafega no **path** (`/api/integration/quotation-site/inbound`, v1 implícita) e
  o payload externo (`ExternalQuotationPayload`) vive **só na ACL** (`infra.integration`); evoluções de
  versão adicionam um tradutor novo sem tocar o domínio (BR6). A versão explícita no path/Accept fica
  para quando o parceiro publicar o contrato.

## Justificativa

- HMAC-SHA256 com segredo compartilhado é o esquema de assinatura de webhook **mais difundido** (padrão
  de Stripe/GitHub/Shopify) e suficiente para um consumidor *server-to-server*: não exige PKI nem
  rotação de certificado, satisfaz BR3 e a orientação de `messaging-and-integrations.md`/`security.md`
  ("webhook é contrato externo sério: assinatura, idempotência, versionamento").
- Comparação em tempo constante é o cuidado mínimo contra vazamento por tempo.
- A `simulation-and-mocking.md` autoriza o **mock rastreável**: como o site real está fora de escopo, o
  segredo é de DEV e o adaptador referencia SPEC-0009; a tradução ACL e o ramo INTEGRATED são testados
  ponta a ponta com um corpo assinado pelo mesmo segredo.

## Alternativas descartadas

- **mTLS / assinatura por certificado (ICP/JWS).** Descartada no v1: exige PKI e custódia de
  certificado (peça do `Platform`, SPEC-0023) — peso desproporcional para a primeira ACL (Regra Zero).
- **Token estático no header (Bearer fixo).** Descartada: não autentica o **corpo**; um corpo adulterado
  com o mesmo token passaria. HMAC liga a autenticidade ao conteúdo.
- **Sem versão alguma.** Descartada: a spec trata webhook como contrato sério; a versão no path deixa a
  porta aberta para evolução sem quebra.

## Impacto

- `infra.integration`: `QuotationSiteSignatureVerifier` (HMAC), propriedade
  `integration.quotation-site.secret`.
- `domain.sourcing`: exceção `IntegrationSignatureInvalidException` (`integration.signature.invalid`)
  registrada em `HttpErrorMapping` → 401.
- i18n: `integration.signature.invalid`.
- Contrato: header `X-Signature` documentado na OpenAPI do endpoint inbound.

## Como reverter

Trocar o `QuotationSiteSignatureVerifier` por outro esquema (mTLS/JWS) é uma mudança **localizada na
infra** + uma chave de configuração; o domínio (que só recebe um comando já validado) não muda.
Reversão moderada.
