# DL-0017 — Inbound: Account inexistente **rejeita** (não cria conta provisória nem enfileira)

- **Fase:** 3 (Primeira integração real — ACL)
- **Spec(s):** SPEC-0009 (Open Question: "Account inexistente no inbound: criar conta provisória,
  rejeitar, ou enfileirar para curadoria?"; Validation Rules: "resolução da `Account` pelo documento")
- **ADR relacionado:** 0014 (módulos/ordem)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** **Baixa** (é decisão de negócio — só o dono fecha)
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0009 marca como **Open Question de negócio** o que fazer quando o `account.document` do payload
externo **não corresponde a nenhuma Account** cadastrada. O ROADMAP não traz recomendação específica
para esta pergunta.

## Decisão

- **Rejeitar** a cotação de entrada quando a Account não existir para o documento:
  `integration.account.not-found` ⇒ **422 Unprocessable Entity**, **nada é criado** (sem Quote, sem
  registro em `inbound_quotations`).
- A resolução é por **documento** (CNPJ/CPF normalizado em dígitos) via uma **nova capacidade da fachada
  de Accounts** (`AccountDirectory.findIdByDocument(document)`), respeitando a fronteira modular
  (nenhum acesso ao repositório de Accounts a partir do Sourcing/ACL).
- Como a resolução **só conhece o documento** (não o `legalType`), a fachada resolve por número de
  documento normalizado; a unicidade real do cadastro é `(legalType, documentNumber)`, então a busca
  retorna a conta cujo `documentNumber` casa (no v1 não há colisão entre tipos para o mesmo número).

## Justificativa

- **Não inventar regra de negócio** (`CLAUDE.md` invariante 3): "criar conta provisória" embute
  política de cadastro (status, validação de DV, unicidade, quem é o dono comercial) que pertence ao
  **Accounts** (SPEC-0002) e **não foi decidida** — criá-la aqui seria expandir escopo e arriscar
  contas-fantasma sem CADASTUR/curadoria.
- **Não enfileirar para curadoria**: não existe contexto de curadoria/inbox no v1 (seria infra
  especulativa — `simulation-and-mocking.md` proíbe construir consumidor inexistente).
- **Rejeitar** é a opção **mais defensável e segura**: falha alto e cedo, sem efeito colateral, e
  preserva a integridade do cadastro. É reversível: quando o dono decidir "criar provisória" ou
  "curadoria", troca-se a política de resolução sem mexer no resto da ACL.
- Classifica como falha de negócio do conector (não 500), coerente com BR5 ("classificar falhas; nunca
  produzir resultado enganoso").

## Alternativas descartadas

- **Criar conta provisória automaticamente.** Descartada: inventaria regra de cadastro não decidida;
  risco de contas inválidas/duplicadas e de pular validação de documento da SPEC-0002.
- **Enfileirar para curadoria humana.** Descartada: exige um inbox/contexto de curadoria que não existe
  (escopo futuro); seria framework especulativo.

## Impacto

- `domain.accounts`: `AccountDirectory.findIdByDocument(String document): Optional<UUID>` +
  implementação em `AccountService` (lookup por `documentNumber` normalizado) + método no repositório.
- `domain.sourcing`: exceção `IntegrationAccountNotFoundException` (`integration.account.not-found`) →
  422 em `HttpErrorMapping`.
- i18n: `integration.account.not-found`.
- SPEC-0009: Open Question "Account inexistente" → Business Rules como **ASSUMIDO (ver DL-0017)**.

## Como reverter

Para mudar a política (criar provisória / curadoria), altera-se **um ponto** na aplicação do inbound
(a resolução da Account) e adiciona-se o fluxo correspondente; a ACL, a assinatura e o ramo INTEGRATED
não mudam. Como envolve regra de cadastro de Accounts, é reversão **moderada**.
