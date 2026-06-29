# DL-0058 — Marketing: exclusão LGPD apaga PII de marketing mas preserva a prova de revogação (tombstone) e a atribuição anonimizada

- **Fase:** 8f (Marketing — SPEC-0019)
- **Spec(s):** SPEC-0019 (BR6 "Pedido de exclusão/portabilidade do titular MUST ser atendível:
  consultar/remover dados de marketing do titular, **preservando o que outra base legal obrigue a
  manter**"; Acceptance "Pedido de exclusão do titular é atendido sem apagar o que a lei manda
  guardar"; Validation "LGPD: atender exclusão/portabilidade (BR6)").
- **ADR relacionado:** security.md (LGPD, minimização, trilha, mascarar PII em log), redesign Parte
  7.7 (retenção legal autoriza guardar sob "cumprimento de obrigação legal"), DL-0056 (consent log).
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Baixa
- **Reversibilidade:** Cara

## Lacuna

A BR6 manda **excluir** os dados de marketing do titular **mas preservar** o que outra base legal
obriga a guardar — sem dizer **o que** exatamente sobrevive ao expurgo nem **como** (apagar linha ×
anonimizar). É uma tensão jurídica (direito ao apagamento × dever de prova/retenção) que, no detalhe,
só o DPO/jurídico do dono fecha.

## Decisão (valor mais defensável; Confiança=Baixa)

1. **`POST /api/marketing/erasure` (corpo `{subject}`)** atende o pedido do titular: remove os
   **dados pessoais de marketing** do titular (conteúdo identificável associado a segmentos/campanhas;
   `source` livre que possa conter PII), e **encerra o consentimento** (não há mais base para envio).
2. **Preserva uma prova de revogação não-identificável (tombstone):** em vez de apagar todo o log de
   `consents`, a última decisão vira/permanece `REVOKED` e os campos de PII livre são **anonimizados**
   (`source` → `null`/`"ERASED"`), mantendo `purpose`, `legal_basis`, `status=REVOKED`, `created_at`.
   Isso preserva a **prova de que NÃO se pode mais enviar** (e de quando o titular saiu) — necessária
   para defender a conformidade e para a **supressão futura** (BR2), que é a própria garantia do
   titular. O `subject_id` é substituído por um **pseudônimo irreversível** (hash) quando o titular
   exige apagamento do identificador.
3. **Atribuição preservada anonimizada:** linhas de `attributions` (campanha→reserva) **não** contêm
   PII do titular (guardam `campaign_code` + `booking_id`), então são **métricas/registro de negócio**
   e permanecem — a conversão histórica é fato comercial agregável, não dado pessoal de marketing.
4. **O expurgo NUNCA toca outras bases legais:** documentos fiscais no Compliance, lançamentos no
   Finance, reserva no Booking — fora do escopo do Marketing e protegidos por "cumprimento de
   obrigação legal" (redesign 7.7). O erasure do Marketing só mexe no **dado de marketing**.
5. **Métricas como log agregado:** `campaign_sends_total`/`consent_suppressed_total`/
   `campaign_conversions_total` são **contadores sem PII** (Micrometer) — não armazenam titular, logo
   não há o que apagar neles (a observabilidade já é "sem PII", security.md).

## Justificativa

- A LGPD (art. 18, direito à eliminação) **convive** com o art. 16 (conservação para cumprimento de
  obrigação legal/regulatória e para **exercício regular de direitos**). Manter um **tombstone
  anonimizado de revogação** é a forma reconhecida de honrar o apagamento **sem** perder a prova de
  que o titular optou por sair (sem ela, o sistema poderia "re-incluir" o titular num próximo
  disparo — o oposto do que ele pediu). Fontes: texto da Lei 13.709/2018 (arts. 16, 18); guias de
  boas práticas de *suppression list* (manter a supressão é a forma de respeitar a revogação).
- Anonimizar (em vez de apagar a linha) é o caminho que satisfaz **os dois deveres**; apagar tudo
  quebraria a supressão futura (BR2) e a auditabilidade.
- Confiança **Baixa** porque o **alcance exato** do apagamento e o formato do tombstone são decisão
  de DPO/jurídico do cliente; Reversibilidade **Cara** porque, uma vez anonimizada/apagada, a PII
  **não volta** (expurgo é destrutivo por natureza) — daí a marcação.

## Alternativas descartadas

- **Apagar todas as linhas de `consents` do titular.** Descartada: destrói a prova de revogação e a
  supressão futura (BR2) — o titular poderia ser re-incluído, violando o próprio pedido.
- **Não apagar nada (só marcar REVOKED).** Descartada: não atende o direito à eliminação se houver
  PII livre (`source`/conteúdo) — minimização exige remover o identificável de marketing.
- **Apagar também as `attributions`.** Descartada: não contêm PII do titular (são código+booking) e
  são métrica de negócio; apagá-las degradaria o DSS sem ganho de privacidade.
- **Inventar uma regra fina de "o que fica" sem o DPO.** Descartada explicitamente (invariante 3 do
  CLAUDE.md): adotamos o valor defensável e marcamos Confiança=Baixa para o dono confirmar.

## Impacto

- **Specs:** SPEC-0019 BR6 concretizada como "ASSUMIDO (ver DL-0058)".
- **Arquivos:** `MarketingService.erase(subject, actor)` (anonimiza consent + remove PII de
  marketing, preserva tombstone), endpoint `POST /api/marketing/erasure`; portabilidade =
  `GET /consents?subject=` já devolve o que o titular tem.
- **Migração:** nenhuma coluna nova obrigatória (anonimização atualiza `source`/`subject_id`); o
  `consents` já comporta.
- **Contratos:** `POST /api/marketing/erasure` → 200 com resumo `{ purgedConsents, anonymized }`.

## Como reverter

Cara/impossível por natureza: o expurgo é destrutivo (PII removida não retorna). Reverter a
**política** (ex.: o DPO decidir apagar a linha inteira em vez de anonimizar) é trocar o método
`erase` — refator de uma classe — mas **não** recupera dados já anonimizados. Por isso a marcação
Reversibilidade=Cara; recomenda-se confirmar a política com o DPO **antes** do primeiro uso real.
