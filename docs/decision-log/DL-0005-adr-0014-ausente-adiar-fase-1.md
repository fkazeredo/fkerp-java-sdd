# DL-0005 — ADR 0014 (módulos iniciais) ausente: adiar criação para a Fase 1

- **Fase:** 0 (Fundação)
- **Spec(s):** SPEC-0001 (lista ADR 0014 em "Related ADRs")
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

A SPEC-0001 declara "Related ADRs: 0010, 0011, 0012, 0013, **0014**" e o ROADMAP
diz que "a decisão de conjunto inicial de módulos e ordem está registrada em
`docs/adr/0014`". Porém **o arquivo `docs/adr/0014` não existe** no repositório
(os ADRs vão de 0000 a 0013). É uma referência pendente.

## Decisão

**Não criar o ADR 0014 na Fase 0.** A Fase 0 (walking skeleton) não introduz
nenhum módulo de negócio — só as camadas `domain`/`application`/`infra` e o kernel
`domain.error`. O ADR 0014 documenta o **conjunto inicial de módulos e a ordem das
fatias**, que só passa a existir como código na **Fase 1** (Accounts, Exchange,
Commissioning, Quoting...). O ADR 0014 será criado na primeira fatia da Fase 1,
quando o primeiro `@ApplicationModule` nascer.

## Justificativa

- **Regra Zero / `simulation-and-mocking.md`:** não materializar decisões de
  módulos antes de existir o código que elas governam. Criar o ADR agora seria
  documentar fronteiras de módulos que a Fase 0 não tem.
- O conteúdo do ADR 0014 já está **descrito em prosa** no ROADMAP (mapa de fases,
  ordem de dependência da Fase 1) e no ADR 0001 (lista de módulos). Não há decisão
  perdida — apenas o ADR formal ainda não foi escrito.
- `workflow.md`: um ADR é criado quando a decisão de fronteira de módulo é tomada;
  isso ocorre na Fase 1.

## Alternativas descartadas

- **Criar o ADR 0014 agora** (copiando o ROADMAP) — descartada: especulativo na
  Fase 0; o lugar natural é a primeira fatia da Fase 1.
- **Remover a referência a 0014 da SPEC-0001** — descartada: a referência está
  correta como destino; apenas o ADR ainda não foi redigido.

## Impacto

- Specs: nota adicionada à SPEC-0001 esclarecendo que 0014 é entregue na Fase 1.
- Arquivos: nenhum código.
- Próxima fase: criar o ADR 0014 na Fase 1. (Atualização: o dono já o criou em
  `docs/adr/0014-initial-modules-and-slice-order.md` — ver seção de Atualização abaixo.)

## Como reverter

Antecipar a criação do ADR 0014 é só escrever o arquivo a partir do ROADMAP.
Trivial.

## Atualização (2026-06-29) — SUPERSEDED

O **próprio dono criou** o ADR 0014 — `docs/adr/0014-initial-modules-and-slice-order.md`
("Inclui a ADR 14 que estava perdida"). Portanto a lacuna está fechada e a decisão de
adiar deixa de valer. (Durante a execução autônoma eu cheguei a criar um arquivo
duplicado por interpretação errada do pedido; ele foi removido — o ADR válido é o do
dono.) Esta DL fica como histórico do raciocínio original de adiamento.
