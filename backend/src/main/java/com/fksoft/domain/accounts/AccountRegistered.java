package com.fksoft.domain.accounts;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a commercial account was registered. Published in-process (Spring event) by the
 * accounts module; no consumer yet (future: Marketing, Intelligence). Becomes a stable
 * contract/outbox once another module or service consumes it (messaging-and-integrations.md). The
 * payload deliberately omits the document number (LGPD — never log/propagate personal data).
 *
 * @param accountId the registered account id
 * @param legalType the legal type of the account
 * @param occurredAt when the registration happened
 */
public record AccountRegistered(UUID accountId, LegalType legalType, Instant occurredAt) {}
