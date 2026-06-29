# Plano — Sub-fase 8c: Billing (SPEC-0016) — NFS-e de comissão, ISS e retenções

> Modo autônomo (RUN-PHASE, FASE-ALVO=8, **escopo SPEC-0016 apenas**). Novo módulo **folha**
> `com.fksoft.domain.billing` (13º módulo Modulith). Emite a **Nota Fiscal de Serviço (NFS-e) sobre a
> comissão** — a receita real da Acme —, calcula **ISS** (e retenções, parametrizado por regime), assina
> com e-CNPJ (porta), transmite ao webservice municipal (ACL com mock rastreável), arquiva o documento
> no **Compliance** (SPEC-0008) e lança o tributo no **Finance** (SPEC-0015) por evento. **Base = só a
> comissão**, nunca o pacote (OVERVIEW 3.2/7.7; BR1). Não toca nenhum outro SPEC.

## Objetivo (Acceptance Criteria da SPEC-0016)

1. Emitir a NF da comissão de **R$ 405** calcula **ISS**, transmite, retorna número/código e **arquiva
   o XML no cofre** (Compliance).
2. **Não é possível emitir duas NFs para a mesma comissão** (idempotência, BR4).
3. `./mvnw verify` verde (ArchUnit + Spring Modulith + Spotless + Checkstyle).

## Decisões registradas ANTES do código (decision-log)

| DL | Lacuna | Decisão | Conf. | Rev. |
|---|---|---|---|---|
| **DL-0044** | **Q7 — regime tributário / quem emite** | **Simples Nacional** default; emitente = Acme; ISS/retenções parametrizados por regime+município atrás de `TaxRegimeStrategy` trocável | **Baixa** | **Cara** |
| DL-0045 | Onde mora Billing; referência ao lançamento; base tributável | Novo módulo folha `domain.billing`; `CommissionInvoice`; **base = comissão** (sem campo de pacote); ref por id (valor), sem FK | Alta | Moderada |
| DL-0046 | NFS-e externa: modelagem/assinatura/falha | Porta `NfseGateway` + adaptador ACL **mock rastreável** em `infra.integration.nfse`; assinatura via porta `CertificateSigner` (stub → SPEC-0023); falha classificada (TIMEOUT/UNAVAILABLE→502, REJECTED→422) | Média | Moderada |
| DL-0047 | Idempotência de emissão; lançamento no Finance; arquivamento | UNIQUE parcial por comissão; orquestrador `infra` arquiva no Compliance síncrono (devolve `documentId`); Finance lança o tributo consumindo `CommissionInvoiceIssued` idempotente; `billing` folha → grafo acíclico | Média | Moderada |

## Fronteira / Spring Modulith (acíclico)

- **`domain.billing` é folha:** depende só dos kernels (`money`, `error`) e das suas portas
  (`NfseGateway`, `CertificateSigner`). **Não importa** `finance` nem `compliance`.
- **Finance → Billing:** novo listener `CommissionInvoiceEventsListener` em `finance.internal` consome
  `CommissionInvoiceIssued` (igual a `finance → booking` do DL-0041). Sem ciclo.
- **Orquestração de emissão e arquivamento** em `infra.integration.nfse` (`BillingIssuanceService`):
  chama `BillingService` (domínio), `NfseGateway` (porta), `ComplianceService.upload` (fachada). Infra
  é isento da regra de ciclo entre módulos de domínio (infra → domínio é permitido) — mesmo padrão do
  `AfdIngestionService` (Fase 6).
- **ArchUnit novo:** `domain` não depende de `..infra.integration.nfse..` (o vendor DTO da prefeitura
  não vaza — ACL), análogo às regras das Fases 3 e 6. O teste-com-dentes planta a violação e falha.

## Fatias (ordem de dependência)

### Slice 8c-1 — Cálculo de tributos + agregado `CommissionInvoice` (rascunho) · `feature/slice-8c1-billing-tax`
- **Teste vermelho (unitário, números exatos, HALF_UP):**
  - Simples default: base R$ 405,00 → ISS = 5% × 405 = **R$ 20,25**; município `3550308` (2%) → **R$ 8,10**.
  - **Regressão BR1:** a base é a **comissão** (405), nunca o pacote (2.700) — um teste falha se a base virar o bruto.
  - **Swap da estratégia:** plugar um stub "Presumido" (IRRF 1,5%) muda o resultado sem tocar o agregado/serviço.
  - Criar rascunho: `CommissionInvoice` nasce `RASCUNHO` com base = comissão; transição inválida lança exceção.
