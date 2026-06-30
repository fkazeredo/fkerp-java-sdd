# DL-0088 — Endpoints sensíveis do Admin exigem ROLE_FINANCE e são auditados

- **Fase:** 8l (Admin)
- **Spec(s):** SPEC-0025 (BR6 — toda alteração auditada); SPEC-0024 (Identity, BR2/BR10/DL-0082/DL-0083)
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0025 BR6 manda "toda alteração de fornecedor/contrato **ser auditada**", mas não diz **qual
papel** gateia os endpoints administrativos. A Identity (SPEC-0024/DL-0082) fechou as ações
sensíveis já citadas em outras specs (`finance:period:close`→FINANCE, etc.), mas o Admin é novo e
**não** estava no mapa de ações sensíveis do DL-0082. Sem decidir, os endpoints de
fornecedor/contrato/despesa ficariam apenas `authenticated` (qualquer papel), e a auditoria de
alteração não teria trilha consistente.

## Decisão

1. **Papel:** as **escritas** do Admin (registrar fornecedor, registrar contrato, registrar despesa)
   exigem **`ROLE_FINANCE`** — é o papel que já gateia o fechamento e a emissão de NF (DL-0082), e a
   despesa administrativa **gera lançamento financeiro** (AP). Negação → **403 + auditoria**
   (`ACCESS_DENIED` no `system_audit`, via o handler já existente, DL-0083). As **leituras**
   (GET fornecedor/lista/contratos) ficam `authenticated` (qualquer papel logado) — não são
   sensíveis. O gate é declarado na camada HTTP (`SecurityConfig.configure`), como os demais
   (DL-0082).
2. **Auditoria de alteração (BR6):** cada cadastro/alteração de fornecedor/contrato e cada registro
   de despesa é **auditado** reusando o `system_audit` do Platform (DL-0077/DL-0083) — sem tabela
   nova (Regra Zero), só metadados mascarados (ator, ação, `supplierRef`/`contractId`, correlation
   id; **nunca** identificador completo/segredo). O `AdminService` registra os eventos de negócio em
   log e publica os eventos de domínio; a trilha de auditoria sensível usa a fachada de auditoria já
   existente.

## Justificativa

- **SPEC-0024 BR2/BR10 / DL-0082:** ações sensíveis exigem o papel correspondente, declaradas na
  camada HTTP (Spring Security); `ROLE_FINANCE` já é o papel financeiro do projeto. Despesa
  administrativa = obrigação financeira (AP) → FINANCE é o fit natural.
- **SPEC-0025 BR6:** auditoria obrigatória de alteração — atendida reusando `system_audit` (não
  reinventar trilha, DL-0083).
- **`security.md`:** o backend é a autoridade final; gate no servidor, mensagem genérica, sem vazar
  dado sensível em erro; mascarar dado pessoal em log/auditoria.
- **`TestSecurityConfig` (DL-0081):** mantém os testes existentes verdes (ator de teste com acesso
  total) **com a segurança montada**; o teste novo de segurança sobe o caminho JWT real para provar
  403 sem `ROLE_FINANCE` e 200 com ele — sem afrouxar o gate.

## Alternativas descartadas

- **Papel próprio `ROLE_ADMIN`/`ROLE_PROCUREMENT`:** criaria um papel novo fora do catálogo fechado
  do DL-0082 sem necessidade — o administrativo é financeiro por natureza (gera AP). Manter o
  catálogo fechado é a regra do projeto.
- **Deixar tudo só `authenticated`:** violaria BR6 (sem gate, qualquer papel cria despesa que vira
  AP) e a fronteira de segurança que a Identity fechou.
- **Tabela `admin_audit` própria:** Regra Zero — o `system_audit` do Platform já é o seam de
  auditoria sensível (DL-0083).

## Impacto

- **Specs:** SPEC-0025 BR6 — confirma papel FINANCE para escritas + auditoria via `system_audit`.
- **Arquivos:** `SecurityConfig.configure` ganha os matchers POST `/api/admin/suppliers`,
  `/api/admin/suppliers/*/contracts`, `/api/admin/expenses`, `/api/admin/contracts/flag-expiring` →
  `hasRole("FINANCE")`; `AdminService`/controller resolvem o ator e auditam via fachada de auditoria.
- **Testes:** teste de segurança (403 sem FINANCE; 200/201 com FINANCE) sobe o caminho JWT real
  (DL-0081); os demais testes seguem com o ator de teste full-access.
- **Modulith:** sem novo módulo; o gate é infra (segurança); a auditoria reusa a fachada do Platform.

## Como reverter

Trocar o papel é editar os matchers em `SecurityConfig.configure` (uma linha por endpoint) e o teste
de segurança. Reversão **barata**. Se o dono criar um papel administrativo dedicado, acrescenta-se ao
catálogo do DL-0082 e troca-se o `hasRole`. A auditoria via `system_audit` independe do papel
escolhido.
