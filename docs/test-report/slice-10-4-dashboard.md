# Caderno de testes — Slice 10-4: Dashboard com KPIs

## Escopo
SPEC-0026 — AC10 (dashboard com KPIs), BR10. DL-0094 (KPIs no cliente, sem backend novo).

## Mudanças
- `DashboardService` compõe 4 KPIs **no cliente** a partir dos endpoints de lista existentes:
  Contas (`GET /api/accounts`), Reservas (`GET /api/bookings`), Conciliação
  (`GET /api/reconciliation`), Câmbio (`GET /api/exchange/pinned-rates/current`).
- `BookingService.list()` adicionado (o controller já expunha `GET /api/bookings`).
- `DashboardPage`: 4 cartões, cada um com estado próprio (loading/empty/error/permissão via
  `ScreenState`); cada cartão linka para a tela do domínio.
- Rota raiz protegida `''` → `dashboard` (era `accounts`).

## Casos de teste (component/unit — vitest headless)

| Caso | Verifica | AC / BR |
|---|---|---|
| DashboardService: KPI de Contas (total+ativas) | agregação no cliente | BR10 / DL-0094 |
| DashboardService: KPI de Reservas (pending/confirmed) | agregação | BR10 |
| DashboardService: soma do spread esperado (Conciliação) | agregação | BR10 |
| DashboardService: taxa vigente (Câmbio) | mapeia `current` | BR10 |
| DashboardPage: carrega todos os KPIs | 4 cartões em success | AC10 |
| DashboardPage: erro independente por cartão | um falha, os outros carregam | AC10 |
| DashboardPage: 403 mantém access.denied (permissão) | estado de permissão por cartão | AC10 |

## Resultado
- `npm run lint` → **All files pass linting**.
- `CI=true npx ng test --no-watch` → **17 arquivos / 57 testes — todos verdes** (era 15/50).
- `npm run build` → **sucesso**; dashboard é chunk lazy (6.18 kB); inicial 517.29 kB / 118.83 kB
  transfer (dentro do budget).

## Cobertura
- Coberto: composição client-side dos 4 KPIs e os estados por cartão (success/error/permissão).
- Não coberto: e2e da navegação cartão→tela (Fase 12). Sem backend novo ⇒ nada a cobrir no back.

## Como reproduzir
```bash
cd frontend && npm ci
npm run lint
CI=true npx ng test --no-watch
npm run build
```
