# DL-0093 — Paleta de comandos `Ctrl/Cmd+K` + atalhos globais (registro central de comandos)

- **Fase:** 10 (UX & Frontend profissional)
- **Spec(s):** SPEC-0026
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Barata

## Lacuna

O ROADMAP pede "paleta de comandos `Ctrl/Cmd+K` + atalhos globais/contextuais + `?` ajuda +
autofoco". Falta decidir **a arquitetura** (como comandos são registrados, como atalhos são
capturados, como evitar conflito com inputs) e se entra biblioteca externa.

## Decisão

1. **Sem biblioteca de palette externa** (Regra Zero): a paleta é um componente próprio sobre
   PrimeNG `Dialog` + um `input` filtrável + lista navegável por teclado (`@angular/cdk` `A11yModule`
   para foco/`aria-activedescendant`).
2. **Registro central `CommandRegistry`** (serviço com signal): cada comando = `{ id, labelKey,
   icon?, keys?, run() }`. As entradas iniciais são **navegação** (ir para cada tela), **ações de
   tema** (alternar claro/escuro) e **sessão** (sair). Telas podem registrar comandos contextuais.
3. **`ShortcutService` global** (um único listener em `document`, registrado no shell): `Ctrl/Cmd+K`
   abre a paleta; `?` (Shift+/) abre o diálogo de ajuda com a lista de atalhos; `g` seguido de tecla
   (ex.: `g a` → Contas) navega; **ignora** quando o foco está em `input/textarea/select` ou em campo
   editável (evita capturar digitação), exceto `Ctrl/Cmd+K` que é global.
4. **Autofoco**: ao abrir a paleta, foco no campo de busca; `Esc` fecha; `↑/↓` navegam; `Enter`
   executa. `?` lista os atalhos a partir do mesmo registro (fonte única).

## Justificativa

- **Regra Zero:** uma paleta é um Dialog + input + lista; uma lib dedicada seria peso e risco de
  peer-deps sem ganho. PrimeNG já traz Dialog acessível; o CDK cobre foco/anúncio.
- **Registro central** evita atalhos espalhados e duplicados, e dá uma **fonte única** tanto para a
  paleta quanto para o `?` (ajuda), o que mantém os dois sempre coerentes.
- **Ignorar campos editáveis** é a prática consagrada (GitHub, Linear) para atalhos de letra única não
  atrapalharem digitação; `Ctrl/Cmd+K` permanece global por convenção de palette.

## Alternativas descartadas

- **`ngx-command-palette`/libs similares:** dependência extra para um componente pequeno (Regra Zero).
- **Listeners por componente:** espalha estado, gera conflito de atalhos, dificulta o `?` consistente.
- **Atalhos de letra única sempre ativos (sem ignorar inputs):** quebraria a digitação nos formulários.

## Impacto

- **Arquivos:** `core/commands/command-registry.service.ts`, `core/commands/shortcut.service.ts`,
  `shared/command-palette/*`, `shared/keyboard-help/*`, shell (monta os serviços). Sem backend.
- **Migração/Contrato:** nenhum.

## Como reverter

Remover os serviços e os componentes da paleta/ajuda e o listener no shell. Barata.
