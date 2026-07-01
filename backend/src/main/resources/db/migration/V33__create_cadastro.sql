-- SPEC-0031 / ADR-0019 / DL-0115 — Cadastro (dados de referência editáveis).
-- Registry genérico que substitui enums de negócio que NÃO são máquina de estado nem imutáveis por
-- lei. Cada item = um code (= nome do antigo constante do enum) + rótulo pt-BR + ativo + ordem. Ao
-- converter um enum, o valor persistido pelos módulos vira este code — o contrato JSON NÃO muda.
--
-- O code NUNCA é uma FK de outra tabela (Modulith / persistence.md): os módulos guardam o code como
-- VALOR e validam via a porta CadastroValidator. Esta tabela pertence só ao módulo cadastro.

CREATE TABLE cadastro_item (
    id          uuid          PRIMARY KEY,
    type        varchar(40)   NOT NULL,          -- CadastroType (chave técnica do registry)
    code        varchar(60)   NOT NULL,          -- = nome do antigo constante do enum (imutável)
    label       varchar(200)  NOT NULL,          -- rótulo humano (pt-BR), editável
    active      boolean       NOT NULL DEFAULT true,
    sort_order  integer       NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    created_by  varchar(100),
    updated_by  varchar(100),
    version     bigint        NOT NULL DEFAULT 0
);

-- Um code por tipo (BR1). Suporta a validação (type, code) e a listagem por tipo.
CREATE UNIQUE INDEX ux_cadastro_item_type_code ON cadastro_item (type, code);
CREATE INDEX ix_cadastro_item_type_active ON cadastro_item (type, active);

-- Seed dos valores atuais dos enums convertidos na fatia 18a (Admin/Assets/Billing). O code é o
-- nome do enum (contrato inalterado); o label é o rótulo pt-BR. Idempotente por (type, code) — se a
-- migração já rodou, o ON CONFLICT DO NOTHING evita duplicar. created_at/updated_at fixos (epoch de
-- referência do seed): dado de sistema (persistence.md — seeds essenciais via Flyway).
INSERT INTO cadastro_item (id, type, code, label, active, sort_order, created_at, updated_at, created_by)
VALUES
    -- ADMIN_EXPENSE_KIND (era AdminExpenseKind; ramifica para EntryType — DL-0085)
    (gen_random_uuid(), 'ADMIN_EXPENSE_KIND', 'UTILITY',            'Conta de consumo',        true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_EXPENSE_KIND', 'AUTONOMOUS_SERVICE', 'Serviço de autônomo (PF)', true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_EXPENSE_KIND', 'SERVICE',            'Serviço (PJ)',            true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_EXPENSE_KIND', 'OTHER',              'Outros',                  true, 40, now(), now(), 'system'),
    -- ADMIN_RECURRENCE (era AdminRecurrence)
    (gen_random_uuid(), 'ADMIN_RECURRENCE',   'MONTHLY',            'Mensal',                  true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_RECURRENCE',   'YEARLY',             'Anual',                   true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_RECURRENCE',   'OTHER',              'Outra',                   true, 30, now(), now(), 'system'),
    -- ADMIN_SUPPLIER_TYPE (era AdminSupplierType)
    (gen_random_uuid(), 'ADMIN_SUPPLIER_TYPE','UTILITY',            'Concessionária/consumo',  true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_SUPPLIER_TYPE','SOFTWARE',           'Software',                true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_SUPPLIER_TYPE','SERVICE',            'Serviços',                true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'ADMIN_SUPPLIER_TYPE','OTHER',              'Outros',                  true, 40, now(), now(), 'system'),
    -- ASSET_TYPE (era AssetType; SOFTWARE_LICENSE exige expiresAt — regra de domínio SPEC-0021)
    (gen_random_uuid(), 'ASSET_TYPE',         'EQUIPMENT',          'Equipamento',             true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'ASSET_TYPE',         'SOFTWARE_LICENSE',   'Licença de software',     true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'ASSET_TYPE',         'OTHER',              'Outros',                  true, 30, now(), now(), 'system'),
    -- WITHHOLDING_KIND (era WithholdingKind; retenções federais + ISS retido)
    (gen_random_uuid(), 'WITHHOLDING_KIND',   'IRRF',               'IRRF',                    true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'WITHHOLDING_KIND',   'PIS',                'PIS',                     true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'WITHHOLDING_KIND',   'COFINS',             'COFINS',                  true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'WITHHOLDING_KIND',   'CSLL',               'CSLL',                    true, 40, now(), now(), 'system'),
    (gen_random_uuid(), 'WITHHOLDING_KIND',   'ISS_RETIDO',         'ISS retido',              true, 50, now(), now(), 'system'),
    -- TAX_REGIME (era TaxRegime; seleciona a TaxRegimeStrategy — DL-0044)
    (gen_random_uuid(), 'TAX_REGIME',         'SIMPLES_NACIONAL',   'Simples Nacional',        true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'TAX_REGIME',         'LUCRO_PRESUMIDO',    'Lucro Presumido',         true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'TAX_REGIME',         'LUCRO_REAL',         'Lucro Real',              true, 30, now(), now(), 'system')
ON CONFLICT (type, code) DO NOTHING;
