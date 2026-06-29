# Manual de Instruções — ERP Acme Travel

> Manual em **português**, para o usuário/operador (não técnico). Descreve **o que o sistema já faz
> hoje**. É atualizado **a cada fatia entregue** (ver o comando *User manual* no `CLAUDE.md`).
>
> **Versão do sistema:** 0.1.0 · **Fase atual:** 0 (Fundação)

## 1. O que é o sistema

O **ERP Acme Travel** é o sistema de gestão comercial e financeira da Acme Travel (uma
representante de marcas de turismo — GSA). Quando pronto, ele vai cuidar de câmbio, comissões,
cotações, reservas, conciliação e dos documentos fiscais das vendas.

**Nesta fase (0 — Fundação), o sistema ainda não tem funcionalidades de negócio.** Esta versão
existe para provar que a "espinha dorsal" do sistema funciona de ponta a ponta: o aplicativo sobe,
conecta no banco de dados e responde. As funcionalidades de negócio começam na **Fase 1**
(cadastro de contas comerciais).

## 2. Como acessar

> A Fase 0 ainda não tem um instalador; o sistema é iniciado por linha de comando. Você precisa ter
> **Docker** e **Node.js** instalados. Os passos abaixo são simples e estão explicados.

**Passo 1 — Iniciar o servidor e o banco de dados.** Na pasta do projeto, execute:

```
docker compose up --build
```

Isso sobe o banco de dados e o servidor da aplicação. Quando aparecer que está "started", o servidor
está pronto em `http://localhost:8080`.

**Passo 2 — Abrir a tela.** Em outra janela de terminal, na pasta `frontend`, execute uma vez
`npm install` e depois:

```
npm start
```

Abra o navegador em **`http://localhost:4200`**.

**Para encerrar:** feche o `npm start` e rode `docker compose down` na pasta do projeto.

## 3. Funcionalidades disponíveis

### Fase 0 — Tela "Saúde do sistema"

É a única tela desta fase. Ao abrir `http://localhost:4200`, o sistema verifica automaticamente se
está tudo no ar e mostra um destes estados:

| Estado | O que você vê | O que significa |
|---|---|---|
| Verificando | **"Verificando…"** | O sistema está consultando se está tudo no ar (alguns instantes). |
| Saudável | **"Sistema saudável"** + Status: `UP` e Banco de dados: `UP` (caixa verde) | Aplicação e banco de dados estão funcionando. |
| Com erro | **"Não foi possível contatar o backend"** + botão **"Tentar novamente"** (caixa vermelha) | O servidor está fora do ar ou inacessível. Verifique se o `docker compose up` está rodando e clique em **Tentar novamente**. |

> Para quem é de TI: a mesma verificação está disponível em `GET http://localhost:8080/api/system/health`,
> que responde `{ "status": "UP", "db": "UP" }`.

## 4. Glossário

- **Backend / servidor:** a parte do sistema que processa as regras e fala com o banco de dados.
- **Banco de dados:** onde as informações ficam guardadas (PostgreSQL).
- **Saúde / health:** uma verificação rápida de que o sistema está no ar e respondendo.

## 5. Histórico de versões do manual

| Versão | Fase | O que mudou no manual |
|---|---|---|
| 0.1.0 | 0 — Fundação | Primeira versão: visão geral, como acessar e a tela "Saúde do sistema". |

> **Próxima fase (1):** o manual ganhará o **cadastro de contas comerciais** (agências/agentes) e,
> em seguida, câmbio, comissões e cotações.
