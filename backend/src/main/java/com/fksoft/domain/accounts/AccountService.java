package com.fksoft.domain.accounts;

import com.fksoft.domain.accounts.internal.Account;
import com.fksoft.domain.accounts.internal.AccountRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the accounts module (SPEC-0002): registers, fetches and lists commercial
 * accounts, and implements the {@link AccountDirectory} port for other modules. Money is never
 * computed here (BR6). The audit actor is resolved by the delivery layer (which owns {@code
 * UserContext}) and passed in, keeping the domain free of infra dependencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService implements AccountDirectory {

  private final AccountRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Registers a new account. Validates the document's check digits via the {@link Document} value
   * object (BR2), enforces document uniqueness (BR3) and starts the account {@code ACTIVE} (BR4).
   *
   * @param legalType the legal type (BR1)
   * @param rawDocument the document as typed (punctuation allowed; normalized to digits)
   * @param displayName non-empty commercial display name (BR4)
   * @param cadastur optional CADASTUR registration, stored as provided (BR5)
   * @param iata optional IATA registration, stored as provided (BR5)
   * @param actor the user performing the action (audit), resolved by delivery
   * @return the registered account view
   * @throws AccountDocumentInvalidException when the document is invalid for its type (BR2)
   * @throws AccountDocumentDuplicateException when the document is already registered (BR3)
   */
  @Transactional
  public AccountView register(
      LegalType legalType,
      String rawDocument,
      String displayName,
      String cadastur,
      String iata,
      String actor) {
    Document document = Document.of(legalType, rawDocument);
    if (repository.existsByLegalTypeAndDocumentNumber(document.legalType(), document.number())) {
      throw new AccountDocumentDuplicateException();
    }
    Account account =
        Account.register(
            document,
            displayName.trim(),
            trimToNull(cadastur),
            trimToNull(iata),
            clock.instant(),
            actor);
    try {
      repository.saveAndFlush(account);
    } catch (DataIntegrityViolationException duplicate) {
      throw new AccountDocumentDuplicateException();
    }
    events.publishEvent(
        new AccountRegistered(account.id(), account.legalType(), account.createdAt()));
    log.info(
        "AccountRegistered accountId={} legalType={} document={}",
        account.id(),
        account.legalType(),
        mask(account.documentNumber()));
    return toView(account);
  }

  /**
   * Fetches an account by id.
   *
   * @throws AccountNotFoundException when no account has that id (BR7)
   */
  @Transactional(readOnly = true)
  public AccountView getById(UUID id) {
    return repository
        .findById(id)
        .map(AccountService::toView)
        .orElseThrow(AccountNotFoundException::new);
  }

  /**
   * Lists accounts with optional {@code status} and {@code document} filters (empty page if none).
   */
  @Transactional(readOnly = true)
  public Page<AccountView> list(AccountStatus status, String documentFilter, Pageable pageable) {
    return repository
        .search(status, normalizeFilter(documentFilter), pageable)
        .map(AccountService::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(UUID accountId) {
    return accountId != null && repository.existsById(accountId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findIdByDocument(String document) {
    String digits = normalizeFilter(document);
    if (digits == null) {
      return Optional.empty();
    }
    return repository.findFirstByDocumentNumber(digits).map(Account::id);
  }

  private static AccountView toView(Account account) {
    return new AccountView(
        account.id(),
        account.legalType(),
        account.documentNumber(),
        account.displayName(),
        account.status(),
        account.cadastur(),
        account.iata(),
        account.createdAt());
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String normalizeFilter(String document) {
    if (document == null) {
      return null;
    }
    String digits = document.replaceAll("\\D", "");
    return digits.isEmpty() ? null : digits;
  }

  /** Masks all but the last two digits of a document for LGPD-safe logging. */
  private static String mask(String document) {
    if (document.length() <= 2) {
      return "**";
    }
    return "*".repeat(document.length() - 2) + document.substring(document.length() - 2);
  }
}
