# Caderno de testes — Slice 12-1 (JaCoCo: cobertura do backend como portão)

## Escopo

- **Spec:** SPEC-0028 (AC1, BR2, BR3).
- **Entrega:** `jacoco-maven-plugin` no `backend/pom.xml` — `prepare-agent` + `report` (HTML/XML/CSV em
  `target/site/jacoco`) + **`check`** (INSTRUCTION ≥ 80%, DL-0099) bound à fase `verify`. O `check`
  **quebra o build** quando a cobertura cai abaixo do piso. Nenhum portão existente afrouxado.

## Casos de teste por tipo

### Portão (cobertura)

| Caso | O que verifica | AC / regra |
|---|---|---|
| `./mvnw verify` gera relatório JaCoCo | `target/site/jacoco/index.html` + `jacoco.csv` produzidos | AC1 |
| `jacoco-check` aplica o piso | "All coverage checks have been met" na fase `verify` com o código atual | AC1/BR2 |
| Piso é gate de verdade | Plantar um piso acima do medido faz `BUILD FAILURE` (prova do dente) | BR2 |
| 477 testes intactos | `Tests run: 477, Failures: 0, Errors: 0` sob `verify` | BR3 |

### Resultado

- `cd backend && ./mvnw -B -ntp verify` → **BUILD SUCCESS**, `Tests run: 477, Failures: 0, Errors: 0,
  Skipped: 0`, **"All coverage checks have been met."** (Total time ~1m14s).
- **Cobertura medida (relatório JaCoCo total):** **89% de instruções** (3.225 de 30.464 perdidas);
  branch ~68%. O piso de **80%** (DL-0099) fica ~9 pp abaixo → gate de não-regressão com folga.
- **Prova do dente:** com `jacoco.min.instruction.ratio` acima do medido o `check` falha (verificado em
  bancada; restaurado para 0.80).

## Cobertura (o que NÃO está coberto e por quê)

- O contador do gate é **INSTRUCTION** (sinal mais estável; DL-0099). Branch/line têm relatório mas não
  gate (poderiam entrar como melhoria incremental futura — Out of Scope da SPEC-0028).
- Exclusões (sem lógica testável): `FkErpApplication`/`**/*Application`, `package-info`, `**/dto/**`,
  `**/config/**`. Refletem o que não agrega como contrato de não-regressão.

## Como reproduzir

```bash
cd backend && ./mvnw -B -ntp verify
# relatório:
open backend/target/site/jacoco/index.html
# prova do gate (deve FALHAR):
#   editar jacoco.min.instruction.ratio para 0.95 no pom.xml e rodar ./mvnw verify
```
