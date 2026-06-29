package com.fksoft.infra.i18n;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * i18n configuration. Resolves user-facing messages from {@code messages.properties} (fallback) and
 * {@code messages_pt_BR.properties} (default project locale, pt-BR). Domain error codes are i18n
 * keys (ADR 0011), so a missing key falls back to the code itself at the call site.
 */
@Configuration
public class MessageSourceConfig {

  /** Project default locale: Brazilian Portuguese. */
  public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("pt-BR");

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setDefaultLocale(DEFAULT_LOCALE);
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
