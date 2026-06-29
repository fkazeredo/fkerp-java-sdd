# DL-0037 — Modelagem da `ParameterRule` e motor de precedência (camada × especificidade de escopo)

- **Fase:** 8a (CommercialPolicy)
- **Spec(s):** SPEC-0014 (BR1 atributos da regra; BR2 `resolve` por maior precedência + proveniência;
  BR3 especificidade dentro da mesma camada; BR4 sempre há SYSTEM_DEFAULT; redesenho 7.3)
- **ADR relacionado:** 0011 (exceções = chave i18n), 0012 (camadas), 0014 (ordem dos módulos)
- **Data:** 2026-06-29
- **Status:** ASSUMIDO
- **Confiança:** Alta
- **Reversibilidade:** Moderada

## Lacuna

A SPEC-0014 fixa a **ordem das camadas** (`DIRECTIVE > PROMOTION > CONTRACT > POLICY >
SYSTEM_DEFAULT`) e que escopo mais específico vence **dentro** da mesma camada (produto > agência >
global), mas **não fixa**: (a) como representar o *matcher de escopo* na tabela e a ordem de
desempate exata; (b) o desempate determinístico quando duas regras da mesma camada **e** mesma
especificidade estão vigentes; (c) a tipagem do valor; (d) onde mora o módulo.

## Decisão

1. **Agregado único `ParameterRule`** (uma linha = uma regra parametrizada), módulo
   `com.fksoft.domain.commercialpolicy` (graduando o stub da Fase 1 — **não** cria módulo novo; o
   12º Modulith continua o mesmo). Entidade em `…commercialpolicy.internal`; o domínio expõe a
   resolução por **serviço/porta**, nunca a entidade.
2. **Matcher de escopo por colunas explícitas** (não jsonb): `scope_account_id uuid null`,
   `scope_product_ref varchar null`, `scope_channel varchar null`. Forma pequena e conhecida — jsonb
   seria overengineering (mesma postura de V8/V17, Rule Zero).
3. **Especificidade = nº de dimensões de escopo casadas e não-nulas** (`specificity` ∈ {0..3}). Uma
   regra casa um pedido `resolve(key, scope)` quando **toda** dimensão não-nula da regra é igual à do
   pedido (dimensão nula da regra = curinga "qualquer"). `specificity` = quantas dimensões a regra
   fixa. Global = 0; por agência **ou** produto = 1; etc. Mais específico vence **dentro da camada**
   (BR3).
4. **Ordenação total de precedência** (determinística, BR2):
   `(layer.rank ASC) → (specificity DESC) → (validFrom DESC) → (createdAt DESC) → (id ASC)`.
   `layer.rank`: DIRECTIVE=0 … SYSTEM_DEFAULT=4 (0 vence). `validFrom`/`createdAt`/`id` quebram
   empates de forma determinística: **a regra mais recente e a mais nova vencem** entre pares de
   mesma camada e mesma especificidade (a última palavra do diretor/curador prevalece). `id` é o
   desempate final absoluto (nunca há empate).
5. **`value` tipado** por `value_type ∈ {NUMBER, PERCENT, MONEY, BOOL}` guardado como texto
   (`value_text`) + `value_type`. O motor entrega `ResolvedParameter{key, value(texto), type,
   provenance}`; cada consumidor (markup, drift, conciliação) interpreta segundo o tipo — sem
   `Object` solto no domínio.
6. **Vigência:** `valid_from date NOT NULL`, `valid_until date NULL`. Ativa quando
   `validFrom ≤ today ≤ validUntil` (ou `validUntil` nulo = sem fim). Comparada em `LocalDate` no
   fuso do sistema via `Clock` (consistente com o resto do projeto).
7. **`resolve` é consulta pura/Open-Host** (BR6): não publica evento nem muta nada; só a
   **criação** de regra/diretiva muta e audita.

## Justificativa

- **BR2/BR3** pedem precisamente "maior precedência por camada, mais específico dentro da camada,
  proveniência". A tupla de ordenação acima é a tradução direta e **testável** dessa frase, com
  desempate determinístico explícito (a spec exige "tie-break determinístico").
- **Colunas vs jsonb:** o conjunto de dimensões de escopo é fixo e pequeno (conta/produto/canal) —
  o projeto já rejeitou jsonb por Rule Zero em casos análogos (V8, V17).
- **Texto + tipo** evita um modelo polimórfico de valor (uma tabela por tipo) que seria
  overengineering para 3-4 chaves; mantém o domínio sem `Object`.

## Alternativas descartadas

- **`scope` como jsonb/matcher genérico.** Descartada: overengineering; as três dimensões cobrem o
  redesenho (global/agência/produto/canal) e mantêm índice/consulta simples.
- **Especificidade por "ranking" hardcoded (produto sempre > agência).** Descartada em favor de
  **contagem de dimensões**: é mais simples, determinística e cobre combinações (agência+produto)
  sem tabela de prioridade. Onde duas regras de mesma contagem casam (ex.: só-agência vs só-produto),
  o desempate por `validFrom`/`createdAt`/`id` resolve sem ambiguidade.
- **Módulo novo `policy`.** Descartada: o módulo `commercialpolicy` já existe (stub Fase 1); graduar
  no lugar mantém o contrato `MarkupProvider` e o nº de módulos Modulith.

## Impacto

- Migração **V18** `create_commercial_policy.sql`: tabela `parameter_rules` + índice
  `(parameter_key, layer)` + seed dos `SYSTEM_DEFAULT` (DL-0039).
- Domínio: `ParameterRule` (entity, internal), `ParameterLayer`/`ParameterValueType` (enums com
  comportamento: rank e parsing), `ParameterKey`/`ParameterScope`/`ResolvedParameter`/`Provenance`
  (value objects), `ParameterRepository`, `CommercialPolicyService` (resolve + create).
- Testes unitários com **fixtures exatas** de cada camada e especificidade (BR2/BR3) + fallback.
- Sem FK cross-contexto: `scope_account_id`/`scope_product_ref` são **valores** copiados do pedido.

## Como reverter

Reversão **moderada**: a tupla de ordenação e o cálculo de especificidade vivem num único método do
serviço de resolução + a entidade. Trocar o desempate ou a representação de escopo é mudar esse
método e a migração (nova `V`); o contrato externo (`/resolve`) e a porta `MarkupProvider` ficam
estáveis, então o raio do refactoring é interno ao módulo.
