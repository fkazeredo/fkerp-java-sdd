# DL-0124 — Cofre: validação por magic bytes + fileRef como UUID (não confiar na extensão)

- **Fase:** 19c
- **Spec(s):** SPEC-0008
- **ADR relacionado:** `architecture/messaging-and-integrations.md` (§Files); DL-0015
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O Javadoc do `FilesystemFileStorage` **prometia** "never trust the extension alone", mas o código
só validava a extensão contra uma allowlist — divergência doc×comportamento (aderência — 19j). E
`read/delete` resolviam o `fileRef` recebido sem validar (defesa em profundidade contra traversal).

## Decisão

1. Para os tipos binários (pdf/png/jpg/jpeg) o upload valida os **magic bytes** (%PDF, ‰PNG,
   FFD8FF); tipos texto/DER (xml/txt/p7s) e upload programático sem nome não têm assinatura a
   checar. Conteúdo que contradiz a extensão declarada → `compliance.upload.invalid` (400).
2. `read/delete` só aceitam um `fileRef` que **parseia como UUID** (o ref é sempre gerado por nós);
   qualquer outra coisa → 400. Defesa em profundidade contra path traversal.

## Justificativa

- Fecha o gap doc×código e o vetor "arquivo malicioso com extensão inocente".
- Mantém p7s/xml/txt permissivos (p7s pode ser PEM ou DER; xml/txt são texto arbitrário).

## Alternativas descartadas

- **Sniffing completo por biblioteca (Tika):** dependência pesada para o ganho; magic bytes dos
  tipos binários cobrem o risco (Regra Zero).

## Impacto

- **Arquivos:** `FilesystemFileStorage` (magic bytes + `safeRef`); testes de upload prefixam %PDF;
  novo caso "pdf que não é pdf → 400".

## Como reverter

Barata: remover a checagem de magic bytes. O `safeRef` (UUID) é defesa barata — manter.
