# DL-0006 — Spring Modulith: detection-strategy = explicitly-annotated

- **Fase:** 0 (Fundação)
- **Spec(s):** SPEC-0001
- **ADR relacionado:** 0012 (três camadas), 0001 (modular monolith)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O ADR 0012 diz que os módulos Spring Modulith usam `@ApplicationModule` com
`detection-strategy=explicitly-annotated` ("os módulos são `domain.auth` …
`domain.screening`"), mas a SPEC-0001 não repete a configuração concreta. Com a
estratégia **padrão** do Modulith (`direct-sub-packages`) e o pacote base
`com.fksoft`, os subpacotes `domain`/`application`/`infra` seriam tratados como
"módulos", e o Modulith reprovaria `application → infra` — quebrando o build.

## Decisão

Configurar `spring.modulith.detection-strategy=explicitly-annotated` no
`application.yml`. Assim, **só** pacotes anotados com `@ApplicationModule` viram
módulos. Na Fase 0 não há módulo de negócio anotado → `ApplicationModules.of(...)`
fica vazio e `verify()` passa trivialmente. A partir da Fase 1, cada módulo
(`domain.accounts`, `domain.exchange`, ...) recebe `@ApplicationModule` e o
Modulith passa a impor as fronteiras entre eles.

## Justificativa

- Alinha 1:1 com o ADR 0012 (camadas `domain/application/infra` **não** são
  módulos; módulos são os contextos sob `domain.<módulo>`).
- Evita falso-positivo do Modulith tratando camadas como módulos.
- O gate de **camadas** (domain ⇏ application/infra; infra ⇏ application) é imposto
  por **ArchUnit** (ver SPEC-0001 Tests Required); o Modulith fica responsável
  pelas fronteiras **entre módulos de negócio** (a partir da Fase 1).

## Alternativas descartadas

- **Estratégia padrão `direct-sub-packages`** — descartada: trataria as camadas
  como módulos e reprovaria `application → infra` (permitido pelo ADR 0012).
- **Apontar a base do Modulith para `com.fksoft.domain`** — descartada:
  desnecessário; `explicitly-annotated` resolve sem fixar a base.

## Impacto

- Arquivos: `backend/src/main/resources/application.yml`, `ModularityTests`.

## Como reverter

Trocar a propriedade `spring.modulith.detection-strategy`. Trivial.
