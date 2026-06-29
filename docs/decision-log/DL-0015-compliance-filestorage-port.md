# DL-0015 — Compliance: porta `FileStorage` + adaptador filesystem; hash SHA-256

- **Fase:** 2 (Compliance mínimo)
- **Spec(s):** SPEC-0008 (escopo: "upload validado via porta `FileStorage`"; Persistence: "`FileStorage`
  é porta; o adaptador vive em `com.fksoft.infra.integration`")
- **ADR relacionado:** 0010 (camada de infra centralizada)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0008 manda abstrair o storage por porta, mas **não fixa** o adaptador concreto do v1 nem o
algoritmo de hash do conteúdo (BR1 exige `hash`, mas não o algoritmo).

## Decisão

- **Porta `FileStorage`** no módulo `compliance` (interface): `store(content, ...) -> fileRef`,
  `read(fileRef) -> bytes`, `delete(fileRef)`. O domínio nunca depende de SDK de storage
  (`messaging-and-integrations.md` §Files).
- **Adaptador `FilesystemFileStorage`** em `com.fksoft.infra.integration`, gravando sob uma raiz
  configurável (`compliance.storage.root`, default em `./var/compliance-vault`). É o único que conhece
  caminho de arquivo; o `fileRef` exposto é opaco (UUID), nunca o caminho.
- **Hash = SHA-256 do conteúdo** (hex, prefixado `sha256:`), calculado na ingestão (BR1). Validação de
  upload (tamanho/tipo/extensão/content-type/nome) no adaptador/delivery — nunca confiar na extensão.

## Justificativa

- A spec exige porta + adaptador em `infra.integration`; filesystem é o adaptador mais simples que
  satisfaz o v1 (POC single-instance, ADR 0002) e troca por S3/Azure depois sem tocar o domínio.
- SHA-256 é o padrão de integridade de conteúdo (resistente a colisão, amplamente disponível na JDK);
  o prefixo `sha256:` documenta o algoritmo e deixa a porta aberta para outro no futuro.
- Caminho de arquivo nunca vaza em erro/log (SPEC-0008 Error Behavior; `security.md`).

## Alternativas descartadas

- **Gravar o conteúdo no Postgres (bytea).** Descartado: infla a base e o backup, acopla storage a
  schema; a spec pede storage abstraído.
- **MD5/SHA-1 para o hash.** Descartado: fracos para integridade; SHA-256 é o mínimo defensável.
- **Adaptador S3 já no v1.** Descartado: exige credencial/infra externa fora do escopo do POC; a porta
  permite plugar quando precisar (a reversão é barata).

## Impacto

- `compliance`: porta `FileStorage`; `Document.hash` em SHA-256; validação de upload.
- `infra.integration`: `FilesystemFileStorage` + propriedade `compliance.storage.root`.
- Migração `V8__create_compliance.sql`: `documents.file_ref`, `documents.hash`.

## Como reverter

Implementar outra `FileStorage` (S3/Azure/GCS) e apontar a configuração para ela — sem tocar domínio
nem schema; mudança barata e localizada na camada de infra.
