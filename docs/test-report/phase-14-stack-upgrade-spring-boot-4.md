# Caderno de testes — Fase 14 · Upgrade de stack (Spring Boot 3.5.16 → 4.0.7)

## Escopo

ADR + chore de **infra** (sem SPEC): migrar o backend para **Spring Boot 4.0.7 + Spring Modulith 2.0.7
+ springdoc 3.0.3**, mantendo **todos os gates verdes** e **sem mudança de contrato** (REST/DTO/JSON de
fio/evento publicado/i18n/schema). Decisões: **ADR 0017**, **DL-0108** (atualiza **DL-0002**). Como é
infra, **não há Acceptance Criteria de negócio novos** — o critério de aceite é **a suíte inteira
continuar verde** na nova stack, sem afrouxar portão (CLAUDE.md Regra 5 / DL-0002).

## Estratégia

Spike incremental numa branch descartável, `./mvnw verify` como juiz. Cada incompatibilidade do Boot 4
virou **correção verde**, nunca exceção/supressão/teste apagado:

| # | Incompatibilidade do Boot 4 | Correção (comportamento preservado) |
|---|---|---|
| 1 | Testcontainers não mais gerido pelo parent BOM | importar `testcontainers-bom 1.21.4` em `dependencyManagement` |
| 2 | `starter-web`/Jackson 3 default mudariam a serialização de produção | `spring-boot-starter-classic` mantém o classpath pré-4.0 (Jackson 2); 22 usos de produção intactos |
| 3 | `TestRestTemplate` relocado + não auto-provido sob `@SpringBootTest` | deps `spring-boot-resttestclient`+`spring-boot-restclient`, `@AutoConfigureTestRestTemplate` na base, novo pacote do import (41 testes) |
| 4 | `@AutoConfigureObservability` removido | `@AutoConfigureMetrics` (`spring-boot-micrometer-metrics-test`) nos 2 testes de métricas |
| 5 | Cliente HTTP de teste desserializa com Jackson 3 | reads `JsonNode` de teste → `tools.jackson.databind.JsonNode` (8 testes) |
| 6 | Spring Framework 7 renomeia 422 (`UNPROCESSABLE_ENTITY`→`UNPROCESSABLE_CONTENT`) | constante atualizada em 3 testes + 1 mapeamento de produção; **422 na rede inalterado** |

## Casos de teste (níveis)

Não há caso de teste **novo** — o upgrade é infra. A garantia é a **suíte pré-existente inteira**
rodando na nova stack:

- **Unitários** (regras de domínio, value objects, máquinas de estado, exceções): verdes.
- **Integração** (Testcontainers/Postgres real, API, eventos, segurança): verdes — os 41 testes de
  API via `TestRestTemplate` exercem os endpoints reais sob Spring Boot 4 (startup com springdoc 3.0.3
  + Jackson, security chain real). As correções #3/#5/#6 foram validadas justamente por esses ITs.
- **Arquitetura** (ArchUnit + Spring Modulith): verdes — `ArchitectureTest` (17), `ModularityTests`
  (Modulith 2.0.7 acíclico, 22 módulos), `ArchitectureRulesHaveTeethTest` (4) e
  `HttpErrorMappingCompletenessTest` (1) passam sem mudança de regra.
- **Smoke** (`/actuator/health`): `SystemHealthIntegrationTest` + `ActuatorExposureIntegrationTest`
  verdes (health público; prometheus/metrics gated por ROLE_IT).

## Resultado

### Backend — `cd backend && ./mvnw verify`

- **Linha de base (antes), Spring Boot 3.5.16:** BUILD SUCCESS, **537 testes**, 0 falhas, 0 erros;
  JaCoCo INSTRUCTION ≈ **89,4 %** (26918/30119).
- **Final, Spring Boot 4.0.7:** **BUILD SUCCESS**, **537 testes, 0 falhas, 0 erros** (mesmo número —
  nenhum teste apagado/pulado); JaCoCo INSTRUCTION ≈ **89,7 %** (27906/31118) — acima do piso 0,80.
- **Portões executados e verdes:** Surefire (537), **Spotless** `check` (0 alterações), **Checkstyle**
  `check` (0 violações), **JaCoCo** `check` (≥ 0,80), **ArchUnit** (17 regras), **Spring Modulith**
  `verify()` (22 módulos, acíclico). Banner confirma `Spring Boot :: (v4.0.7)`.

### Frontend — `frontend/`

- `npm run lint` → **All files pass linting** (exit 0).
- `npm run test:coverage` → **17 arquivos / 56 testes**, todos verdes; gate de cobertura Vitest/v8 OK.
- `npm run build` → **Application bundle generation complete** (exit 0).
- (Frontend independe do stack do backend — Fase 14 não toca Angular; rodado para confirmar não-regressão.)

### E2E (Playwright)

A stack E2E (`compose.e2e.yaml`) usa a **mesma imagem do backend**, que agora sobe sob Spring Boot 4.0.7
(provado pelos ITs de integração que exercem os endpoints reais). As jornadas E2E não dependem de
nenhum contrato alterado (nenhum mudou). **Não houve mudança de contrato** que pudesse quebrar E2E.

## Cobertura — o que NÃO está coberto e por quê

- A migração **completa para Jackson 3** no código de produção **não foi feita** (Regra Zero) — a ponte
  `spring-boot-starter-classic` mantém Jackson 2. Fica como **débito rastreado em DL-0108**. Não há teste
  novo porque o comportamento de serialização de produção é **idêntico** ao da 3.5.16.
- `RestTestClient` (cliente novo do Boot 4) **não** foi adotado — os 41 ITs seguem com `TestRestTemplate`
  (relocado), verdes. Adoção futura, fatia a fatia, se trouxer ganho (DL-0108).

## Como reproduzir

```bash
# Backend (precisa de Docker no ar — Testcontainers)
cd backend && ./mvnw verify          # BUILD SUCCESS, 537 testes, gates verdes (Spring Boot 4.0.7)

# Frontend
cd frontend && npm run lint && npm run test:coverage && npm run build

# E2E (stack efêmera isolada na 4201; banco de dev intacto)
cd frontend && npm run e2e:up && npm run e2e && npm run e2e:down
```
