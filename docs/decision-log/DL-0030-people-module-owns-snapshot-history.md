# DL-0030 — Novo módulo `people` (bounded context real) é dono do snapshot operacional, da idempotência e do histórico de execução; o crawler técnico fica em `infra/integration`

- **Fase:** 6 (Crawler de ponto)
- **Spec(s):** SPEC-0012 (BR2 publicar snapshot; BR3 `operationalOnly=true`; BR5 idempotência por
  `(sourceRef, periodRef)`; BR6 não escrever no miolo; BR7 histórico de execução; Persistence `point_snapshots`
  e `point_crawl_runs`)
- **ADR relacionado:** 0010 (porta por adaptador), 0012 (camadas), 0001 (monólito modular)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0012 fala em três donos possíveis (`Integration` para o crawler, `People` para o consumo do snapshot,
`Platform` para credenciais/agendador), mas **não crava** qual módulo Spring Modulith nasce nesta fase nem
quem é dono das tabelas `point_snapshots`/`point_crawl_runs`. É preciso decidir as fronteiras **sem** criar
módulo vazio especulativo (Regra Zero / `workflow.md`: "MUST NOT create fake bounded contexts or placeholder
classes") e **sem** formar ciclo no Modulith.

## Decisão

- **Criar UM módulo de negócio novo: `com.fksoft.domain.people`** (11º Modulith). Justificativa de Regra Zero:
  ele recebe agora **comportamento real** — o `PointSnapshotService` (caso de uso de coleta, idempotente),
  o agregado `PointSnapshot` (`operationalOnly=true`), o histórico de execução `PointCrawlRun` e os eventos
  `PointSnapshotCollected`/`PointCrawlingFailed`. Não é casca: tem entidade, regra e API pública. O redesenho
  mapeia `People` = "colaboradores, jornada, ponto (físico, via crawling)".
- **NÃO criar um módulo `platform` agora.** Regra Zero: não há comportamento de domínio de Platform nesta fase
  que justifique um bounded context — o **agendamento** do job é um adaptador técnico em `com.fksoft.infra.jobs`
  (como `RetentionExpiryScheduler`/`BookingTimeoutScheduler` já são), e a **custódia de credenciais** é
  SPEC-0023 (fora de escopo aqui). Criar `platform` vazio seria casca proibida. (Ver DL-0031 para credenciais.)
- **O crawler técnico (cliente do portal, fila, disjuntor, parser do espelho) vive em
  `com.fksoft.infra.integration.pointclock`** (ACL), **não** no módulo. Ele depende do `people` (chama o caso
  de uso público) e do `compliance` — direção `infra → domain`, **permitida e acíclica**. O formato externo do
  portal **nunca** cruza para o domínio (regra ArchUnit, como a do `quotationsite` da SPEC-0009).
- **Dono das tabelas:** `point_snapshots` e `point_crawl_runs` pertencem ao módulo `people` (entidades internas).
  O adaptador de `infra` opera-as **através do caso de uso público** do `people` (não escreve direto nas tabelas
  de outro módulo de negócio; a exceção da regra de persistência vale só para `com.fksoft.infra`, mas aqui nem
  é preciso — tudo passa pela fachada).

## Justificativa

- **`people` é leaf** (nada depende dele exceto `infra`), então não há risco de ciclo Modulith — diferente do
  cuidado que a DL-0028 teve com `exchange`. O crawler em `infra` chamando `people`/`compliance` é a direção
  natural e acíclica.
- **Separar técnico (crawler/fila/breaker em `infra`) de negócio (snapshot/histórico/idempotência em `people`)**
  segue `modules-and-apis.md` (ACL em infra; domínio puro) e `messaging-and-integrations.md` (a resiliência é
  preocupação de fronteira de integração).
- **Um módulo real, justificado**, respeita a Regra Zero; adiar `platform` evita casca especulativa.

## Alternativas descartadas

- **Pôr o snapshot e o histórico dentro de `infra/integration` (sem módulo de negócio).** Descartada: snapshot,
  idempotência e histórico são **estado/regra de negócio** (consumidos pelo RH/People), não detalhe técnico;
  ficariam fora de fronteira e sem dono claro.
- **Criar `people` + `platform` juntos.** Descartada por Regra Zero: `platform` não recebe comportamento real
  nesta fase (agendador é adaptador; credenciais são SPEC-0023). Seria bounded context falso.
- **Criar um módulo `integration` de domínio.** Descartada: a `Integration` do redesenho é a **camada ACL**
  (`com.fksoft.infra.integration`), não um bounded context com agregados; já é onde o crawler vive.

## Impacto

- **Novo módulo** `com.fksoft.domain.people` (+ `internal`), `@ApplicationModule(displayName="People")` (11º).
- **Migração** `V16__create_point_integration.sql`: `point_snapshots` (UNIQUE `(source_ref, period_ref)` —
  idempotência BR5) e `point_crawl_runs` (histórico BR7).
- **Adaptador** `com.fksoft.infra.integration.pointclock` (crawler + fila + breaker + parser do espelho) e
  `com.fksoft.infra.jobs.PointClockCrawlScheduler` (agendador).
- **Regra ArchUnit** nova: o DTO externo do portal de ponto não cruza para o domínio.

## Como reverter

Reversão **moderada**: se mais tarde o `Platform` (SPEC-0023) virar módulo de negócio, o agendador e a
governança de jobs migram de `infra/jobs` para lá sem mexer no `people`; se o snapshot precisar de outro dono,
move-se o agregado, mas o contrato de eventos permanece. A fronteira técnico × negócio escolhida não trava
essas evoluções.
