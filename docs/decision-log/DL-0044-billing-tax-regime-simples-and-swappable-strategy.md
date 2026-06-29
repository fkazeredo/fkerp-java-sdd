# DL-0044 — Billing: regime tributário Simples Nacional (default) + estratégia trocável de cálculo (Q7)

- **Fase:** 8c (Billing — SPEC-0016)
- **Spec(s):** SPEC-0016 (Q7 "regime tributário e quem emite"; BR2 "ISS + retenções conforme tomador/regime")
- **ADR relacionado:** 0011, 0012 ; `architecture/security.md` (dado tributário/pessoal); OVERVIEW 3.2/7.7
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** **Baixa**
- **Reversibilidade:** **Cara**

## Lacuna

A **Q7** da SPEC-0016 está em aberto: **qual o regime tributário** da Acme (Simples Nacional /
Lucro Presumido / Lucro Real) e **quem emite** a NF de comissão. O regime define a **alíquota
efetiva de ISS**, a **base** e **quais retenções** (IRRF, PIS/COFINS/CSLL, ISS retido) se aplicam.
Sem essa resposta, BR2 não tem números concretos. É uma **decisão de negócio que só o
contador/diretor da Acme pode dar** — não há fonte técnica que a feche.

## Decisão

1. **Regime default = Simples Nacional**, conforme a recomendação do arquiteto na tabela
   "Recomendações para as Open Questions" do `docs/ROADMAP.md` (Q7): *"Assumir Simples Nacional
   inicialmente; cálculo de ISS/retenções parametrizado por regime (Simples/Presumido/Real) e
   município, atrás de estratégia trocável; emitente = a própria Acme."*

2. **Emitente = a própria Acme** (a GSA emite a NFS-e de serviço sobre a sua comissão; e-CNPJ da
   Acme — custódia futura do Platform, SPEC-0023, hoje atrás de uma porta `CertificateSigner` com
   mock — ver DL-0046).

3. **Cálculo parametrizado por regime + município, atrás de uma estratégia trocável**
   (`TaxRegimeStrategy`, um **port de domínio**). O serviço de Billing nunca embute as regras de um
   regime: ele resolve a estratégia do regime corrente (config `billing.tax.regime`, default
   `SIMPLES_NACIONAL`) e a aplica sobre a **base = comissão** (DL-0045). Trocar o regime real do
   contador = trocar a config / plugar outra `TaxRegimeStrategy`, **sem refatorar** o agregado nem o
   fluxo de emissão.

4. **Parâmetros de cálculo do Simples (v1, default, confirmáveis):**
   - **ISS** = `issRate(município, serviceCode) × base_comissão`, escala 2 HALF_UP (kernel `Money`).
     `issRate` é um **parâmetro por município** (tabela de alíquotas, default **5%** = `0.05`, o teto
     do ISS na LC 116/2003; faixa legal 2%–5%). O município `3550308` (São Paulo capital) entra no
     seed com **2%** (`0.02`) como exemplo de alíquota local distinta do teto.
   - **Retenções no Simples (default v1) = nenhuma.** Optantes pelo Simples Nacional, em regra, **não
     sofrem as retenções federais de IRRF/PIS/COFINS/CSLL** na fonte sobre a comissão (IN RFB
     1.234/2012 art. 3º §2º I, e a sistemática do recolhimento unificado do Simples). O **ISS retido**
     pelo tomador depende da legislação municipal e do tomador — modelado como **capacidade existente**
     (lista de retenções no agregado), mas **vazia por default** no Simples. Quando o contador
     confirmar Presumido/Real, a estratégia daquele regime preenche as retenções federais.

5. **A estratégia é trocável e testada como tal:** além do default Simples (números exatos), um
   teste prova que **plugar outra `TaxRegimeStrategy`** (ex.: um stub "Presumido" com retenção
   IRRF 1,5%) muda o resultado **sem tocar** no agregado/serviço — a costura de troca é exercida.

## Justificativa

- **Recomendação explícita do ROADMAP (Q7)** — a 1ª fonte na ordem do RUN-PHASE (modo autônomo).
- **Simples é o mais comum em PME** e o que **menos retém na fonte**, então é o default mais
  defensável enquanto o regime real não é confirmado.
- **Estratégia trocável** honra a Regra Zero: não construímos os três regimes especulativamente;
  construímos **o default real (Simples)** + **a costura** que permite plugar os outros quando
  houver a regra concreta. O port + um stub no teste provam que a troca não custa refator.
- **Base legal citada (não inventada):** LC 116/2003 (ISS, faixa 2%–5%); LC 123/2006 (Simples
  Nacional, recolhimento unificado); IN RFB 1.234/2012 (dispensa de retenção federal a optantes do
  Simples). Os **números concretos do município e do regime real continuam dependendo do contador**.

## Alternativas descartadas

- **Fixar Lucro Presumido/Real com retenções federais agora.** Descartada: não é o mais comum em
  PME e **inventaria retenções** que a Acme talvez não sofra — pior default. Fica plugável.
- **Hardcode da alíquota e das retenções no serviço de Billing.** Descartada: viola a parametrização
  pedida pela Q7; trocar o regime exigiria refator. A estratégia + tabela por município evita isso.
- **Modelar os três regimes completos já (Simples + Presumido + Real, todas as retenções).**
  Descartada por Regra Zero: especulação sem a regra concreta do contador. Entregamos Simples real +
  o seam; os outros entram quando virarem dado.

## Impacto

- **Specs:** SPEC-0016 — Q7 movida para **ASSUMIDO (ver DL-0044)** em Business Rules.
- **Arquivos:** novo port `TaxRegimeStrategy` + value objects (`TaxAssessment`, `Withholding`,
  `TaxRegime`) e o adaptador default `SimplesNacionalTaxStrategy` no módulo `domain.billing`; tabela
  de alíquotas de ISS por município (seed) — migração **V20**; config `billing.tax.*`.
- **Contratos:** o `issue` retorna o ISS calculado e a lista de retenções (vazia no Simples).
- **Migração:** V20 cria a tabela `municipal_iss_rates` (seed default 5%, São Paulo 2%).

## Como reverter

Reversão **cara** porque move a tese tributária: trocar o regime default exige (a) plugar/escrever a
`TaxRegimeStrategy` do regime real, (b) revisar o seed de alíquotas e a config, (c) **reemitir** (não
retroagir) notas já emitidas sob o regime anterior — uma NFS-e emitida é imutável; corrige-se por
cancelamento + reemissão (BR6). O **raio é o módulo `billing`** (o agregado e o fluxo não mudam, só a
estratégia plugada), mas o **impacto fiscal de notas já emitidas é externo e caro** — por isso
Reversibilidade=Cara e Confiança=Baixa. **Levar Q7 ao contador antes de emitir em produção.**
