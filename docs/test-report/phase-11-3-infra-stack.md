# Caderno de testes — Fatia 11-3 · Stack de monitoramento (`infra/`: Prometheus/Loki/Alloy/Grafana)

## Escopo

SPEC-0027 **AC9** (operacional): `docker compose up` sobe app + db + a stack de observabilidade;
métricas/logs no Grafana; datasources e dashboard pré-provisionados. **BR8** (a stack é
configuração/operação, **fora** do `./mvnw verify`).

## O que foi entregue (config + compose, espelhando o fkerp-poc)

- `infra/prometheus/prometheus.yml` — scrape do `prometheus` e do `acme-travel-erp`
  (`app:8080/actuator/prometheus`, label `application=acme-travel-erp`), autenticando no endpoint
  protegido por **Bearer token** (`credentials_file`, DL-0095) + `infra/prometheus/README.md`.
- `infra/loki/loki-config.yml` — Loki single-binary (filesystem, retenção 168h).
- `infra/alloy/config.alloy` — Grafana Alloy lê o socket do Docker e envia os logs dos containers ao
  Loki (o `app` loga JSON/ECS, fatia 11-2).
- `infra/grafana/provisioning/datasources/datasources.yml` — datasources **Prometheus** + **Loki**.
- `infra/grafana/provisioning/dashboards/dashboards.yml` + `infra/grafana/dashboards/acme-travel-erp.json`
  — dashboard **"Acme Travel ERP — Backend Overview"** (JVM memory, HTTP request rate, CPU, e um painel
  de **eventos de negócio** `acme_*_total` que a fatia 11-4 popula).
- `docker-compose.yml` — serviços `prometheus`, `loki`, `alloy`, `grafana` na rede `acme`; volumes
  `loki-data`/`grafana-data`. `.env.example` com portas/credenciais. `.gitignore` ignora o
  `scrape-token` (segredo nunca versionado).

## Resultado (verificação operacional)

- `docker compose config` → **VALID** (compose + todos os mounts).
- `docker compose up -d prometheus loki grafana alloy` → 4 containers **Up**.
- `GET :9090/-/ready` → **prometheus READY**; `GET :9090/api/v1/targets` → jobs `acme-travel-erp` e
  `prometheus` presentes (o alvo do app fica `down` enquanto o app não está no ar — esperado).
- `GET :3000/api/health` → **Grafana UP** (database ok).
- `GET :3000/api/datasources` (admin) → **Prometheus** e **Loki** provisionados.
- `GET :3000/api/search?query=Acme` (admin) → dashboard **"Acme Travel ERP — Backend Overview"**
  (uid `acme-travel-erp-backend`) na pasta **"Acme Travel ERP"**.
- Loki: `Ingester ... ready after 15s` (aquecimento normal). Stack derrubada com `docker compose down -v`.
- **Backend:** `./mvnw verify` permanece **VERDE, 475 testes** (a stack não muda o build — BR8).

## Cobertura / o que NÃO está coberto

- A verificação ponta a ponta com **métricas reais do app no Grafana** depende do `app` no ar e do
  token de raspagem preenchido (fluxo do `infra/prometheus/README.md`); a presença das séries
  técnicas/negócio é coberta pelos testes de integração das fatias 11-1 e 11-4 (no
  `/actuator/prometheus`). Aqui valida-se a **subida e o provisionamento** da stack.
- Limiares de alerta (Alertmanager) são Open Question de operação (fora de escopo, SPEC-0027).

## Como reproduzir

```bash
cp .env.example .env
touch infra/prometheus/scrape-token            # evita o mount como diretório
docker compose up -d                           # sobe app+db+prometheus+loki+alloy+grafana
# Gere o token de raspagem (uma vez) e reinicie o Prometheus:
curl -s localhost:8080/api/identity/login -H 'Content-Type: application/json' \
  -d '{"username":"it","password":"dev12345"}' | jq -r .token > infra/prometheus/scrape-token
docker compose restart prometheus
# Grafana em http://localhost:3000 (admin/admin) → dashboard "Acme Travel ERP — Backend Overview".
docker compose down -v
```
