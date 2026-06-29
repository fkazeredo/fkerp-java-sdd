package com.fksoft.domain.accounts;

/**
 * Value object that protects the structural validity of a commercial account's document (BR2): the
 * {@code (legalType, number)} pair, with {@code number} stored as normalized digits. CNPJ/MEI are
 * 14 digits + check digits; CPF is 11 digits + check digits. The invariant is enforced at
 * construction, so an invalid {@code Document} can never exist — independent of any controller
 * validation.
 *
 * @param legalType the legal type that fixes the expected shape
 * @param number the document as digits only (no punctuation)
 */
public record Document(LegalType legalType, String number) {

  private static final int[] CNPJ_WEIGHTS_FIRST = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
  private static final int[] CNPJ_WEIGHTS_SECOND = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

  public Document {
    if (legalType == null || number == null || !isStructurallyValid(legalType, number)) {
      throw new AccountDocumentInvalidException();
    }
  }

  /**
   * Builds a {@link Document} from a raw, possibly punctuated string (e.g. {@code
   * 12.345.678/0001-95}), normalizing it to digits before validating.
   *
   * @throws AccountDocumentInvalidException when the digits do not form a valid document for the
   *     legal type.
   */
  public static Document of(LegalType legalType, String raw) {
    String digits = raw == null ? "" : raw.replaceAll("\\D", "");
    return new Document(legalType, digits);
  }

  private static boolean isStructurallyValid(LegalType legalType, String digits) {
    if (digits.length() != legalType.digitCount() || !digits.chars().allMatch(Character::isDigit)) {
      return false;
    }
    return legalType == LegalType.CPF ? isValidCpf(digits) : isValidCnpj(digits);
  }

  private static boolean isValidCpf(String s) {
    if (allSameDigit(s)) {
      return false;
    }
    int first = cpfCheckDigit(s, 9, 10);
    int second = cpfCheckDigit(s, 10, 11);
    return first == digitAt(s, 9) && second == digitAt(s, 10);
  }

  private static int cpfCheckDigit(String s, int length, int startWeight) {
    int sum = 0;
    for (int i = 0; i < length; i++) {
      sum += digitAt(s, i) * (startWeight - i);
    }
    int remainder = (sum * 10) % 11;
    return remainder == 10 ? 0 : remainder;
  }

  private static boolean isValidCnpj(String s) {
    if (allSameDigit(s)) {
      return false;
    }
    int first = cnpjCheckDigit(s, CNPJ_WEIGHTS_FIRST);
    int second = cnpjCheckDigit(s, CNPJ_WEIGHTS_SECOND);
    return first == digitAt(s, 12) && second == digitAt(s, 13);
  }

  private static int cnpjCheckDigit(String s, int[] weights) {
    int sum = 0;
    for (int i = 0; i < weights.length; i++) {
      sum += digitAt(s, i) * weights[i];
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }

  private static boolean allSameDigit(String s) {
    return s.chars().distinct().count() == 1;
  }

  private static int digitAt(String s, int index) {
    return s.charAt(index) - '0';
  }
}
