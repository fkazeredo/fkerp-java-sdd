# DL-0034 — PromoFxAdvisor: sujeito = agência (derivado de evento), não rota; intelligence é consumidor-folha

- **Fase:** 7 (Intelligence / DSS)
- **Spec(s):** SPEC-0013 (BR2 "Intelligence MUST ser SOMENTE LEITURA"; BR5 "PromoFxAdvisor: por
  rota/agência/produto"; Events "Intelligence consome (read-only) ... publica nenhum comando")
- **ADR relacionado:** 0010 (infra centralizada), 0012 (camadas), 0014 (módulos/fatias)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0013 descreve o `PromoFxAdvisor` "por **rota**/agência/produto" e o exemplo de payload usa
`subject:{kind:"ROUTE","ref":"GRU-MCO"}`. Porém **nenhum dos eventos disponíveis** das fases 1-6
carrega rota: `RateSubsidyAccrued{bookingId,subsidy,...}` e `FxPositionClosed{bookingId,...}` são
chaveados por `bookingId`; `BookingConfirmed{bookingId,quoteId,accountId,...}` carrega a **conta
(agência)**; `SpreadRealized{caseId,...}` é chaveado por `caseId`. Não há produto nem rota no fluxo
de eventos atual. Era preciso decidir **em que sujeito o advisor é projetado** sem violar o princípio
do redesenho (intelligence só lê eventos; nunca chama de volta um produtor).

## Decisão

1. **Sujeito do v1 = `AGENCY`** (a conta comercial), porque é o único dos três eixos
   (rota/agência/produto) **derivável dos eventos existentes**: `BookingConfirmed.accountId`. O
   read-model `Insight.subjectKind` é um enum (`AGENCY`, `ROUTE`, `PRODUCT`, `SUPPLIER`) já desenhado
   para plugar rota/produto depois **sem refatorar** o agregado, quando um evento passar a carregá-los.
2. **Correlação 100% por evento (consumidor-folha).** Intelligence mantém uma projeção
   `booking → account` aprendida de `BookingConfirmed`, e atribui o subsídio/gap dos eventos
   chaveados por `bookingId` (`RateSubsidyAccrued`, `FxPositionClosed`) à conta correspondente.
   **Nunca** chama `BookingService`/`FxPositionService`/repositório de outro módulo para resolver a
   conta — isso criaria a dependência de volta que o redesenho proíbe e quebraria a aciclicidade do
   Spring Modulith. Se um evento de subsídio chega antes do `BookingConfirmed` (ordem não garantida),
   o subsídio fica **pendente** (buffer no read-model) e é atribuído quando o mapping chega; nada é
   perdido nem inventado.
3. **`SpreadRealized` é chaveado por `caseId`**, sem booking no payload — portanto **não** é usado na
   v1 do PromoFxAdvisor (evitaria adivinhar a correlação). A receita/realização atraída usa
   `FxPositionClosed.totalGap`/`realizedDrift` (chaveado por `bookingId`, já correlacionável). Fica
   registrado como melhoria: enriquecer `SpreadRealized` com `bookingId` numa fase futura.

## Justificativa

- **BR2 (só leitura) e o princípio do redesenho** exigem que intelligence seja folha do grafo: o
  ArchUnit desta fase trava qualquer dependência de `intelligence` para outros módulos de domínio
  (DL-0035 detalha o teste). Resolver conta via evento é a única forma de respeitar isso.
- **Recomendação do ROADMAP (Q-Preço / governança):** modelar eixos como dado plugável evita
  hardcode; o enum `subjectKind` é o seam para rota/produto sem refator do agregado.
- **Rule Zero:** não inventamos rota (dado que o sistema ainda não produz). Usamos o eixo realmente
  disponível (agência) e deixamos o seam pronto — sem dado falso (espelha a postura da SPEC-0013 BR6
  para o OverrideNudge).

## Alternativas descartadas

- **Subject = ROUTE agora.** Descartada: exigiria inventar a rota ou ler tabelas de booking/sourcing
  (chamada de volta proibida). Sem evento que carregue rota, seria dado falso.
- **Intelligence consulta `BookingService` para obter `accountId` do `bookingId`.** Descartada:
  cria dependência `intelligence → booking` (ciclo de consumidor para produtor), quebra o
  "aconselha, nunca comanda" e a aciclicidade do Modulith. A correlação por evento é o caminho.
- **Usar `SpreadRealized` adivinhando o booking pelo caseId.** Descartada: `caseId ≠ bookingId` e não
  há evento que ligue os dois no payload; adivinhar violaria "nunca inventar".

## Impacto

- Novo módulo `com.fksoft.domain.intelligence` (12º `@ApplicationModule`), leaf consumer.
- `Insight.subjectKind` enum com `AGENCY|ROUTE|PRODUCT|SUPPLIER`; v1 emite `AGENCY`.
- Projeção `booking→account` + buffer de subsídio pendente (idempotente, recomputável).
- Migração `V17__create_intelligence.sql` (read-model `insights` + tabela de correlação/atribuição).
- Teste e2e (Testcontainers) com relógio controlado: eventos → Insight por agência.

## Como reverter

Reversão **moderada**: trocar o eixo para rota/produto é (a) enriquecer os eventos produtores com o
campo do eixo (mudança nos módulos donos) e (b) trocar `subjectKind` emitido + o teste. O agregado e
a migração já comportam os outros eixos (enum + `subject_ref` textual), então não há mudança de
schema do `insights` — só dos eventos de origem e da regra de atribuição.
