# Caderno de testes — Slice 12-4 (Jornadas E2E + caminhos tristes)

## Escopo

- **Spec:** SPEC-0028 (AC3, AC4, AC5, AC6, AC7, BR4).
- **Entrega:** specs Playwright das jornadas críticas e dos caminhos tristes, contra o stack isolado
  (4201), headless, chromium. Seletores derivados das telas **reais** (Fase 10) e dos rótulos **reais**
  do i18n (`translations.ts`) — nada inventado (Regra Zero).

## Casos de teste por tipo (E2E)

| Caso (arquivo) | O que verifica | AC / regra |
|---|---|---|
| login feliz (`login.spec.ts`) | `director`/`dev12345` → dashboard "Painel" + `shell-user` | AC3 |
| login inválido (`login.spec.ts`) | senha errada → erro genérico (`login-error`), permanece em `/login` | AC4/BR4 |
| guard sem sessão (`auth-guard.spec.ts`) | `/accounts` sem login → redireciona `/login?returnUrl=%2Faccounts` | AC5 |
| fluxo central + vazio (`accounts.spec.ts`) | navega a Contas pelo shell; **empty state** real; cria conta (CNPJ válido) → linha aparece | AC6/BR4 |
| não-salvos (`unsaved-changes.spec.ts`) | form sujo + sair → `confirm()` "alterações não salvas"; dismiss mantém; accept sai | AC7 |
| permissão 403 (`permission.spec.ts`) | `viewer` emite diretiva diretor-only → **403** | BR4 / SPEC-0024 DL-0082 |
| 401 (`permission.spec.ts`) | `GET /api/accounts` sem token → **401** | BR4 |
| diretor passa o gate (`permission.spec.ts`) | `director` na mesma diretiva → **não** 401/403 (gate é por papel) | BR4 |

## Resultado

- `npx playwright test` (headless, chromium, contra a 4201) → **11 passed (7.3s)**, `PW_EXIT=0`.
  (8 casos de jornada/sad path desta fatia + 3 smoke da 12-3, no mesmo run.)
- Sad paths cobertos: **401** (sem sessão/sem token), **403** (papel insuficiente), **empty state**
  (lista de contas vazia no DB efêmero), **proteção de não-salvos** (form sujo), **credencial inválida**
  (erro genérico). Caminho feliz: login → dashboard → navegação → criação de conta.

## Cobertura (o que NÃO está coberto e por quê)

- **E2E só para fluxos críticos** (`testing.md`): cobrimos login/guard/fluxo central/borda/permissão,
  não toda tela. Exchange/Quoting/Booking/Reconciliation têm cobertura unitária (frontend) e de
  integração (backend) — não se duplica no E2E (DL-0102).
- **403 na UI:** o estado de permissão (`state-permission`) existe no `ScreenState`, mas nenhuma tela
  atual chama endpoint papel-gated na listagem; o 403 é provado no nível de API pelo proxy real
  (mesma autorização do backend) — caminho triste honesto sem inventar tela (Regra Zero).
- **Multi-browser/mobile/visual:** Out of Scope (SPEC-0028).

## Como reproduzir

```bash
cd frontend && npm ci && npx playwright install chromium
npm run e2e:up
npx playwright test            # 11 passed; headless chromium na 4201
npm run e2e:down
```
