-- SPEC-0031 / ADR-0019 / DL-0118 — Cadastro fatia 18d (Finance / Payout / People / CommercialPolicy / AfterSales).
-- Fecha a Fase 18: semeia os valores atuais dos últimos enums de referência convertidos como itens
-- do registry cadastro_item. O code é o nome do antigo constante do enum (contrato JSON inalterado);
-- o label é o rótulo pt-BR. Idempotente por (type, code): ON CONFLICT DO NOTHING (nunca editar uma
-- migração já aplicada). Nenhuma FK cross-contexto — os módulos guardam o code como VALOR e validam
-- via CadastroValidator.

INSERT INTO cadastro_item (id, type, code, label, active, sort_order, created_at, updated_at, created_by)
VALUES
    -- ENTRY_TYPE (era EntryType; dirige a postagem AP/AR + o documento exigido pelo Compliance — SPEC-0015/SPEC-0008)
    (gen_random_uuid(), 'ENTRY_TYPE',           'COMMISSION_RECEIVABLE', 'Comissão a receber',          true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'COMMISSION_PAYABLE',    'Comissão a pagar',            true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'PENALTY',               'Multa',                       true, 30,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'UTILITY_EXPENSE',       'Despesa de utilidade',        true, 40,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'AUTONOMOUS_SERVICE',    'Serviço autônomo (RPA)',      true, 50,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'SUPPLIER_SETTLEMENT',   'Liquidação de fornecedor',    true, 60,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'REFUND',                'Reembolso',                   true, 70,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'TAX_PAYABLE',           'Tributo a recolher',          true, 80,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'SERVICE',               'Serviço (PJ)',                true, 90,  now(), now(), 'system'),
    (gen_random_uuid(), 'ENTRY_TYPE',           'OTHER_EXPENSE',         'Outras despesas',             true, 100, now(), now(), 'system'),
    -- PARTY_TYPE (era PartyType; tipo de contraparte do lançamento — SPEC-0015)
    (gen_random_uuid(), 'PARTY_TYPE',           'AGENCY',                'Agência',                     true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'PARTY_TYPE',           'AGENT',                 'Agente',                      true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'PARTY_TYPE',           'SUPPLIER',              'Fornecedor',                  true, 30,  now(), now(), 'system'),
    (gen_random_uuid(), 'PARTY_TYPE',           'OTHER',                 'Outros',                      true, 40,  now(), now(), 'system'),
    -- PAYEE_TYPE (era PayeeType; tipo de favorecido do repasse — SPEC-0017)
    (gen_random_uuid(), 'PAYEE_TYPE',           'AGENT',                 'Agente',                      true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'PAYEE_TYPE',           'SUPPLIER',              'Fornecedor',                  true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'PAYEE_TYPE',           'CUSTOMER',              'Cliente',                     true, 30,  now(), now(), 'system'),
    -- PAYOUT_KIND (era PayoutKind; dirige o fato de liquidação/repasse/reembolso + a armadilha do lojista — DL-0024/DL-0051)
    (gen_random_uuid(), 'PAYOUT_KIND',          'AGENT_COMMISSION',      'Repasse de comissão',         true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'PAYOUT_KIND',          'SUPPLIER_SETTLEMENT',   'Liquidação de fornecedor',    true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'PAYOUT_KIND',          'REFUND',                'Reembolso',                   true, 30,  now(), now(), 'system'),
    -- DISCREPANCY_KIND (era DiscrepancyKind; produzido pelo cálculo da jornada — SPEC-0022/DL-0071)
    (gen_random_uuid(), 'DISCREPANCY_KIND',     'ODD_PUNCH',             'Marcação ímpar',              true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'DISCREPANCY_KIND',     'MISSING_PUNCH',         'Marcação faltante',           true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'DISCREPANCY_KIND',     'INCOHERENT_JOURNAL',    'Jornada incoerente',          true, 30,  now(), now(), 'system'),
    -- PARAMETER_VALUE_TYPE (era ParameterValueType; dirige o parse/validação do valor da regra — SPEC-0014/DL-0037)
    (gen_random_uuid(), 'PARAMETER_VALUE_TYPE', 'NUMBER',                'Número',                      true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'PARAMETER_VALUE_TYPE', 'PERCENT',               'Percentual',                  true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'PARAMETER_VALUE_TYPE', 'MONEY',                 'Valor monetário (BRL)',       true, 30,  now(), now(), 'system'),
    (gen_random_uuid(), 'PARAMETER_VALUE_TYPE', 'BOOL',                  'Booleano (sim/não)',          true, 40,  now(), now(), 'system'),
    -- SUPPORT_CASE_TYPE (era SupportCaseType; seleciona o SLA governado — DL-0052)
    (gen_random_uuid(), 'SUPPORT_CASE_TYPE',    'COMPLAINT',             'Reclamação',                  true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'SUPPORT_CASE_TYPE',    'CHANGE_REQUEST',        'Pedido de alteração',         true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'SUPPORT_CASE_TYPE',    'CANCELLATION_REQUEST',  'Pedido de cancelamento',      true, 30,  now(), now(), 'system'),
    (gen_random_uuid(), 'SUPPORT_CASE_TYPE',    'REFUND_REQUEST',        'Pedido de reembolso',         true, 40,  now(), now(), 'system'),
    (gen_random_uuid(), 'SUPPORT_CASE_TYPE',    'INFO',                  'Informação',                  true, 50,  now(), now(), 'system'),
    -- CASE_RESOLUTION (era CaseResolution; orquestra Payout REFUND / Booking cancel — DL-0054)
    (gen_random_uuid(), 'CASE_RESOLUTION',      'REFUND_APPROVED',       'Reembolso aprovado',          true, 10,  now(), now(), 'system'),
    (gen_random_uuid(), 'CASE_RESOLUTION',      'CANCEL_APPROVED',       'Cancelamento aprovado',       true, 20,  now(), now(), 'system'),
    (gen_random_uuid(), 'CASE_RESOLUTION',      'RESOLVED_NO_ACTION',    'Resolvido sem ação',          true, 30,  now(), now(), 'system'),
    (gen_random_uuid(), 'CASE_RESOLUTION',      'REJECTED',              'Rejeitado',                   true, 40,  now(), now(), 'system')
ON CONFLICT (type, code) DO NOTHING;
