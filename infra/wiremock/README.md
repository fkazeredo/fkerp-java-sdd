# Emulador de integração — NFS-e municipal (dev)

Emulador HTTP **real** do webservice de NFS-e municipal, para rodar o adaptador HTTP em
**desenvolvimento** (não só nos testes). SPEC-0016 / Fase 19e / DL-0127.

## Subir o emulador (opt-in)

O serviço fica atrás do profile `emulators` no `docker-compose.yml`, então o `docker compose up`
padrão continua leve. Para subir com o emulador:

```bash
docker compose --profile emulators up
```

O WireMock sobe em `http://localhost:8090` (porta configurável por `NFSE_EMULATOR_PORT`) e carrega
os stubs de `infra/wiremock/mappings/`.

## Apontar o app para o emulador

Rode o backend com o **adaptador HTTP** (o default é o mock in-process `simulated`):

```bash
BILLING_NFSE_ADAPTER=http \
BILLING_NFSE_BASE_URL=http://nfse-emulator:8080 \
docker compose --profile emulators up app nfse-emulator db
```

Rodando o app fora do Docker (contra o emulador publicado em 8090):

```bash
BILLING_NFSE_ADAPTER=http BILLING_NFSE_BASE_URL=http://localhost:8090 ./mvnw -pl backend spring-boot:run
```

## Comportamentos (fault injection por código de município)

Os stubs decidem a resposta pelo campo `municipalityCode` do corpo:

| `municipalityCode` | Resposta do emulador | Classe de falha no adaptador |
|---|---|---|
| `REJECT` | 422 + motivo | `REJECTED` (não retenta) |
| `TIMEOUT` | 200 após 15 s (> read-timeout) | `TIMEOUT` (retenta, depois abre o breaker) |
| qualquer outro | 200 + número/código gerados | sucesso → `NfseIssuance` |

Assim dá para exercitar, **em dev**, os mesmos caminhos que os testes cobrem
(`MunicipalNfseHttpAdapterTest`): sucesso, rejeição de negócio, timeout, disjuntor.

> O webservice municipal real (XML/SOAP, e-CNPJ, homologação) segue Open Question (credenciais do
> cliente). Este emulador prova o adaptador HTTP + resiliência sem depender de um município vivo; o
> mesmo padrão gradua os demais mocks (portais/GDS/gateway/newsletter/ponto) — seam documentado.
