# DL-0029 — Tipo de REP (Q6): mirar REP-P (software/nuvem) e modelar a captura do AFD/AEJ como upload da exportação oficial

- **Fase:** 6 (Crawler de ponto)
- **Spec(s):** SPEC-0012 (Open Question **Q6 — tipo de REP (C/A/P)**; BR4 ingestão do AFD/AEJ assinado pela
  exportação OFICIAL; redesenho 7.8; Portaria MTP 671/2021)
- **ADR relacionado:** 0010 (porta por adaptador técnico), 0002 (single-instance)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** **Baixa**
- **Reversibilidade:** **Cara**

## Lacuna

Qual **tipo de REP** o cliente da Acme Travel usa — REP-C (equipamento físico convencional, AFD extraído
por **USB**), REP-A (alternativo, exige acordo coletivo) ou REP-P (via programa/software, certificado INPI,
pode rodar em **nuvem**, exporta AFD/AEJ oficialmente)? O tipo muda **como** o AFD com validade legal é
capturado e o que o crawler consegue de fato raspar (SPEC-0012 Q6). É uma **decisão de negócio** que só o
cliente fecha; nenhuma fase anterior a respondeu.

## Decisão

1. **Mirar REP-P (software/nuvem)** como alvo de partida da exportação oficial do AFD/AEJ assinado.
2. **Modelar a captura do AFD/AEJ como um upload da exportação OFICIAL** (`POST /api/integration/point/afd`,
   multipart com o `.p7s` + metadados), guardado no cofre da Compliance com `signedFormat=CAdES_P7S` e
   retenção de 5 anos. **Este mesmo upload serve REP-C** (em que o operador extrai o AFD por USB e faz o
   upload do arquivo): o **mecanismo de ingestão é o mesmo** (upload da exportação oficial), independente do
   tipo de REP. O que muda por tipo é apenas **a origem física** do arquivo (USB × portal/nuvem), que fica
   **fora** do código (é um passo operacional do humano/Platform).
3. O **crawler** raspa o **portal do fornecedor de ponto** apenas para o **dado operacional**
   (espelho/marcações) → snapshot para o `People`; **nunca** trata o que raspou como artefato legal
   (`operationalOnly=true`, BR3). O artefato legal **só** entra pela ingestão oficial (BR4).

## Justificativa

- **Recomendação do ROADMAP** (tabela "Recomendações para as Open Questions", linha Q6): "Mirar **REP-P
  (software/nuvem)** para a exportação oficial do AFD/AEJ; ingestão da SPEC-0012 como 'upload da exportação
  oficial' (serve também para REP-C via USB)". Adotada na ordem 1 do `RUN-PHASE` (modo autônomo).
- **REP-P (Portaria MTP 671/2021)** roda em nuvem, é certificado INPI e exporta AFD/AEJ oficialmente — melhor
  fit para um ERP em nuvem que o fluxo USB do REP-C.
- **Modelar como upload desacopla o código do tipo de REP:** a separação operacional × legal do redesenho 7.8
  é independente do tipo; só o **mecanismo físico** de obter o `.p7s` depende do Q6, e esse mecanismo é um
  passo humano/operacional, não código. Assim o ERP atende REP-P **e** REP-C sem refatorar.

## Alternativas descartadas

- **Assumir REP-C e modelar leitura por USB no código.** Descartada: amarraria o ERP a um device USB físico
  (impróprio para SaaS em nuvem) e ainda assim precisaria de um upload na ponta; o upload já cobre os dois.
- **Tentar extrair o AFD assinado por raspagem do portal.** Descartada e proibida por BR4/redesenho 7.8: **só
  o REP gera o AFD com fé legal**; raspar entrega espelho/jornada (operacional), não o documento legal. Tratar
  o raspado como legal seria erro de compliance.
- **Esperar a confirmação do cliente antes de codar.** Descartada pelo modo autônomo do `RUN-PHASE`: registra-se
  a suposição com **Confiança=Baixa / Reversibilidade=Cara** e segue-se com a opção mais defensável.

## Impacto

- **Specs:** SPEC-0012 — item Q6 movido de *Open Questions* para *Business Rules* como "ASSUMIDO (ver DL-0029)".
- **Arquivos:** endpoint `POST /api/integration/point/afd` (upload), verificação de assinatura/integridade do
  `.p7s`, ingestão como `Document` no `Compliance` (reuso de `ComplianceService.upload` + `RetentionPolicy`,
  tipos `TIME_RECORD_AFD`/`PROCESSED_JOURNAL_AEJ` já existentes na SPEC-0008).
- **Migrações:** nenhuma para o AFD (é `Document` no cofre existente).
- **Contratos:** novo endpoint de ingestão e evento `LegalTimeRecordArchived`.

## Como reverter

Reversão **cara**: se o cliente usar um REP-C/REP-A cuja exportação oficial **não** for um `.p7s` no formato
assumido, muda-se o **verificador de assinatura/parser** e os metadados aceitos no upload; se exigir leitura
USB automatizada, entra um adaptador novo de origem física. O **modelo** (operacional × legal, upload como
porta de entrada do legal) **permanece**; o que muda é o adaptador de captura e a validação do formato — daí
Confiança=Baixa (o tipo real é incógnita de negócio) e Reversibilidade=Cara (mexe em formato legal e
verificação de integridade).
