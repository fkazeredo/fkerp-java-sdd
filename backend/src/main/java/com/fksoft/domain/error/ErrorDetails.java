package com.fksoft.domain.error;

import java.util.Map;

/**
 * Optional contract a {@link DomainException} may implement to expose extra domain data as
 * field/value pairs (e.g. the set of unavailable seat ids). The presentation layer renders these as
 * the {@code fields} of the API error response. This is domain data, not transport classification.
 */
public interface ErrorDetails {

  /** Field/value pairs that further qualify the error. */
  Map<String, Object> details();
}
