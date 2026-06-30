# Caderno de testes — Slice 10-2: Login/silent-refresh + paleta de comandos + atalhos + canDeactivate

## Escopo
SPEC-0026 — AC4 (paleta), AC5 (atalhos/ajuda), AC6 (login), AC7 (silent refresh), AC8 (canDeactivate).
DL-0092 (silent refresh), DL-0093 (paleta/atalhos).

## Casos de teste (component/unit — vitest headless)

| Caso | Verifica | AC / BR |
|---|---|---|
| AuthService: `/me` 200 mantém e atualiza usuário | revalidação preserva sessão e usa resposta do backend | AC7 / BR7 |
| AuthService: `/me` 401 limpa sessão | logout silencioso em token inválido | AC7 / BR7 |
| AuthService: `bootstrapSession` revalida com token salvo | chamada `/me` no boot | AC7 / BR7 |
| AuthService: `bootstrapSession` sem token não chama nada | sem token, nada acontece | AC7 |
| CommandRegistry: registra e executa por id | `run(id)` dispara o comando | AC4 |
| CommandRegistry: só comandos com hint são shortcuts | `shortcuts()` filtra | AC5 |
| CommandRegistry: disposer remove os comandos | limpeza correta | AC4 |
| ShortcutService: Ctrl+K abre paleta | listener global | AC4 / BR4 |
| ShortcutService: Cmd+K abre paleta | mac | AC4 / BR4 |
| ShortcutService: `?` abre ajuda | ajuda derivada do registry | AC5 / BR5 |
| ShortcutService: `g a` navega | leader + nav key | AC5 / BR5 |
| ShortcutService: ignora letra em input | não navega ao digitar | AC5 / BR5 |
| ShortcutService: Ctrl+K funciona dentro de input | palette global mesmo em campo | AC4 / BR4 |
| canDeactivate: limpo permite sair | sem confirmação | AC8 / BR9 |
| canDeactivate: sujo + aceitar → sai | confirma e navega | AC8 / BR9 |
| canDeactivate: sujo + cancelar → fica | bloqueia | AC8 / BR9 |
| LoginPage: navega à raiz no sucesso | `navigateByUrl('/')` | AC6 / BR6 |
| LoginPage: honra returnUrl | `navigateByUrl(returnUrl)` | AC6 / BR7 |
| LoginPage: erro genérico em credencial inválida | mostra código, não navega | AC6 / BR6 |
| LoginPage: estado submetendo | botão desabilitado durante a chamada | AC6 / BR6 |
| LoginPage: não submete vazio | guarda de campos | AC6 |

## Resultado
- `npm run lint` → **All files pass linting** (as regras a11y do template pegaram 3 erros na paleta —
  corrigidos com `tabindex`/`keydown` e movendo o handler para o `<input>` focável; nada foi afrouxado).
- `CI=true npx ng test --no-watch` → **14 arquivos / 42 testes — todos verdes** (era 10/25).
- `npm run build` → **sucesso** (initial 799.22 kB raw / 171.20 kB transfer; dentro do budget).

## Cobertura
- Coberto: revalidação de sessão (4 caminhos), registry (3), atalhos (6), canDeactivate (3), login (5).
- Não coberto aqui: estados das telas repaginadas (10-3) e dashboard (10-4); o agendamento de
  revalidação por timer é exercido só indiretamente (a chamada `/me` é o que importa).

## Como reproduzir
```bash
cd frontend && npm ci
npm run lint
CI=true npx ng test --no-watch
npm run build
```
