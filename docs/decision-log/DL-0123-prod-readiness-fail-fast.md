# DL-0123 — Fail-fast de prontidão de produção (recusa subir com default de dev)

- **Fase:** 19c
- **Spec(s):** SPEC-0024, SPEC-0016 (flag de regime — DL-0121)
- **ADR relacionado:** `architecture/security.md`
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

Todos os segredos tinham **default de dev** que valeria silenciosamente em produção: os 2 segredos
HMAC de webhook, a chave-mestra do cofre (`PLATFORM_SECRET_KEY` vazia → default inseguro), a senha
do banco (`acme`), o issuer OIDC em `http://`. E uma NFS-e real não deve ser emitida antes do
contador confirmar o regime (DL-0121).

## Decisão

`ProdReadinessValidator` (`@Profile("prod")`, em `ApplicationReadyEvent`) **recusa subir** listando
cada configuração insegura: segredos de webhook = default/vazio, `PLATFORM_SECRET_KEY` vazia, senha
de DB default, issuer `http://`, `billing.tax.regime-confirmed=false`. Lança `IllegalStateException`
com a lista (nenhum valor de segredo é logado — só o nome da propriedade e o motivo). Dev/test/e2e
mantêm os defaults convenientes.

## Justificativa

- "Secure by default" para produção: uma subida mal configurada vira **falha explícita**, não um
  sistema silenciosamente inseguro.
- Barato e testável direto (o validador é instanciável sem contexto).

## Alternativas descartadas

- **Só documentar os overrides:** depende de disciplina; a fatia existe justamente porque o default
  de dev valia em produção.
- **Falhar em qualquer perfil:** quebraria dev/test; o gate é só `prod`.

## Impacto

- **Arquivos:** `ProdReadinessValidator` + `ProdReadinessValidatorTest`. Sem migração/contrato.
- **Config:** consome as propriedades já existentes + `billing.tax.regime-confirmed` (DL-0121).

## Como reverter

Barata: remover o componente. Recomenda-se manter.
