# DL-0087 — AdminContractExpiring por job de relógio controlado (alerta, não bloqueio)

- **Fase:** 8l (Admin)
- **Spec(s):** SPEC-0025 (BR5/Events); relacionada à DL-0063 (Portfolio) e DL-0066 (Assets) — mesmo padrão
- **Data:** 2026-06-30
- **Status:** ASSUMIDO
- **Confiança:** Média
- **Reversibilidade:** Barata

## Lacuna

A BR5 manda "**contrato a vencer** publicar `AdminContractExpiring` (alerta — não bloqueia)" e a spec
diz "alerta de vencimento por **job**", mas não fixa a **antecedência** (horizonte) nem como a
detecção é tornada **determinística/testável** e **idempotente** (não re-alertar o mesmo contrato a
cada varredura).

## Decisão

Replicar o padrão já validado de **relógio controlado** do Portfolio (DL-0063) e Assets (DL-0066):

- O `AdminService.flagExpiringContracts(Instant now)` recebe o **instante de avaliação como
  parâmetro** (não usa `Instant.now()` interno) — determinístico nos testes.
- **Horizonte = 30 dias** (default), igual ao Portfolio/Assets; configurável por propriedade
  (`admin.contract.expiry-horizon-days:30`).
- **Idempotente por contrato:** coluna `expiry_signaled_at` em `admin_contracts`; um contrato só
  publica `AdminContractExpiring` **uma vez** (a varredura seguinte o ignora). Índice parcial sobre
  os candidatos (vigentes, com `valid_until` não nulo, ainda não sinalizados).
- É **alerta**, nunca bloqueio: não impede nada; só publica o evento para governança/Intelligence.
- O agendamento técnico fica em `infra.jobs` (`AdminContractExpiryScheduler`), governado pelo
  Platform (`GovernedJobs`, DL-0076), como os demais schedulers; a **regra** (quais contratos, qual
  horizonte, idempotência) mora no `AdminService`.

## Justificativa

- **Consistência do projeto:** Booking (`expirePendingBookings`), Portfolio
  (`flagExpiringContracts`), Assets (`flagExpiringLicenses`) e Compliance (`flagRetentionExpiring`)
  já usam exatamente este padrão (instante como parâmetro + flag de idempotência). Reusar reduz
  surpresa e custo cognitivo (Regra Zero).
- **`messaging-and-integrations.md` (jobs):** um job importante considera idempotência, histórico e
  correlação — o `expiry_signaled_at` + `GovernedJobs` (lock + janela + `JobRun`) atendem isso.
- **30 dias** é o horizonte que o projeto já adotou para vencimentos (representação e licença); para
  contrato administrativo a antecedência exata é decisão operacional barata de trocar (config).

## Alternativas descartadas

- **`Instant.now()` interno no serviço:** tornaria a regra não-determinística e os testes frágeis —
  contraria o padrão do projeto.
- **Republicar a cada varredura (sem `expiry_signaled_at`):** geraria ruído/duplicidade de alerta;
  a idempotência por flag é o padrão já validado.
- **Bloquear renovação/uso do contrato vencido:** a spec é explícita — é **alerta, não bloqueio**.

## Impacto

- **Specs:** SPEC-0025 BR5 — confirma horizonte 30d, relógio controlado e idempotência.
- **Arquivos:** `AdminService.flagExpiringContracts(now)`; evento `AdminContractExpiring`;
  `AdminContractExpiryScheduler` em `infra.jobs` (registra `JobRun` via `GovernedJobs`).
- **Migração:** coluna `expiry_signaled_at timestamptz` + índice parcial em `admin_contracts`
  (na V30).
- **Contratos:** endpoint ad-hoc `POST /api/admin/contracts/flag-expiring` (como Portfolio/Assets)
  para acionar a varredura manualmente; OpenAPI atualizada.

## Como reverter

Trocar o horizonte é só mudar a propriedade. Trocar a estratégia (ex.: alertar repetidamente, ou
integrar a um calendário externo) é refator **barato** e localizado no `AdminService` + scheduler —
nenhum consumidor depende do formato interno da varredura.
