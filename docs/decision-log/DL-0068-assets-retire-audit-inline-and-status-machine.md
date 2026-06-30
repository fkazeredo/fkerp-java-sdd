# DL-0068 — Baixa (RETIRED) auditada inline no agregado; máquina de estado ACTIVE→RETIRED simples

- **Fase:** 8h (Assets)
- **Spec(s):** SPEC-0021 (BR4 baixa auditada; Validation Rules estados ACTIVE/RETIRED)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A BR4 exige que a baixa (RETIRED) **seja auditada (quem, quando, motivo)**, mas a spec não diz
**onde/como** guardar a auditoria (colunas no próprio ativo × tabela de histórico separada), nem se a
transição RETIRED é **terminal** (reativar é permitido?) ou se baixar duas vezes é erro.

## Decisão

1. **Auditoria inline no agregado `Asset`**, em três colunas: `retired_at` (timestamptz),
   `retired_by` (varchar) e `retirement_reason` (varchar). Não há tabela de histórico de baixas
   separada — o ativo só tem **uma** baixa (estado terminal), então uma linha basta.
2. **Máquina de estado mínima:** `ACTIVE → RETIRED`. `RETIRED` é **terminal**: não há reativação no
   v1. Baixar um ativo já `RETIRED` lança `AssetAlreadyRetiredException` →
   `assets.asset.already-retired` (409). A transição é validada **no domínio** (`Asset.retire`),
   não no controller.
3. Os campos de auditoria genéricos `created_by`/`updated_by`/`created_at`/`updated_at` seguem o
   padrão dos demais agregados (Portfolio/Billing); a baixa adiciona os três campos específicos da
   BR4 e atualiza `updated_*`.

## Justificativa

- **SPEC-0021 BR4 + Validation Rules:** pede auditoria (quem/quando/motivo) e os estados
  ACTIVE/RETIRED — três colunas no agregado cobrem exatamente isso; não há requisito de múltiplas
  baixas que justifique uma tabela de histórico (Regra Zero — não criar tabela especulativa).
- **`backend.md` (state machines):** "Simple enums for simple statuses; explicit state machines only
  when workflow complexity justifies; invalid transitions throw specific business exceptions;
  important transitions audited." A baixa é uma transição importante e audita; ACTIVE/RETIRED é
  simples demais para uma máquina explícita — basta o guard no método de domínio.
- **Padrão do projeto:** Portfolio (`RepresentedBrand.deactivate`) e Booking modelam transições
  auditadas inline no agregado; reusar o padrão evita divergência.

## Alternativas descartadas

- **Tabela `asset_retirements` (histórico):** sem requisito de baixas múltiplas/reversão, seria
  estrutura ociosa (Regra Zero). Se um dia houver reativação + nova baixa, promove-se a histórico.
- **Baixa idempotente (re-baixar é no-op):** a spec trata a baixa como fato auditável único; baixar de
  novo com outro motivo seria ambíguo — preferimos rejeitar (409) com erro de negócio traduzido,
  preservando o primeiro registro de auditoria.
- **Permitir reativar (RETIRED→ACTIVE):** não há regra de negócio para reativação de patrimônio
  baixado; seria especulativo.

## Impacto

- **Specs:** SPEC-0021 — BR4 em *Business Rules* ("ASSUMIDO (ver DL-0068)").
- **Arquivos:** `Asset.retire(reason, now, actor)`; `AssetStatus {ACTIVE, RETIRED}`;
  `AssetAlreadyRetiredException`.
- **Migração:** colunas `retired_at`, `retired_by`, `retirement_reason` em `V26` (nulas até a baixa).
- **Contratos:** `POST /api/assets/{id}/retire {reason}` → 200; 409 `assets.asset.already-retired`
  ao re-baixar; i18n pt-BR + fallback.

## Como reverter

Para suportar reativação/baixas múltiplas: extrair a auditoria de baixa para `asset_retirements`
(uma linha por baixa) e relaxar o guard terminal. Refactoring **barato** (aditivo: migração nova +
ajuste do método de domínio), sem quebrar contratos existentes.
