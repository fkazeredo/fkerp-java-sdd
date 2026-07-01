-- SPEC-0031 / ADR-0019 / DL-0116 — Cadastro fatia 18b (Marketing / Intelligence / Portfolio).
-- Semeia os valores atuais dos enums convertidos nesta fatia como itens do registry cadastro_item.
-- O code é o nome do antigo constante do enum (contrato JSON inalterado); o label é o rótulo pt-BR.
-- Idempotente por (type, code): ON CONFLICT DO NOTHING (nunca editar uma migração já aplicada).
-- Nenhuma FK cross-contexto — os módulos guardam o code como VALOR e validam via CadastroValidator.

INSERT INTO cadastro_item (id, type, code, label, active, sort_order, created_at, updated_at, created_by)
VALUES
    -- CONSENT_PURPOSE (era ConsentPurpose; NEWSLETTER é o propósito cablado do envio — DL-0059)
    (gen_random_uuid(), 'CONSENT_PURPOSE',        'NEWSLETTER',        'Newsletter',              true, 10, now(), now(), 'system'),
    -- MARKETING_SUBJECT_TYPE (era SubjectType; sujeito de consentimento/marketing)
    (gen_random_uuid(), 'MARKETING_SUBJECT_TYPE', 'ACCOUNT',           'Conta (agência)',         true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'MARKETING_SUBJECT_TYPE', 'AGENT',             'Agente',                  true, 20, now(), now(), 'system'),
    -- INSIGHT_SUBJECT_KIND (era SubjectKind; eixo do insight — AGENCY é o produzido em v1, demais são seam)
    (gen_random_uuid(), 'INSIGHT_SUBJECT_KIND',   'AGENCY',            'Agência',                 true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'INSIGHT_SUBJECT_KIND',   'ROUTE',             'Rota',                    true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'INSIGHT_SUBJECT_KIND',   'PRODUCT',           'Produto',                 true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'INSIGHT_SUBJECT_KIND',   'SUPPLIER',          'Fornecedor',              true, 40, now(), now(), 'system'),
    -- INSIGHT_TYPE (era InsightType; tipo de insight do DSS)
    (gen_random_uuid(), 'INSIGHT_TYPE',           'PROMO_FX_ADVISOR',  'Consultor de câmbio (promoção)', true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'INSIGHT_TYPE',           'OVERRIDE_NUDGE',    'Alerta de faixa de comissão',    true, 20, now(), now(), 'system'),
    -- INSIGHT_VERDICT (era Verdict; veredito do PromoFxAdvisor — dirige a guardrail e o texto)
    (gen_random_uuid(), 'INSIGHT_VERDICT',        'CONVERTE',          'Converte (manter)',       true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'INSIGHT_VERDICT',        'QUEIMA_MARGEM',     'Queima margem (apertar)', true, 20, now(), now(), 'system'),
    -- GOAL_METRIC (era GoalMetric; VOLUME←BookingConfirmed, REVENUE←SpreadRealized — DL-0062)
    (gen_random_uuid(), 'GOAL_METRIC',            'VOLUME',            'Volume (vendas)',         true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'GOAL_METRIC',            'REVENUE',           'Receita (spread BRL)',    true, 20, now(), now(), 'system')
ON CONFLICT (type, code) DO NOTHING;