- **Implementar:** módulo `domain.billing`; `Money`-based `TaxAssessment`/`Withholding`/`TaxRegime`;
  porta `TaxRegimeStrategy` + `SimplesNacionalTaxStrategy`; agregado `CommissionInvoice` + `internal`
  repo; `InvoiceStatus`; exceções + i18n; **V20** (tabelas `commission_invoices`, `municipal_iss_rates` seed).
- **Verde → portões → merge `--no-ff` em develop → push.**

### Slice 8c-2 — ACL NFS-e (porta + mock rastreável) + emissão idempotente + arquivamento + evento Finance · `feature/slice-8c2-nfse-issuance`
- **Teste vermelho (integração, Testcontainers + prefeitura fake):**
  - Emitir → número/código retornados, status `EMITIDA`, **Document NFSE arquivado** no Compliance e
    **anexado** ao lançamento de comissão; **ISS lançado** no Finance (PAYABLE `TAX_PAYABLE`).
  - **Idempotência (BR4):** reemitir a mesma nota → **um** número, **um** documento, **um** lançamento.
  - **Falha do webservice (BR7):** REJECTED → 422 classificado (sem "emitida" falsa); TIMEOUT → 502.
  - **Regressão de ouro (Tests Required):** a NF emitida **satisfaz o `DocumentRequirement`** do
    lançamento de comissão → o mês passa a poder fechar (falha antes, passa depois).
- **Implementar:** portas `NfseGateway`/`CertificateSigner`; `infra.integration.nfse`
  (`SimulatedMunicipalNfseService` com injeção de falha, `MunicipalNfseEnvelope` externo,
  `StubECnpjCertificateSigner`, `BillingIssuanceService` orquestrador); evento
  `CommissionInvoiceIssued`; `CommissionInvoiceEventsListener` em `finance.internal`; novo
  `EntryType.TAX_PAYABLE`; índice parcial UNIQUE; **ArchUnit** vendor-DTO-não-vaza.
- **Verde → portões → merge → push.**

### Slice 8c-3 — API REST + cancelamento + OpenAPI · `feature/slice-8c3-billing-api`
- **Teste vermelho (integração API):**
  - `POST /api/billing/invoices` → 201 RASCUNHO; `POST /{id}/issue` → 200 EMITIDA com número/ISS/documentId;
    `POST /{id}/cancel` → 200 CANCELADA (chama `NfseGateway.cancel`, publica `CommissionInvoiceCancelled`,
    libera a comissão para reemissão); `GET /{id}` → 200 | 404 `billing.invoice.not-found`;
    reemitir sem cancelar → 409 `billing.invoice.already-issued`.
- **Implementar:** `BillingController` + DTOs; mapeamentos no `HttpErrorMapping`; OpenAPI; i18n pt-BR + fallback.
- **Verde → portões → merge → push.**

## Definition of Done (por fatia e no fim)

- Código bate a spec; SPEC-0016 atualizada (Q7 → ASSUMIDO ver DL-0044; BR concretizadas).
- Testes por tipo (unit tributo/agregado; integração emissão/idempotência/falha/arquivo/Finance;
  regressão base=comissão e DocumentRequirement). Caderno em `docs/test-report/`.
- **V20** Flyway (idempotente; nunca editar migração aplicada). OpenAPI + i18n. Erro global respeitado.
- Todo `DomainException` registrado no `HttpErrorMapping` (teste de completude verde).
- `./mvnw spotless:apply` antes de `verify`; ArchUnit + Spring Modulith **verdes e acíclicos**.
- Observabilidade: emissão/cancelamento como evento de negócio + log de integração com a prefeitura
  (latência, classe de falha, correlation id), **sem** logar certificado/credenciais.

## Fora de escopo (deferido, sem dívida)

- Regimes Presumido/Real **completos** (só a costura + Simples real — DL-0044); webservice municipal
  real (mock rastreável — DL-0046); custódia real do e-CNPJ (porta → SPEC-0023 Platform); estorno
  contábil da nota cancelada no Finance (fluxo do Finance, fora da SPEC-0016); NF-e de mercadoria;
  SPED/obrigações acessórias. A 2ª Open Question da spec (município/padrão NFS-e) permanece como
  parâmetro do adaptador (registrada em DL-0046).
