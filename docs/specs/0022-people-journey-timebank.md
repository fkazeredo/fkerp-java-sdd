# 0022 - People (Colaboradores, Jornada e Banco de Horas)

Status: Approved
Related ADRs: 0011, 0012, 0014

> Convenções herdadas da **SPEC-0001**. **Subdomínio genérico** (redesenho linha 137/164: "avaliar
> comprar"): a entrega é o **mínimo** de RH que consome o **snapshot operacional do crawler (SPEC-0012)**
> e amarra holerite/ponto ao **Compliance**. Folha completa/eSocial: **comprar/integrar**.

## Goal

Dar ao RH um contexto que mantém **colaboradores**, processa a **jornada** a partir do snapshot
operacional do ponto (raspado em SPEC-0012), calcula **banco de horas** e sinaliza **divergências** —
deixando o **artefato legal (AFD/AEJ)** no Compliance e a folha pesada para um sistema especializado
(redesenho 7.8/275, linha 137).

## Scope

**Em escopo:** o agregado `Employee` (identificação, admissão, jornada contratada, status); o consumo de
`PointSnapshotCollected` para montar a **jornada do período** (entradas/saídas operacionais);
`TimeBank` (saldo de horas: extras/faltas) e a sinalização de **divergências** (marcação faltante,
inconsistência); vínculo do **holerite/espelho** ao Compliance (folha: retenção 5 anos; contrato:
indeterminado).

**Fora de escopo:** **cálculo de folha de pagamento**, eSocial, FGTS/INSS, férias/13º — **comprar/
integrar** (este módulo fornece colaborador + jornada + seam); o **crawler** e o **AFD legal** (SPEC-0012);
o **certificado** (SPEC-0023).

## Business Context

O snapshot raspado é **operacional** (não legal): serve para o RH ver jornada e faltas no dia a dia
(7.8). O documento com fé legal (AFD assinado) já está no Compliance. People transforma o snapshot em
**jornada/banco de horas** e aponta divergências para tratamento humano — sem pretender ser a folha.

## Business Rules

```txt
BR1  Employee MUST ter identifier, admissionDate, contractedJourney (ex.: 8h/dia) e status
     ACTIVE/ON_LEAVE/TERMINATED. O contrato de trabalho é documento no Compliance (retenção indeterminada).
BR2  Ao receber PointSnapshotCollected (operationalOnly=true), People MUST montar a jornada do período
     por colaborador (a partir das marcações operacionais) — tratando o snapshot como **não-legal**.
BR3  TimeBank: saldo do período = horas trabalhadas − jornada contratada; acumula extras/faltas conforme
     política (parâmetro governado/legislação — a confirmar).
BR4  Divergência (marcação ímpar/faltante, jornada incoerente) MUST gerar JourneyDiscrepancy (alerta)
     para tratamento humano — NÃO corrige sozinho.
BR5  Holerite/espelho processado MUST ser arquivável no Compliance (PAYROLL) com retenção de 5 anos.
BR6  People MUST NOT tratar o snapshot como documento legal nem recalcular o AFD — o legal vive no
     Compliance (SPEC-0012/0008).
```

## Input/Output Examples

```http
POST /api/people/employees
{ "identifier":"col-0012", "admissionDate":"2025-03-01", "contractedJourney":"08:00" }
201 Created  { "id":"emp1...", "status":"ACTIVE" }

GET /api/people/employees/{id}/timebank?period=2026-06
200 OK  { "period":"2026-06", "workedHours":"176:20", "contractedHours":"176:00",
          "balance":"+00:20", "discrepancies": 1 }
```

## API Contracts

- `POST /api/people/employees` / `GET .../employees/{id}` / `GET .../employees?status=` → CRUD + lista.
- `GET /api/people/employees/{id}/journey?period=` → jornada montada do snapshot.
- `GET /api/people/employees/{id}/timebank?period=` → saldo + divergências.
- `GET /api/people/discrepancies?period=&status=` → fila de divergências para tratamento.
- OpenAPI atualizada.

## Events

- `JourneyProcessed` — `{employeeId, period, balance, occurredAt}`. Produtor: `people`.
- `JourneyDiscrepancy` — `{employeeId, period, kind, occurredAt}` (alerta). Consumidor: RH/governança.

## Persistence Changes

```txt
V22__create_people.sql
  employees( id uuid PK, identifier varchar not null UNIQUE, admission_date date not null,
             contracted_journey varchar not null, status varchar not null, contract_document_id uuid null,
             created_at, updated_at timestamptz not null, created_by, updated_by varchar null, version bigint not null )
  journeys( id uuid PK, employee_id uuid not null, period char(7) not null,
            snapshot_ref uuid not null,                 -- snapshot operacional consumido (valor)
            worked_minutes int not null, balance_minutes int not null, processed_at timestamptz not null,
            UNIQUE (employee_id, period) )
  journey_discrepancies( id uuid PK, employee_id uuid not null, period char(7) not null,
                         kind varchar not null, status varchar not null, created_at timestamptz not null )
```

Consumo do snapshot é **idempotente** por (employee, period). O cálculo de jornada/banco é serviço de
domínio testável (datas/timezone). Folha pesada, se exigida, é **integração/compra**.

## Validation Rules

- Application: idempotência do consumo do snapshot; existência do colaborador.
- Domain: cálculo de saldo (BR3) e detecção de divergência (BR4) como serviço testável.
- Princípio: snapshot tratado como não-legal (BR2/BR6).

## Error Behavior

`people.employee.not-found` → 404; `people.employee.duplicate` → 409 (identifier);
`people.journey.not-found` → 404. i18n em `messages_pt_BR.properties`. Dados pessoais com controle de
acesso/trilha.

## Observability Requirements

- Logar processamento de jornada e divergências como evento de negócio (employeeId, period, correlation
  id), **sem PII sensível**. Métricas: `journeys_processed_total`, `journey_discrepancies_total`.

## Tests Required

- **Unit/domain:** cálculo de saldo (extras/faltas); detecção de marcação ímpar/faltante.
- **Integração (Testcontainers):** `PointSnapshotCollected` monta a jornada (idempotente); divergência
  gera `JourneyDiscrepancy`; arquivar holerite no Compliance com retenção 5 anos.
- **Regressão:** snapshot **nunca** vira documento legal aqui (falha antes, passa depois).

## Acceptance Criteria

- Cadastrar colaborador e, ao chegar o snapshot do período, montar a jornada e o saldo do banco de horas.
- Marcação inconsistente vira divergência para tratamento humano.
- `./mvnw verify` verde.

## Open Questions

- **Comprar vs. construir folha:** se o cliente exige folha/eSocial/FGTS completos, **integrar/comprar**
  e usar este módulo como colaborador+jornada — decisão do dono.
- **Política de banco de horas** (limites, compensação, acordo coletivo) — depende de regra
  trabalhista/negocial — **em aberto**.
- Q6 (tipo de REP) afeta o que o snapshot traz (SPEC-0012) e, por tabela, a jornada.

## Out of Scope

Folha de pagamento, eSocial, FGTS/INSS, férias/13º (comprar/integrar); crawler e AFD legal (SPEC-0012);
certificado (SPEC-0023).
