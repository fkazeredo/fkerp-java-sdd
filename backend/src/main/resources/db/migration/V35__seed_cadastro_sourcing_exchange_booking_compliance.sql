-- SPEC-0031 / ADR-0019 / DL-0117 — Cadastro fatia 18c (Sourcing / Exchange / Booking / Compliance).
-- Semeia os valores atuais dos enums convertidos nesta fatia como itens do registry cadastro_item.
-- O code é o nome do antigo constante do enum (contrato JSON inalterado); o label é o rótulo pt-BR.
-- Idempotente por (type, code): ON CONFLICT DO NOTHING (nunca editar uma migração já aplicada).
-- Nenhuma FK cross-contexto — os módulos guardam o code como VALOR e validam via CadastroValidator.

INSERT INTO cadastro_item (id, type, code, label, active, sort_order, created_at, updated_at, created_by)
VALUES
    -- OFFER_ORIGIN (era OfferOrigin; procedência da oferta sourced — SPEC-0009)
    (gen_random_uuid(), 'OFFER_ORIGIN',        'PORTAL_API',            'Portal integrado (API)',      true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'OFFER_ORIGIN',        'EXTERNAL_SITE',         'Site externo',                true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'OFFER_ORIGIN',        'THIRD_PARTY_CATALOG',   'Catálogo de terceiro',        true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'OFFER_ORIGIN',        'RAW_DEMAND',            'Demanda avulsa',              true, 40, now(), now(), 'system'),
    -- INTEGRATION_LEVEL (era IntegrationLevel; INBOUND cabla o ramo INTEGRATED do quoting — DL-0018)
    (gen_random_uuid(), 'INTEGRATION_LEVEL',   'NONE',                  'Sem integração',              true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'INTEGRATION_LEVEL',   'INBOUND',               'Entrada (feed do ERP)',       true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'INTEGRATION_LEVEL',   'BIDIRECTIONAL',         'Bidirecional',                true, 30, now(), now(), 'system'),
    -- MARKET_RATE_SOURCE (era MarketRateSource; origem da observação de câmbio — DL-0025)
    (gen_random_uuid(), 'MARKET_RATE_SOURCE',  'FEED',                  'Feed (provedor externo)',     true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'MARKET_RATE_SOURCE',  'MANUAL',                'Manual (contingência)',       true, 20, now(), now(), 'system'),
    -- CHARGE_KIND (era ChargeKind; obrigação de cancelamento/no-show — cabla a postagem AP/AR)
    (gen_random_uuid(), 'CHARGE_KIND',         'PENALTY',               'Multa (penalidade)',          true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'CHARGE_KIND',         'SUPPLIER',              'Custo do fornecedor',         true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'CHARGE_KIND',         'CUSTOMER_REFUND',       'Reembolso ao cliente',        true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'CHARGE_KIND',         'NO_SHOW',               'No-show',                     true, 40, now(), now(), 'system'),
    -- CANCELLATION_TYPE (era CancellationType; dirige janelas de multa + a armadilha do lojista — DL-0024/DL-0010)
    (gen_random_uuid(), 'CANCELLATION_TYPE',   'STANDARD',              'Padrão (por janela)',         true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'CANCELLATION_TYPE',   'ALL_SALES_FINAL',       'Venda sem reembolso',         true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'CANCELLATION_TYPE',   'CUSTOM',                'Personalizada',               true, 30, now(), now(), 'system'),
    -- DOCUMENT_TYPE (era DocumentType; dirige retenção legal + requisitos — SPEC-0008)
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'NFE',                   'NF-e',                        true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'NFSE',                  'NFS-e',                       true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'RPA',                   'RPA',                         true, 30, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'UTILITY_BILL',          'Conta de consumo',            true, 40, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'LOAN_CONTRACT',         'Contrato de empréstimo',      true, 50, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'COMMISSION_INVOICE',    'NF de comissão',              true, 60, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'PAYMENT_PROOF',         'Comprovante de pagamento',    true, 70, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'REFUND_PROOF',          'Comprovante de reembolso',    true, 80, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'PAYROLL',               'Folha de pagamento',          true, 90, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'TIME_RECORD_AFD',       'Registro de ponto (AFD)',     true, 100, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'PROCESSED_JOURNAL_AEJ', 'Jornada processada (AEJ)',    true, 110, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'VOUCHER',               'Voucher',                     true, 120, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'REPRESENTATION_CONTRACT', 'Contrato de representação', true, 130, now(), now(), 'system'),
    (gen_random_uuid(), 'DOCUMENT_TYPE',       'OTHER',                 'Outros',                      true, 140, now(), now(), 'system'),
    -- SIGNED_FORMAT (era SignedFormat; formato do artefato assinado — SPEC-0008 BR3)
    (gen_random_uuid(), 'SIGNED_FORMAT',       'CAdES_P7S',             'CAdES (.p7s)',                true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'SIGNED_FORMAT',       'XADES',                 'XAdES',                       true, 20, now(), now(), 'system'),
    (gen_random_uuid(), 'SIGNED_FORMAT',       'PADES',                 'PAdES',                       true, 30, now(), now(), 'system'),
    -- REQUIREMENT_PHASE (era RequirementPhase; AT_REGISTRATION dirige o close-check — DL-0012)
    (gen_random_uuid(), 'REQUIREMENT_PHASE',   'AT_REGISTRATION',       'No registro',                 true, 10, now(), now(), 'system'),
    (gen_random_uuid(), 'REQUIREMENT_PHASE',   'AT_SETTLEMENT',         'Na liquidação',               true, 20, now(), now(), 'system')
ON CONFLICT (type, code) DO NOTHING;
