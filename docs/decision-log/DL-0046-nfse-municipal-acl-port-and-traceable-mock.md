# DL-0046 — Billing: webservice municipal de NFS-e como porta de domínio + adaptador ACL com mock rastreável

- **Fase:** 8c (Billing — SPEC-0016)
- **Spec(s):** SPEC-0016 (BR3 emissão/assinatura/transmissão; BR7 classificação de falha; Scope
  "adaptador ACL"; Persistence "vendor DTO da prefeitura não vaza"; Error Behavior 502/422)
- **ADR relacionado:** 0011, 0012 ; `architecture/messaging-and-integrations.md` (ACL, timeout,
  classificação de falha, validação de resposta) ; `architecture/simulation-and-mocking.md` (mock
  rastreável) ; padrão das Fases 3 (quotation-site) e 6 (point-clock crawler)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Moderada

## Lacuna

A emissão de NFS-e é uma **integração externa** (webservice municipal, padrão ABRASF/Nacional,
assinatura com e-CNPJ). A spec deixa em aberto **município(s) e padrão de NFS-e** (segunda Open
Question) e o webservice real está fora de escopo. Faltava decidir: (a) **onde** mora a integração;
(b) o **contrato de porta** que o domínio enxerga; (c) o **mock rastreável**; (d) a **costura de
assinatura** (e-CNPJ, que pertence ao Platform/SPEC-0023, ainda não construído); (e) a
**classificação de falha** e a fronteira que impede o DTO do vendor de vazar.

## Decisão

1. **Porta de domínio `NfseGateway`** em `com.fksoft.domain.billing` (interface), com operações de
   negócio em **linguagem do domínio** (nunca do vendor):
   - `NfseIssuance issue(NfseIssueRequest request)` — transmite e devolve número + código de
     verificação **ou** uma falha classificada;
   - `void cancel(NfseCancellation cancellation)` — cancela conforme o município (BR6).
   Os tipos `NfseIssueRequest`/`NfseIssuance`/`NfseCancellation` são **do domínio** (Money, ids,
   município, serviceCode, base, ISS) — **não** o XML/SOAP da prefeitura.

2. **Adaptador ACL `infra.integration.nfse`** implementa `NfseGateway`. É o **tradutor**: monta o
   payload externo (uma representação `MunicipalNfseEnvelope` que **simula** o XML ABRASF), chama o
   `CertificateSigner` (assinatura e-CNPJ), "transmite" e **traduz a resposta externa** de volta para
   o tipo de domínio. O **shape externo nunca sai do pacote `infra.integration.nfse`** — garantido por
   uma **regra ArchUnit** (`domain` não depende de `..infra.integration.nfse..`), exatamente como as
   Fases 3 e 6.

3. **Mock rastreável (`SimulatedMunicipalNfseService`)** é o adaptador concreto do POC: fala o
   contrato externo documentado, **gera número/código determinísticos** e permite **injetar falhas**
   (TIMEOUT/REJEITADA/INDISPONÍVEL) por gatilho de teste (à la `SimulatedPointClockSource`). O
   webservice municipal real fica fora de escopo (`simulation-and-mocking.md`): o mock prova a
   tradução ACL e a emissão ponta a ponta sem dependência viva. Tem **timeout** e **log de
   integração** (latência, classe de falha, correlation id — sem dados sensíveis; BR Observability).

4. **Assinatura e-CNPJ via porta `CertificateSigner`** (também em `domain.billing` como port), com um
   adaptador **stub rastreável** em `infra` que referencia a **SPEC-0023 (Platform — custódia do
   e-CNPJ)** como dono futuro. Hoje o stub "assina" (marca o envelope como assinado) sem custodiar
   chave real; **nunca loga certificado/credenciais** (security.md / Error Behavior). Quando o
   Platform existir, troca-se o adaptador sem mexer no domínio.

5. **Classificação de falha (BR7)** com o enum `NfseFailureClass { TIMEOUT, UNAVAILABLE, REJECTED }`
   mapeado de `messaging-and-integrations.md`. A rejeição da prefeitura (REJECTED) → **422**
   (`billing.municipality.rejected`, com motivo); TIMEOUT/UNAVAILABLE → **502**
   (`billing.nfse.webservice-failure`). **Nunca** "emitida" falsa: só vira EMITIDA com número/código
   **validados** presentes (response validation).

## Justificativa

- **messaging-and-integrations.md** é categórico: integração externa atrás de ACL, vendor DTO não
  vaza, timeout obrigatório, falha classificada, resposta validada. A porta `NfseGateway` +
  adaptador + mock é o mesmo padrão já aprovado nas Fases 3 e 6 (consistência com o repo).
- **`simulation-and-mocking.md`** autoriza o mock rastreável que fala o contrato real e referencia a
  spec do serviço externo — em vez de lógica falsa em produção.
- **`CertificateSigner` como porta** isola a peça que é do **Platform (SPEC-0023)**: a SPEC-0016
  diz que a assinatura é "porta do Platform". Modelar a porta agora (com stub) evita inventar custódia
  de certificado fora de escopo e deixa a costura pronta.

## Alternativas descartadas

- **Chamar o webservice real / montar SOAP de verdade.** Descartada: webservice municipal real está
  fora de escopo; exigiria credenciais/ICP-Brasil e um município concreto (Open Question aberta).
- **Devolver o XML/DTO do vendor para o serviço de domínio decidir.** Descartada: vazaria o shape
  externo no domínio (proibido; quebraria a regra ArchUnit). O adaptador **traduz** e só o tipo de
  domínio sai.
- **Assinar embutido no Billing (sem porta).** Descartada: a assinatura/custódia é do Platform; sem
  porta, trocar pelo Platform real seria refator no domínio.

## Impacto

- **Specs:** SPEC-0016 — BR3/BR7 concretizadas; a 2ª Open Question (município/padrão NFS-e)
  permanece aberta como **parâmetro do adaptador** (município/serviceCode são entrada; o padrão
  ABRASF é simulado), registrada aqui.
- **Arquivos:** `domain.billing` ports `NfseGateway`, `CertificateSigner` + tipos de domínio
  (`NfseIssueRequest`, `NfseIssuance`, `NfseFailureClass`, `NfseTransmissionException`);
  `infra.integration.nfse` (`SimulatedMunicipalNfseService`, `MunicipalNfseEnvelope` externo,
  `StubECnpjCertificateSigner`); **nova regra ArchUnit** "domínio não depende de
  `infra.integration.nfse`".
- **Contratos:** falha → 502/422 conforme classe; sem mudança em outros módulos.

## Como reverter

Reversão **moderada**: remover o pacote `infra.integration.nfse` e as portas; o resto do Billing
(cálculo de imposto, persistência) sobrevive como rascunho sem emissão. A regra ArchUnit é aditiva
(remover é seguro). Trocar o mock pelo serviço real é **substituir o adaptador** — o domínio não muda.
Quando o Platform (SPEC-0023) custodiar o e-CNPJ, troca-se o `CertificateSigner` stub pelo real.
