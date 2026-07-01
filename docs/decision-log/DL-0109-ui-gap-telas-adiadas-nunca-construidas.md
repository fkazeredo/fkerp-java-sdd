# DL-0109 — Gap de UI: telas de módulo adiadas para a "Fase 10" nunca foram construídas

- **Fase:** pós-15 (descoberto ao testar o app); remediação planejada na **Fase 16** (Telas de operação)
- **Spec(s):** **SPEC-0026** (Fase 10 — escopo estreito, causa); **SPEC-0029** (a autorar — remediação); `docs/ROADMAP.md` (Fase 16); `OVERVIEW.md` (Partes 5/6/8 — o produto pensado para operadores)
- **Data:** 2026-06-30
- **Status:** REGISTRADO
- **Confiança:** Alta (diagnóstico factual, verificado no código)
- **Reversibilidade:** Barata (a correção é UI aditiva sobre APIs que já existem)

## Lacuna (o que aconteceu)

Ao testar o ERP como usuário, o dono notou que **falta coisa**. Verificado no código: o **backend está
quase completo** — 22 módulos Spring Modulith, **37 controllers REST**, 476 testes — mas o **frontend
só tem tela para ~5 módulos**: Accounts, Exchange (parcial — só a taxa congelada), Quoting, Booking,
Reconciliation, mais dashboard/login/health. A navegação (`frontend/src/app/core/layout/nav.ts`) tem
**7 itens**. **~17 módulos existem só como API**, sem tela alguma: Finance, Billing, Payout, Compliance,
AfterSales, Sourcing, CommercialPolicy, **Intelligence/DSS**, Marketing, Portfolio, Assets, People/RH,
Platform (certificado/jobs/auditoria), Admin, políticas de cancelamento, Commissioning, e as visões
completas de Exchange (market-rate/posições/exposição). O operador enxerga ~1/4 do ERP.

**Causa-raiz:**
1. **Roadmap backend-first, por decisão.** Cada fase de negócio (2–8l) entregou módulo + API e **adiou a
   tela**, com a nota "Telas: backend-first (UI follow-up)" / "Tela Angular → follow-up" apontando para
   a **Fase 10 (UX)** — ver `docs/ROADMAP-STATUS.md` linhas 60, 126, 133, 148, 163, 178, 194, 209.
2. **A Fase 10 se escopou estreita.** A SPEC-0026 (autorada pelo próprio builder no modo autônomo) leu
   "repaginar TODAS as telas" como *modernizar as 5 telas já existentes* + shell/tema/command-palette/
   login/dashboard — **não** "criar uma tela para cada módulo adiado". A dívida de UI acumulada de ~17
   módulos **nunca foi paga**.
3. **O supervisor (loop) não inseriu uma fase para quitar a dívida.** O loop executou fielmente cada
   spec e a reverificação checou **gates verdes**, não "o operador consegue usar tudo". Os "→ Fase 10"
   caíram no vão entre o ponteiro e o escopo real da Fase 10.

**Gaps secundários (intencionais/POC, não o motivo principal):** DSS/Intelligence implementou 2 dos
~30 insights do catálogo (OVERVIEW Parte 8); Assets/Admin/Portfolio são "registro enxuto + seam";
integrações externas (pagamento, NFS-e, newsletter, ponto) são **mocks** por ADR; o banco sobe vazio
(sem seed), reforçando a impressão de vazio.

## Decisão

1. **Registrar isto** (este DL) como retrospectiva revisável, a pedido do dono.
2. **Quitar a dívida na Fase 16 "Telas de operação"** — construir as telas faltantes reusando o padrão
   de frontend já estabelecido (service+`API_BASE_URL`+`PageResponse`; `<app-screen-state>` para
   loading/empty/erro/permissão; rota lazy sob o `Shell` com guards; `NavItem.roles?` para nav por
   papel; i18n bilíngue; Vitest + Playwright). Autora **SPEC-0029** primeiro. 4 fatias, releases MINOR
   (nova capacidade de usuário, ADR 0015), **frontend-only** (APIs já existem):
   - **16a** Financeiro & Compliance (Finance, Billing, Payout, Compliance) — `0.24.0`.
   - **16b** Ciclo comercial (AfterSales, Sourcing, Exchange-completo, Cancelamento) — `0.25.0`.
   - **16c** Inteligência & Crescimento (Intelligence/DSS, CommercialPolicy, Marketing, Portfolio) — `0.26.0`.
   - **16d** Back-office & TI (People/RH, Ponto, Assets, Admin, Platform/TI, Identity/acesso) — `0.27.0`.
3. **Correção de processo (go-forward):** para fases que entregam capacidade de negócio, a Definition of
   Done passa a exigir explicitamente a **tela de operação** (ou registro consciente do adiamento com
   fase-alvo real), para o gap não se repetir.

## Justificativa

- É um gap **real e de alto impacto** para quem usa: o valor do ERP só aparece na tela. O backend estar
  completo não basta — o OVERVIEW descreve o sistema para operadores ("texto e **telas** em português").
- A correção é **barata e de baixo risco**: as APIs, o modelo de papéis e o padrão de tela já existem;
  são telas aditivas, sem tocar contrato/schema.
- Registrar a causa-raiz (não só corrigir) evita a repetição — o modo autônomo precisa de um gate de
  "usável", não só de "spec cumprido + build verde".

## Alternativas descartadas

- **Não registrar, só corrigir.** Descartada: o dono pediu o registro para revisão; e a causa-raiz
  (processo autônomo sem gate de UI) some se não for anotada.
- **Construir tudo numa fatia só.** Descartada: 18 telas num release é grande e arriscado; fatiar por
  domínio/papel entrega valor incremental com gates verdes a cada passo.
- **Refazer a SPEC-0026.** Descartada: a Fase 10 (shell/tema/repaginação) está correta no que fez; a
  lacuna é *cobertura*, endereçada por uma SPEC-0029 aditiva, não por reabrir a 0026.

## Impacto

- **Specs:** nova **SPEC-0029** (telas de operação). **ADR:** nenhum novo (usa o padrão da SPEC-0026).
- **Arquivos (por fatia):** `frontend/src/app/features/<ctx>/*` (models/service/page/spec),
  `app.routes.ts`, `core/layout/nav.ts`, `core/i18n/translations.ts`, `frontend/e2e/*`,
  `docs/MANUAL.md`+`MANUAL.en-US.md`. **Backend:** idealmente nenhum (as APIs existem); só endpoint de
  leitura novo se uma tela exigir. **Migração:** nenhuma. **Contrato:** nenhum (UI sobre APIs existentes).
- **Versão:** 4 releases MINOR (`0.24.0`…`0.27.0`), ADR 0015.
- **ROADMAP:** adiciona a Fase 16 (16a–16d) ao `ROADMAP.md` e ao `ROADMAP-STATUS.md`.

## Como reverter

Trivial: a UI é aditiva. Reverter é remover as features/rotas/itens de nav novos — nenhum dado,
contrato ou schema é tocado. O gap em si (não ter as telas) é o estado anterior.
