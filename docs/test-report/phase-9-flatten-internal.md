# Caderno de testes — Fase 9: Limpeza estrutural (remover `internal` do domain)

## Escopo

ADR + chore **estrutural** (sem SPEC), definido pela linha da Fase 9 do `docs/ROADMAP.md`. Achatar
`com.fksoft.domain.<módulo>.internal.*` → `com.fksoft.domain.<módulo>.*` em todos os módulos de
domínio (main e test), **sem mudar comportamento/contrato**, preservando a encapsulação por
`@ModuleInternal` + ArchUnit (ADR 0016 / DL-0089). Critério de aceite: **nenhum pacote `internal`
sob `com.fksoft.domain`; gates verdes**.

Como é refactor sem regra de negócio nova, **a suíte de testes de negócio existente é o critério**:
os 466 testes pré-existentes continuam verdes após cada lote (a fronteira que eles exercitam não
muda). O acréscimo de testes é **só** o do mecanismo de encapsulação (teeth).

## Casos de teste por tipo

### Arquitetura (ArchUnit) — o mecanismo que substitui o `internal`

| Caso | O que verifica | Resultado |
|---|---|---|
| `ArchitectureTest.MODULE_INTERNAL_TYPES_ARE_NOT_VISIBLE_ACROSS_MODULES` | **nova regra**: nenhuma classe de outro módulo de domínio depende de um tipo `@ModuleInternal` (exceções: próprio módulo + `infra`). Recria, para os 22 módulos, o esconde-automático que o `internal` dava ao Modulith | ✅ verde |
| `ArchitectureRulesHaveTeethTest.moduleInternalRuleFailsWhenAnotherModuleDependsOnAModuleInternalType` | **teeth da nova regra**: planta uma dependência de `archfixture.moduleb.ForeignConsumer` sobre `archfixture.modulea.SecretInternal` (`@ModuleInternal`) e prova que a regra **falha** | ✅ verde (falha plantada detectada) |
| `ArchitectureRulesHaveTeethTest.domainRuleFailsWhenDomainDependsOnInfra` | teeth da regra de camada (pré-existente) | ✅ verde |
| `ArchitectureRulesHaveTeethTest.intelligenceRuleFailsWhenIntelligenceDependsOnACommandFacade` | teeth de Intelligence (predicado agora reconhece `@ModuleInternal`) | ✅ verde |
| `ArchitectureRulesHaveTeethTest.platformRuleFailsWhenPlatformDependsOnACommandFacade` | teeth de Platform (idem) | ✅ verde |
| `ArchitectureTest` (demais 15 regras: camadas, `*Impl`, setters/Lombok em entidades, ACLs, Intelligence/Portfolio/Platform) | inalteradas, seguem verdes — os predicados de Intelligence/Portfolio/Platform passaram a reconhecer o marcador `@ModuleInternal` no lugar do sinal `.internal` | ✅ verde |

### Spring Modulith

| Caso | O que verifica | Resultado |
|---|---|---|
| `ModularityTests.verifiesModularStructure` | grafo de módulos acíclico, 22 `@ApplicationModule`; após o achatamento o Modulith não esconde mais os ex-`internal` (por isso a fronteira migrou para ArchUnit) | ✅ verde |

### Unitários / Integração (negócio) — regressão do refactor

Os **466 testes** de negócio pré-existentes (unitários de domínio, máquinas de estado, value objects,
exceções; integração de persistência/API/eventos com Testcontainers + Postgres real) **rodaram verdes
após cada lote** (9b..9e). Nenhum teste de negócio foi adicionado, alterado em lógica ou removido — só
os arquivos de teste que viviam em `…/internal/` foram movidos para a base do módulo, e os imports dos
testes que consomem tipos ex-`internal` de um pacote diferente (ex.: `com.fksoft.billing.*`,
`com.fksoft.admin.*`, `com.fksoft.platform.*`) foram reescritos para o pacote achatado.

### Smoke

`com.fksoft.system.SystemHealthIntegrationTest` (liveness/readiness `/api/system/health`) — ✅ verde.

## Resultado

`./mvnw verify` **verde** ao fim de cada lote e no fechamento. Total final: **468 testes**, 0
falhas, 0 erros (eram 466 na 0.20.0; o delta é o teste de teeth da nova regra + a própria regra
contada como `@ArchTest`). **ArchUnit 16 regras** (era 15). **Spotless** 0 alterações, **Checkstyle**
0 violações. **Nenhum pacote `internal`** sob `com.fksoft.domain` (main e test) — critério de aceite
atendido.

## Cobertura — o que NÃO está coberto e por quê

- **Sem testes de negócio novos**: é refactor estrutural; adicionar testes de negócio aqui seria fora
  de escopo. A garantia vem da suíte de 466 já verde + os gates.
- **Frontend**: não tocado (o achatamento é só do backend Java). `ng lint`/`ng test`/`ng build` ficam
  para a Fase 10 (UX), sem relação com esta fase.

## Como reproduzir

```bash
cd backend && ./mvnw verify          # build + 466 testes de negócio + ArchUnit (16) + Modulith + smoke
# Provar que a nova fronteira tem dentes (deve passar — o teste afirma que a regra FALHA na violação):
cd backend && ./mvnw -Dtest=ArchitectureRulesHaveTeethTest test
# Conferir o critério de aceite (deve não retornar nada):
grep -rn "com.fksoft.domain.[a-z]*.internal" backend/src --include=*.java
find backend/src -type d -path "*domain/*/internal"
```
