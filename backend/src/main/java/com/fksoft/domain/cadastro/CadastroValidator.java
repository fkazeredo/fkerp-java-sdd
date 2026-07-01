package com.fksoft.domain.cadastro;

/**
 * Public cross-module port of the Cadastro module (SPEC-0031 BR3/BR7; ADR-0019/DL-0115): lets a
 * business module (Admin/Assets/Billing, and later 18b–18d) validate that a reference {@code code}
 * is a known, active value of a {@link CadastroType} before persisting it. The {@code code} crosses
 * the boundary as a <strong>value</strong> — there is no cross-context FK; the dependency direction
 * is {@code caller → cadastro} (cadastro is a leaf), keeping the module graph acyclic.
 *
 * <p>This is a <strong>validation</strong>, not a lookup for branching: domain logic that branches
 * on specific codes keeps its own small {@code *Codes} constants (DL-0115). The set of valid,
 * active codes is owned by the cadastro; behavior wired to specific codes stays in the owning module.
 */
public interface CadastroValidator {

  /**
   * Validates that {@code code} is an active item of {@code type} (BR3). No-op when valid.
   *
   * @param type the cadastro type the code must belong to
   * @param code the reference code (case-insensitive; upper-cased internally)
   * @throws CadastroCodeInvalidException when the code is missing, unknown or inactive for the type
   */
  void validate(CadastroType type, String code);

  /**
   * Whether {@code code} is an active item of {@code type} — the non-throwing form, for callers that
   * prefer to compose their own error.
   *
   * @param type the cadastro type
   * @param code the reference code
   * @return {@code true} when an active item exists for the type/code
   */
  boolean isValid(CadastroType type, String code);
}
