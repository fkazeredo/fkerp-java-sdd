# DL-0061 — Portfolio: vender marca sem contrato vigente **alerta** (v1), não bloqueia

- **Fase:** 8g (Portfolio — SPEC-0020)
- **Spec(s):** SPEC-0020 (BR2 "Vender marca sem contrato vigente é uma **exceção sinalizável** —
  alerta, não bloqueia v1"; Open Question "Vender marca **sem contrato vigente**: alertar apenas
  (v1) ou **bloquear**?").
- **ADR relacionado:** OVERVIEW Parte 4.2 ("fronteira do dinheiro governada, não travada — o sistema
  calcula e sugere; o humano pode divergir, com rastro"); core-principles.md (governar com rastro em
  vez de travar quando o negócio precisa de flexibilidade).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0020 deixa em aberto se vender uma marca **sem contrato de representação vigente** deve
**apenas alertar** (sinalizar como exceção de governança) ou **bloquear** a operação. Como o
Portfolio é referência para Quoting/Commissioning, a escolha muda se há ou não um veto comercial.

## Decisão

**Alertar, não bloquear (v1).** O Portfolio:

1. Expõe uma consulta `contractCoverage(brandRef, on)` que responde se a marca tem **algum** contrato
   vigente na data (read-model). Quem compõe a venda (Quoting/Booking) **pode** consultar e sinalizar,
   mas o Portfolio **não veta** a venda.
2. **Não cria** um `DomainException` de bloqueio para "vender sem contrato". A exceção é
   **sinalizável** (um alerta/governança), coerente com BR2.
3. A **expiração** de contrato publica `RepresentationExpiring` (DL-0063) como alerta de governança —
   o mesmo espírito: avisa, não trava.

## Justificativa

- A própria **BR2 já decide "alerta — não bloqueia v1"** (o item de Business Rules é explícito); a
  Open Question é só a confirmação do dono. Adotamos a regra da spec.
- OVERVIEW Parte 4.2: a fronteira do dinheiro é **governada, não travada** — o sistema sinaliza a
  divergência com rastro, o humano decide. Bloquear a venda por falta de contrato seria travar uma
  operação que o negócio híbrido da Acme às vezes precisa fazer (representação informal/transição de
  contrato), contrariando a tese.
- Reversível barato: virar bloqueio é adicionar uma checagem/exceção em quem compõe a venda; nada no
  schema do Portfolio muda.

## Alternativas descartadas

- **Bloquear a venda sem contrato vigente (lançar exceção de domínio).** Descartada no v1: trava o
  fluxo híbrido, contraria "governar com rastro, não travar", e antecipa uma decisão de negócio ainda
  não confirmada. Fica como evolução trivial se o dono pedir o veto.
- **Portfolio chamar Quoting/Booking para impor o veto.** Descartada: inverteria a direção de
  dependência (Portfolio é **referência**, consumido por Quoting/Commissioning/DSS — não comanda a
  venda) e criaria ciclo.

## Impacto

- **Specs:** SPEC-0020 — a Open Question "alertar ou bloquear" fecha como "ASSUMIDO (ver DL-0061):
  alertar (v1)"; BR2 permanece como está (já dizia alerta).
- **Arquivos:** `PortfolioService.contractCoverage(...)` (read-model); nenhuma exceção de bloqueio.
- **Contratos:** consulta de cobertura exposta como leitura; sem novo erro 4xx de "sem contrato".
- **Modulith:** sem nova dependência (Portfolio continua referência, não comanda venda).

## Como reverter

Barata: se o dono quiser bloquear, adiciona-se a checagem em quem compõe a venda (Quoting/Booking)
consumindo a fachada `contractCoverage` e lançando a exceção lá — o Portfolio só ganha (no máximo) um
método de conveniência. Sem migração nem mudança de dado.
