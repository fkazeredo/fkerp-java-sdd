package com.fksoft.infra.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single UTC {@link Clock} bean so time-dependent code never relies on the server's
 * default timezone (backend.md: technical instants in UTC) and can be controlled in tests.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
