# Plano — Fase 8i · People (Colaboradores, Jornada e Banco de Horas) · SPEC-0022

> Release alvo: **0.17.0**. Base: `origin/develop` @ 30d0825 (0.16.0, 18 módulos Modulith,
> fases 0–8h verdes). Branch de integração: `feature/8i-integration`.
> Prosa em pt-BR, código em inglês (convenção do projeto).

## 1. Contexto e dependência

O módulo `people` **já existe** desde a Fase 6 (SPEC-0012): é dono do **snapshot operacional**
do ponto (`PointSnapshot`, idempotente por `(sourceRef, periodRef)`, sempre
`operationalOnly=true`) e do histórico de execução do crawler (`PointCrawlRun`). O crawler
técnico vive em `infra.integration.pointclock` (ACL) e dirige o módulo só pela fachada
`PointSnapshotService`. **Esta fase NÃO reescreve o crawler nem o snapshot** — ela
**constrói por cima**: colaboradores, jornada do período, banco de horas e divergências.

A SPEC-0022 entrega o **mínimo de RH** (subdomínio genérico — "comprar vs. construir"): o
módulo fornece `Employee` + jornada + banco de horas + seam; **folha pesada / eSocial / FGTS /
férias / 13º ficam para comprar/integrar** (Out of Scope). O artefato **legal** (AFD/AEJ
assinado) continua no Compliance (SPEC-0012/0008); o snapshot é **não-legal** (BR6).

## 2. Decisão de modelagem crítica (registrada em DL)

O `PointSnapshot` atual guarda só o **agregado** (`marks` = nº de marcações no período) e um
`payloadRef` opaco (o espelho via `FileStorage`). Não há detalhe **por colaborador**. Para
montar a jornada por colaborador (BR2) sem reescrever o crawler nem inventar parser de espelho
(Regra Zero), a fase modela:

- **Cálculo de jornada/banco como serviço de domínio puro e testável** (`JourneyCalculator`):
  recebe `workedMinutes` (operacional do período) × `contractedMinutes` e devolve saldo +
  classificação. É o "miolo testável (datas/timezone)" que a spec pede.
- **Seam de consumo do snapshot** idempotente por `(employee, period)`: o uso de caso recebe o
  `snapshotRef` consumido (valor) + os minutos trabalhados operacionais por colaborador. Em v1
  esses minutos chegam como **entrada operacional** (no mundo real, extraídos do espelho por
  colaborador; aqui informados explicitamente — o snapshot raspado é, por contrato, operacional
  e não-legal). Reprocessar o mesmo `(employee, period)` é idempotente (atualiza no lugar).

Ver **DL-0069** (modelagem jornada/seam), **DL-0070** (política de banco de horas — CLT),
**DL-0071** (divergências), **DL-0072** (arquivo de holerite no Compliance).

## 3. Fatias (ordem de dependência)

### Fatia 8i-1 — `Employee` (cadastro) + migração V27
- **Entrega:** agregado `Employee` (identifier único, admissionDate, contractedJourney `HH:mm`,
  status `ACTIVE|ON_LEAVE|TERMINATED`, `contractDocumentId` opcional — valor do Compliance);
  CRUD: `POST /api/people/employees`, `GET .../{id}`, `GET .../employees?status=` (paginado).
- **Migração:** `V27__create_people_hr.sql` cria `employees`, `journeys`,
  `journey_discrepancies` (a tabela `point_snapshots`/`point_crawl_runs` já existe da V16).
- **Erros:** `people.employee.not-found` (404), `people.employee.duplicate` (409, identifier),
  `people.employee.invalid` (400).
- **Testes:** unit do agregado (invariantes, parse de `contractedJourney`); integração
  (criar/duplicar/buscar/listar) com Postgres real.

### Fatia 8i-2 — Jornada do período + banco de horas + divergências
- **Entrega:** `JourneyCalculator` (domínio puro): saldo do período = trabalhado − contratado
  (em minutos); detecção de divergência (marcação ímpar/faltante → `JourneyDiscrepancy`).
  `PeopleService.processJourney(...)` idempotente por `(employee, period)`; publica
  `JourneyProcessed`; divergência publica `JourneyDiscrepancy` (alerta, **não** corrige sozinho
  — BR4).
- **API:** `GET .../employees/{id}/journey?period=`, `GET .../employees/{id}/timebank?period=`,
  `GET /api/people/discrepancies?period=&status=` (fila para tratamento).
- **Erros:** `people.journey.not-found` (404), `people.journey.invalid` (400).
- **Observabilidade:** logar jornada/divergência como evento de negócio (employeeId, period,
  correlation id) **sem PII**; métricas `journeys_processed_total`, `journey_discrepancies_total`.
- **Testes:** unit do cálculo (extras/faltas, saldo negativo, marcação ímpar/faltante);
  integração (processar é idempotente; divergência gera evento+linha; **regressão**: snapshot
  nunca vira documento legal — nenhum `Document` criado aqui).

### Fatia 8i-3 — Holerite/espelho arquivado no Compliance (PAYROLL)
- **Entrega:** `POST /api/people/employees/{id}/payslip` (multipart: arquivo + período) →
  orquestrador em `infra` chama `ComplianceService.upload(PAYROLL, …, hasPersonalData=true)`
  (retenção 5 anos) e referencia o `documentId` por **valor**. People **não** vira cofre.
- **Testes:** integração (arquivar holerite cria Document `PAYROLL` com `retentionUntil = +5 anos`
  e `hasPersonalData=true`).

## 4. Gates inegociáveis (toda fatia)
- `./mvnw verify` verde (ArchUnit + Spring Modulith + Checkstyle/Spotless), Docker up.
- Migração V27 idempotente; nunca editar migração já aplicada.
- DomainException `code == chave i18n`; pt-BR + fallback en.
- Sem FK cross-contexto (snapshot/contractDocument/payroll referenciados por valor); eventos in-process.
- Construtor injection; sem `@Data/@Setter` em entidade; sem `*Impl`; sem TODO solto.
- LGPD: dado pessoal mascarado nos logs; acesso a holerite auditado (via Compliance).
- OpenAPI atualizada (descrição springdoc + versão 0.17.0).

## 5. Documentação (Definition of Done)
- Caderno de testes por fatia em `docs/test-report/8i-*.md` + INDEX.
- Release note `docs/release-notes/0.17.0.md` (pt-BR) + append `docs/release-notes/CHANGELOG.en-US.md`.
- `docs/MANUAL.md` (pt-BR) **e** `docs/MANUAL.en-US.md` (en-US) em sincronia: nova seção People.
- Decision-log DL-0069..DL-0072 + INDEX atualizado (destaque Baixa/Cara).
- SPEC-0022: mover Open Questions resolvidas para Business Rules marcando "ASSUMIDO (ver DL-NNNN)".

## 6. Riscos
- **Banco de horas (DL-0070):** janela de compensação/limites dependem de acordo coletivo da
  empresa — **Confiança=Baixa**; v1 entrega saldo mensal + política configurável (default legal).
- **Origem dos minutos por colaborador (DL-0069):** o snapshot raspado é agregado; o detalhe por
  colaborador é entrada operacional no v1 — seam rastreável, sem parser especulativo de espelho.
