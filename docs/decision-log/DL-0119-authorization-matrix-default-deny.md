# DL-0119 — Autorização: matriz única rota×papel com default-deny para escritas

- **Fase:** 19a (Refactoring de maturidade — segurança)
- **Spec(s):** SPEC-0024 (BR18; estende o catálogo de ações sensíveis da BR10/DL-0082)
- **ADR relacionado:** 0018; `architecture/security.md` ("o backend é a autoridade final")
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O enforcement da Fase 8k (DL-0082) gate-ou por papel apenas ~8 rotas; o fallback do
`SecurityConfig` era `/api/** → authenticated()`. Resultado auditado na análise da Fase 19:
**qualquer usuário autenticado — inclusive `ROLE_VIEWER` — podia congelar a taxa de câmbio,
criar e executar payouts, lançar no razão, dirigir o ciclo do booking, cadastrar funcionários,
subir holerite e expurgar documentos do cofre.** Pior: o blanket `permitAll` de
`/api/integration/**` (pensado para webhooks HMAC) deixava o upload de AFD e o gatilho de crawl
**sem credencial nenhuma**. Leituras de dados pessoais (People/Ponto) estavam abertas a
qualquer autenticado (LGPD).

## Decisão

1. **Registro único ordenado** `ApiAuthorizationMatrix` (`infra.security`): cada regra =
   `(método, pattern, acesso)` onde acesso ∈ {PERMIT (M2M/HMAC), AUTHENTICATED, ROLES}.
   O `SecurityConfig.configure()` itera a matriz (primeiro match vence) — produção e teste
   rodam as mesmas regras (DL-0081 preservada).
2. **Default-deny:** após a matriz, `POST/PUT/PATCH/DELETE /api/** → denyAll`. Endpoint novo de
   escrita só é alcançável se entrar na matriz.
3. **Completude como portão de build** (`ApiAuthorizationMatrixCompletenessTest`, padrão do
   `HttpErrorMapping`): (a) todo write endpoint do handler mapping casa com uma regra; (b) toda
   regra de escrita casa com ≥1 endpoint real (sem entrada obsoleta).
4. **Papéis por balcão** (alinhados à navegação que o operador já vê — `nav.ts`):
   FINANCE = finance/billing/payouts/admin/liquidação da conciliação/expurgo do cofre;
   OPERATIONS = accounts/sourcing/quotes/bookings/aftersales/market-rates/política de
   cancelamento/marketing/portfolio/decisão de insight (com DIRECTOR);
   IT = platform/assets/people/ponto (incl. AFD/crawl); DIRECTOR = taxa congelada, diretivas,
   apagamento LGPD; POLICY_ADMIN = cadastros. VIEWER não escreve.
5. **`permitAll` de integração estreitado** aos 2 endpoints M2M assinados por HMAC
   (`/api/integration/quotation-site/inbound`, `/api/webhooks/payouts/**`).
6. **Leituras sensíveis gated:** `GET /api/people/**` e `GET /api/integration/point/**` → IT
   (dado pessoal); `GET /api/compliance/documents/*/content` exclui VIEWER (pode conter
   holerite); `GET /api/platform/**` → IT/DIRECTOR.
7. **Frontend não muda de mecânica:** a navegação por papel já espelha os balcões; ações
   negadas caem nos estados de permissão existentes (403 auditado). Única correção: a jornada
   E2E de contas loga como `ops` (balcão certo).

## Justificativa

- `security.md`: "o backend é a autoridade final" — antes valia só para 8 rotas.
- Default-deny é o padrão de mercado (fail-safe defaults); a matriz + teste de completude
  transforma a regra em **fitness function**, não disciplina.
- Papéis por balcão seguem o que a UI já comunica desde a Fase 16 — nenhum conceito novo.
- Um registro único auditável evita o drift "novo controller esqueceu o gate" (a causa raiz
  do gap do 8k).

## Alternativas descartadas

- **`@PreAuthorize` por método:** espalha a política por 37 controllers e não tem teste de
  completude natural; a matriz central + fitness cobre o mesmo com auditabilidade melhor.
- **Papel novo "HR" para People:** a casa decidiu na Fase 16d operar RH sob IT ("sem papel
  HR"); criar papel agora seria regra inventada (invariante 3).
- **Gate por escopo OAuth2 (`SCOPE_*`):** os escopos já são expostos (DL-0104) mas o catálogo
  de enforcement é por papel (BR10); refinar para escopo é evolução, não requisito desta fatia.

## Impacto

- **Arquivos:** novo `infra/security/ApiAuthorizationMatrix.java`; `SecurityConfig.configure()`
  consome a matriz + default-deny; testes novos `ApiAuthorizationMatrixCompletenessTest` e
  `WriteAuthorizationIntegrationTest`; E2E `accounts.spec.ts` loga como `ops`.
- **Specs:** SPEC-0024 BR18.
- **Contratos:** nenhum shape muda; **status de autorização muda** para clientes sem o papel
  (antes 2xx, agora 403) — destacado no release `0.33.0`.
- **Migração:** nenhuma.

## Como reverter

Barata: remover/afrouxar regras da matriz (uma lista) e o fallback `denyAll`; o teste de
completude aponta exatamente o que ficou órfão. O risco real é de negócio (quem pode o quê) —
qualquer reatribuição de papel é uma linha na matriz + teste 403 correspondente.
