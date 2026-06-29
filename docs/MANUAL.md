# Manual de Instruções — ERP Acme Travel

> Manual em **português**, para o usuário/operador (não técnico). Descreve **o que o sistema já faz
> hoje**. É atualizado **a cada fatia entregue** (ver o comando *User manual* no `CLAUDE.md`).
>
> **Versão do sistema:** 0.4.0 (em construção) · **Fase atual:** 3 (Primeira integração real — ACL)

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

### Fase 3 — Procedência da oferta (de onde a venda veio)

A partir desta fase o sistema registra **de onde uma oferta veio** — o que o redesenho chama de
*Sourcing*. Isso vale tanto para a venda digitada à mão (um preço de um site externo, de um catálogo
de papel ou de um pedido por telefone) quanto para a cotação que chega automaticamente do **site de
cotação** (ver abaixo).

**Registrar uma oferta manualmente.** O operador pode anotar a procedência de uma oferta informando:

- **Descrição do produto** (texto livre — por exemplo, "City Tour Rio - dia inteiro"). Não precisa
  estar num catálogo estruturado: texto livre é uma oferta válida.
- **Preço-base** (valor e moeda).
- **Origem:** Portal próprio, Site externo, Catálogo de terceiros, ou Demanda crua (pedido avulso).
- **Nível de integração:** Nenhum, Entra (o sistema externo alimenta o ERP) ou Dois sentidos.
- **Referência externa** (opcional): por exemplo, o número da cotação no site de origem.

> Para quem é de TI: `POST /api/sourcing/offers` registra a oferta e `GET /api/sourcing/offers/{id}`
> consulta. É um registro de **rastreabilidade** (de onde veio a venda); ele não calcula preço.

## 4. Glossário

- **Backend / servidor:** a parte do sistema que processa as regras e fala com o banco de dados.
- **Banco de dados:** onde as informações ficam guardadas (PostgreSQL).
- **Saúde / health:** uma verificação rápida de que o sistema está no ar e respondendo.
- **Procedência / Sourcing:** o registro de **de onde** uma oferta veio (portal próprio, site
  externo, catálogo, pedido avulso) e do quanto ela é integrada.

## 5. Histórico de versões do manual

| Versão | Fase | O que mudou no manual |
|---|---|---|
| 0.1.0 | 0 — Fundação | Primeira versão: visão geral, como acessar e a tela "Saúde do sistema". |
| 0.4.0 | 3 — Integração | Procedência da oferta (*Sourcing*): registro manual de oferta e cotação automática vinda do site de cotação (ramo INTEGRADO). |

> **Próxima fase (4):** política de cancelamento como objeto e a armadilha do *merchant of record*.
