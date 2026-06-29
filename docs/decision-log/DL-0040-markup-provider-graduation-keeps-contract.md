# DL-0040 — Graduação do `MarkupProvider`: motor real preservando o contrato (Quoting intacto)

- **Fase:** 8a (CommercialPolicy)
- **Spec(s):** SPEC-0014 (Scope "implementação real da porta MarkupProvider"; Tests "Quoting
  (regressão de fronteira): markup passa a vir do motor"; gradua o stub da SPEC-0005); SPEC-0005
  (porta `MarkupProvider`, `MarkupDecision`)
- **ADR relacionado:** 0011, 0012 ; `architecture/simulation-and-mocking.md` (graduação de stub
  rastreável)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0014 manda **graduar** o stub `SystemDefaultMarkupProvider` (Fase 1) no motor real **sem que
o Quoting saiba de precedência**. Falta decidir **como** substituir as entranhas mantendo o contrato:
`MarkupProvider.currentMarkup()` e o record `MarkupDecision(pct, source)` que o Quoting consome
(congela `markup.pct()`/`markup.source()` na proveniência da Quote, e o teste e2e do Quoting afirma
`source == "SYSTEM_DEFAULT"`).

## Decisão

1. **Manter o contrato existente intacto.** A interface `MarkupProvider` e o record `MarkupDecision`
   continuam onde estão (`com.fksoft.domain.commercialpolicy`, base pública). O `MarkupDecision`
   ganha apenas constantes de `source` para as novas camadas (`DIRECTIVE`/`PROMOTION`/`CONTRACT`/
   `POLICY`), **sem** remover `SYSTEM_DEFAULT` — mudança aditiva, retrocompatível.
2. **Adicionar variante com escopo** ao port, **sem quebrar** `currentMarkup()`:
   `MarkupDecision currentMarkup(MarkupScope scope)` (escopo = accountId/productRef/channel
   opcionais), e `currentMarkup()` (sem args) passa a delegar para `currentMarkup(MarkupScope.global())`.
   O Quoting é atualizado **na mesma fatia** para chamar a variante com o escopo da cotação
   (accountId + productRef quando houver), mantendo `verify` verde — é o "se mudar assinatura,
   atualize os callers na mesma slice".
3. **Substituir a implementação:** remover `SystemDefaultMarkupProvider` (o stub) e fazer o
   `CommercialPolicyService` **implementar** `MarkupProvider`. `currentMarkup(scope)`:
   - `resolve(MARKUP_PCT, scope)` no motor de precedência;
   - converte para `MarkupDecision(pct, source)` onde **`source` = a camada vencedora**
     (`provenance.layer().name()`), não mais sempre `SYSTEM_DEFAULT`.
   - Quando nada além do default casa, retorna `pct = 0`, `source = "SYSTEM_DEFAULT"` — **back-compat
     idêntica** ao stub (o teste e2e original do Quoting que afirma `SYSTEM_DEFAULT` **continua
     passando** sem alteração, porque sem regra acima do default o resultado é o mesmo).
4. **Regressão da graduação (Tests Required):** um teste novo cria uma `PROMOTION`/`DIRECTIVE` de
   `MARKUP_PCT` para a conta e compõe uma Quote: o markup aplicado é **≠ 0** e a proveniência da Quote
   diz `PROMOTION`/`DIRECTIVE` (não `SYSTEM_DEFAULT`) — prova que o stub foi **realmente graduado** e
   o número flui para o Quoting. Em paralelo, sem regra → ainda `SYSTEM_DEFAULT` (back-compat).

## Justificativa

- **simulation-and-mocking.md (graduação):** o stub era um *placeholder rastreável* que deve
  **graduar** quando a spec dona chega — exatamente esta fatia. Preservar a porta é o que mantém o
  Quoting "sem saber de precedência" (Scope da spec).
- **Aditivo > breaking:** acrescentar `currentMarkup(scope)` e delegar mantém qualquer chamador
  antigo válido (modules-and-apis.md: mudanças retrocompatíveis preferidas). O único caller real
  (Quoting) é atualizado na mesma fatia.
- **Back-compat provada:** manter `pct=0/source=SYSTEM_DEFAULT` como resultado-base garante que os
  179→219 testes herdados que dependem do markup default **não quebram**.

## Alternativas descartadas

- **Trocar a assinatura para só `currentMarkup(scope)` (remover a sem-arg).** Descartada: quebraria
  qualquer chamada existente desnecessariamente; a sobrecarga + delegação é mais segura e barata.
- **Manter o `SystemDefaultMarkupProvider` e fazê-lo consultar o motor.** Descartada: o `@Service`
  do motor já é o provedor natural; um segundo bean implementando a porta criaria ambiguidade de
  injeção. Um único `@Service` (`CommercialPolicyService`) implementa a porta.
- **Mudar `MarkupDecision` para carregar a `Provenance` rica (autor/quando).** Descartada nesta
  fatia: o Quoting só consome `pct`+`source`; enriquecer o record é mudança de contrato sem
  consumidor — a proveniência rica vive no `/resolve` e nos eventos, não no port do Quoting (Rule
  Zero).

## Impacto

- `MarkupProvider` (porta) ganha `currentMarkup(MarkupScope)`; `MarkupDecision` ganha constantes de
  source. **Remove** `SystemDefaultMarkupProvider`. `CommercialPolicyService` implementa a porta.
- `QuoteService.compose` passa o escopo (accountId + productRef) ao chamar o provider — única mudança
  no Quoting; seus testes herdados seguem verdes (back-compat) + 1 regressão nova de graduação.
- Sem mudança no JSON do Quoting (a Quote continua expondo `markup.pct`/`markup.source`).

## Como reverter

Reversão **moderada**: reintroduzir o stub e remover a sobrecarga `currentMarkup(scope)` (voltando o
caller do Quoting à sem-arg). Como o contrato base ficou intacto, o raio é o módulo
`commercialpolicy` + a linha do `QuoteService` — sem migração para desfazer o port (apenas o seed/
tabela, em nova `V`).
