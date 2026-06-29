# Decision Log — Índice

Registro append-only das decisões tomadas em modo autônomo (uma por arquivo
`DL-NNNN-*.md`). Cada decisão foi registrada **antes** do código que depende dela,
conforme `docs/RUN-PHASE.md`.

## ⚠️ Atenção (Reversibilidade=Cara ou Confiança=Baixa)

| DL | Título | Confiança | Reversibilidade | Por que destacada |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | Manter pacote base `com.fksoft` | Alta | **Cara** | Renomear o pacote raiz após código nascer gera diff amplo |

> Nenhuma decisão da Fase 0 ficou com Confiança=Baixa.

## Todas as decisões

| DL | Fase | Título | Conf. | Rev. |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | 0 | Manter pacote base `com.fksoft` | Alta | Cara |
| [DL-0002](DL-0002-stack-versoes-backend.md) | 0 | Versões do stack backend (Spring Boot 3.5.16, Modulith 1.4.12, Java 21) | Alta | Moderada |
| [DL-0003](DL-0003-stack-frontend-fase-0.md) | 0 | Stack frontend Fase 0 (Angular 22 + ngx-translate; PrimeNG/Tailwind adiados) | Alta | Barata |
| [DL-0004](DL-0004-maven-wrapper-bootstrap.md) | 0 | Bootstrap do Maven Wrapper sem Maven no sistema | Alta | Barata |
| [DL-0005](DL-0005-adr-0014-ausente-adiar-fase-1.md) | 0 | ADR 0014 ausente: ~~adiar~~ → **criado** a pedido do dono (ver ADR 0014) | Alta | Barata |
| [DL-0006](DL-0006-modulith-detection-strategy.md) | 0 | Spring Modulith detection-strategy=explicitly-annotated | Alta | Barata |
| [DL-0007](DL-0007-accounts-cadastros-opcionais.md) | 1 | Accounts: CADASTUR/IATA opcionais no v1 (nenhum cadastro obrigatório) | Média | Barata |
