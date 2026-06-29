# Release <versão> — <título>

- **Data:** YYYY-MM-DD
- **Fase do ROADMAP:** <n>
- **Specs entregues:** SPEC-XXXX, …
- **Tag git:** `<versão>`

## Destaques

Resumo em 2–4 linhas do que esta versão entrega.

## Adicionado / Alterado / Corrigido

- **Added:** …
- **Changed:** …
- **Fixed:** …

## Migrações

- `V{n}__….sql`: …

## Contratos / API

- OpenAPI: …

## Decisões (decision-log)

- DL-XXXX — … (Confiança / Reversibilidade)

## Como rodar / verificar

```bash
cd backend && ./mvnw verify
docker compose up --build
```

## Riscos e pendências

- …

---

> **Documentação bilíngue (obrigatório).** Ao cortar a release, além deste arquivo pt-BR, **adicione a
> versão ao [`CHANGELOG.en-US.md`](CHANGELOG.en-US.md)** (mirror consolidado em en-US, mais recente no
> topo). As duas faces andam juntas na mesma fatia — ver `CLAUDE.md` (regra de docs bilíngues).
> Relatórios técnicos (specs, ADRs, decision-log, relatórios de fase/teste) seguem **só pt-BR** (Regra Zero).
