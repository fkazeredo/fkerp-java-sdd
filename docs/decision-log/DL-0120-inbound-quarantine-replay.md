# DL-0120 — Inbound rejeitado vai para QUARENTENA com replay operacional (revisa a DL-0017)

- **Fase:** 19b (Refactoring de maturidade — revisão do decision-log)
- **Spec(s):** SPEC-0009 (BR7 revisada; BR10 nova)
- **ADR relacionado:** `architecture/messaging-and-integrations.md` (integrações externas; DLQ/exception queue)
- **Data:** 2026-07-02
- **Status:** DECIDIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A DL-0017 (Confiança **Baixa**) decidiu que um inbound com Account desconhecida é **rejeitado
(422) e descartado** — "não cria provisória nem enfileira". A revisão dirigida da Fase 19
confrontou essa decisão com a prática de mercado de integrações: rejeitar **e perder** o payload
na fronteira transforma um problema operacional trivial (conta ainda não cadastrada) em perda de
negócio silenciosa — o site externo raramente reenvia. O padrão maduro é a **exception
queue/quarentena**: preservar o payload rejeitado para correção + replay.

## Decisão

1. **O contrato externo da DL-0017 NÃO muda:** o webhook continua respondendo **422
   `integration.account.not-found`** e **nada é criado no núcleo** (sem conta provisória).
2. **O payload traduzido é PRESERVADO** em `inbound_quarantine` (V37), gravado em **transação
   própria** (`REQUIRES_NEW` — a linha sobrevive ao rollback do 422), **idempotente por
   `externalQuotationId`** (um pendente por id; índice único parcial `WHERE status='QUARANTINED'`).
3. **Operação (papel OPERATIONS, tela Origem de ofertas):** listar, **reprocessar** (roda o
   `processInbound` normal; sucesso ⇒ `REPLAYED` + vínculo ao quote criado; causa persistente ⇒
   mesma 422 e a entrada segue pendente) e **descartar** (`DISCARDED`). Transição sobre entrada
   resolvida ⇒ 409.
4. **Falha de assinatura/payload NÃO quarentena** — payload não-autenticado/malformado não
   persiste nada (fronteira de segurança intacta, DL-0016).
5. O status é **máquina de estado** (QUARANTINED→REPLAYED|DISCARDED) e permanece **enum**
   (critério de keep da Fase 18/ADR-0019, documentado no Javadoc).

## Justificativa

- `messaging-and-integrations.md`: "External systems are unreliable by definition" — e o
  ERP também é "unreliable" do ponto de vista do site externo (a conta pode ainda não existir).
  Idempotência + fila de exceção é o par recomendado.
- A quarentena resolve a tensão da DL-0017 sem reabrir a decisão de negócio: continua **não**
  criando conta provisória (só o dono pode mudar isso), mas elimina a perda de dado.
- Reversível barato: desligar = não gravar quarentena (uma chamada); a tabela é aditiva.

## Alternativas descartadas

- **Criar conta provisória automaticamente:** decisão de negócio explícita da DL-0017 contra;
  segue sendo do dono (invariante 3).
- **DLQ genérica em infra (payload cru):** guardaria o DTO externo cru fora da ACL; a quarentena
  guarda o **comando traduzido** (o vendor shape continua confinado à ACL — BR6/ArchUnit).
- **Manter o descarte (status quo):** perde dado na fronteira; contra a prática de mercado.

## Impacto

- **Arquivos:** `InboundQuarantineEntry/Status/Repository/View/Service` + exceções (404/409) no
  módulo `sourcing`; catch no `QuotationSiteInboundController`; endpoints
  `GET/POST /api/sourcing/inbound-quarantine[...]` (matriz 19a já cobre via `/api/sourcing/**` →
  OPERATIONS); `HttpErrorMapping` +2; i18n +2 (pt/en); tela Sourcing ganha a seção "Quarentena de
  integração"; migração **V37**.
- **Specs:** SPEC-0009 BR7 anotada como REVISADA + BR10 nova.
- **Contratos:** endpoints novos aditivos; o webhook não muda.
- **DL-0017:** permanece no log (append-only) com nota de revisão apontando para esta.

## Como reverter

Barata: remover o catch do controller (para de quarentenar) e/ou os endpoints; a tabela V37 é
aditiva e pode ficar. Nenhum contrato existente depende da quarentena.
