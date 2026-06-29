# Decision Log — Índice

Registro append-only das decisões tomadas em modo autônomo (uma por arquivo
`DL-NNNN-*.md`). Cada decisão foi registrada **antes** do código que depende dela,
conforme `docs/RUN-PHASE.md`.

## ⚠️ Atenção (Reversibilidade=Cara ou Confiança=Baixa)

| DL | Título | Confiança | Reversibilidade | Por que destacada |
|---|---|---|---|---|
| [DL-0001](DL-0001-pacote-base-com-fksoft.md) | Manter pacote base `com.fksoft` | Alta | **Cara** | Renomear o pacote raiz após código nascer gera diff amplo |
| [DL-0009](DL-0009-quoting-formula-de-preco.md) | Quoting: preço = base BRL + markup (default 0) | Média | **Cara** | Fórmula de preço move a tese econômica; refator amplo se mudar |
| [DL-0017](DL-0017-inbound-account-not-found-rejects.md) | Inbound: Account inexistente **rejeita** (422) | **Baixa** | Moderada | Decisão de negócio em aberto na SPEC-0009; só o dono fecha |

> DL-0017 é a decisão de **Confiança=Baixa** da Fase 3 (Open Question de negócio). DL-0009/DL-0017 e
> DL-0018 (reuso do agregado para o ramo INTEGRATED) são as de reversão não-barata desta fase.

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
| [DL-0008](DL-0008-exchange-nome-do-modulo.md) | 1 | Manter o nome `Exchange` para o módulo de câmbio (Q1) | Alta | Moderada |
| [DL-0009](DL-0009-quoting-formula-de-preco.md) | 1 | Quoting: preço = base BRL + markup (default 0); base comissionável em BRL | Média | **Cara** |
| [DL-0010](DL-0010-booking-quote-multiplicidade.md) | 1 | Booking: Quote→Booking não 1:1 no v1 (localizador é a trava) | Média | Moderada |
| [DL-0011](DL-0011-reconciliation-tolerancia-discrepancia.md) | 1 | Reconciliation: tolerância = max(R$1,00; 0,5% do spread esperado) | Média | Barata |
| [DL-0012](DL-0012-compliance-requirements-catalog.md) | 2 | Compliance: catálogo `entryType × DocumentRequirement` (seed da tabela 7.7, com fase) | Média | Barata |
| [DL-0013](DL-0013-finance-multimoeda-no-razao.md) | 2 | Finance: razão em moeda original (sem conversão); período agrega por moeda | Média | Moderada |
| [DL-0014](DL-0014-finance-comprar-vs-construir.md) | 2 | Finance: construir o seam mínimo (AP/AR+período) agora; contabilidade plena = comprar depois | Alta | Moderada |
| [DL-0015](DL-0015-compliance-filestorage-port.md) | 2 | Compliance: porta `FileStorage` + adaptador filesystem; hash SHA-256 | Alta | Barata |
| [DL-0016](DL-0016-inbound-webhook-signature-hmac.md) | 3 | Webhook de entrada: assinatura HMAC-SHA256 com segredo compartilhado (`X-Signature`) | Média | Moderada |
| [DL-0017](DL-0017-inbound-account-not-found-rejects.md) | 3 | Inbound: Account inexistente **rejeita** (422); não cria provisória nem enfileira | Baixa | Moderada |
| [DL-0018](DL-0018-integrated-quote-modeling.md) | 3 | Quote INTEGRATED reusa o agregado; colunas de composição MANUAL viram nulas | Alta | Moderada |
| [DL-0019](DL-0019-acl-resilience-scope-inbound.md) | 3 | ACL de entrada: classificação de falha + observabilidade (sem circuit breaker — não há chamada de saída) | Alta | Barata |
