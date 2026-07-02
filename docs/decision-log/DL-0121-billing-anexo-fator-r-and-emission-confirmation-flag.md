# DL-0121 — Billing: enquadramento (Anexo III×V / Fator R) documentado como incógnita + flag de confirmação do contador para emissão real (refina a DL-0044)

- **Fase:** 19b (Refactoring de maturidade — revisão do decision-log)
- **Spec(s):** SPEC-0016 (Q7; BR nova de confirmação)
- **ADR relacionado:** —
- **Data:** 2026-07-02
- **Status:** DECIDIDO (o enquadramento real segue Open Question do contador)
- **Confiança:** Alta (sobre o processo; o enquadramento em si segue Baixa — DL-0044)
- **Reversibilidade:** Barata

## Lacuna

A DL-0044 (Confiança **Baixa** / Reversibilidade **Cara**) assumiu **Simples Nacional** como
regime default. A revisão dirigida da Fase 19 pesquisou o enquadramento e encontrou uma nuance
que **agrava o risco da assunção**: agência de viagens (CNAE 7911-2/00) tributa pelo **Anexo III
sem Fator R**, mas receita de **representação comercial/intermediação** — que é o que uma GSA
fatura — cai no **Anexo V com Fator R** (folha ≥ 28% da receita ⇒ volta ao Anexo III). O
enquadramento muda a alíquota efetiva do DAS e **só o contador fecha** (fontes: Contabilizei,
e-Auditoria, ContaJá — pesquisa 19b). Emitir NFS-e real sob enquadramento errado tem custo fiscal
externo e irreversível (nota emitida é imutável — cancela e reemite).

## Decisão

1. **Nenhuma tabela de anexo/Fator R é implementada agora** (Regra Zero: o ISS da NF — que é o
   que o Billing calcula — não depende do anexo; o anexo afeta o DAS da própria Acme, fora do
   escopo do Billing). A nuance é **registrada na SPEC-0016** como parte da Q7 para a conversa
   com o contador, com as duas hipóteses (7911/Anexo III × representação/Anexo V+Fator R).
2. **Flag de confirmação do contador:** nova propriedade **`billing.tax.regime-confirmed`**
   (default **`false`**). Enquanto `false`, o sistema considera o regime **não confirmado**.
   O **enforcement** é responsabilidade do validador de prontidão de produção da fatia **19c**
   (fail-fast: perfil `prod` não sobe apto a emitir NFS-e real sem `regime-confirmed=true`);
   em dev/test nada muda (o gateway é mock rastreável — DL-0046).
3. A `TaxRegimeStrategy` (porta trocável da DL-0044) **permanece o seam** — quando o contador
   confirmar, troca-se config/estratégia sem refator (inalterado).

## Justificativa

- O risco real da DL-0044 não é o cálculo do ISS (parametrizado); é **emitir nota real** sob
  premissa fiscal não confirmada. A flag transforma a incógnita num **gate operacional
  explícito** em vez de uma nota de rodapé.
- Implementar Anexo III/V + Fator R agora seria inventar regra tributária sem o contador
  (invariante 3) e fora do escopo do Billing (o DAS não é calculado pelo ERP).

## Alternativas descartadas

- **Implementar o cálculo dos anexos/Fator R:** especulativo, fora do escopo (DAS ≠ ISS da NF).
- **Bloquear a emissão já em dev (flag dura):** quebraria a suíte e o fluxo de demonstração sem
  ganho — o gateway atual é mock; o risco só existe com gateway real (gate na 19c/19l).

## Impacto

- **Specs:** SPEC-0016 — Q7 ganha a nuance do enquadramento + BR de confirmação
  (`regime-confirmed`).
- **Config:** `billing.tax.regime-confirmed` (default `false`), documentada no `application.yml`.
- **Código:** nenhum comportamento muda nesta fatia; o enforcement chega com o
  `ProdReadinessValidator` (19c).
- **DL-0044:** permanece no log com nota de revisão apontando para esta.

## Como reverter

Barata: remover a propriedade/gate. A decisão de fundo (regime real) continua sendo do contador
— esta DL só torna o risco explícito e bloqueante em produção.
