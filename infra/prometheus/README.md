# Prometheus — token de raspagem (scrape token)

O endpoint `/actuator/prometheus` do backend exige `ROLE_IT` (SPEC-0027 / DL-0095). Para o Prometheus
raspar as métricas, gere um Bearer token de um usuário com esse papel e salve-o em
`infra/prometheus/scrape-token` (este arquivo **não é versionado** — está no `.gitignore`).

> **Antes do primeiro `docker compose up`:** crie o arquivo (ainda que vazio) para o Docker montá-lo
> como **arquivo** e não como diretório: `touch infra/prometheus/scrape-token`. Com o arquivo vazio,
> o alvo aparece como `down` no Grafana até você preenchê-lo (abaixo) — sem quebrar a subida da stack.

Com a stack no ar (`docker compose up`), gere o token uma vez:

```bash
curl -s localhost:8080/api/identity/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"it","password":"dev12345"}' | jq -r .token > infra/prometheus/scrape-token
docker compose restart prometheus   # reler o token
```

> O usuário `it` (papel `ROLE_IT`, senha `dev12345`) é um seed de **dev/test** (`DevUserSeeder`),
> presente apenas nos perfis `dev`/`test` — nunca em produção. Em produção, use um service
> account/token de longa duração e/ou restrinja a raspagem por rede (DL-0095). O token expira
> conforme `identity.jwt.ttl-seconds` (8h por padrão); ao expirar, o alvo aparece como `down` no
> Grafana — regenere o arquivo. Nenhum segredo é versionado.
