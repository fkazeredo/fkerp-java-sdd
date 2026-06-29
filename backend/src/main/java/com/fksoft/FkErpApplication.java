package com.fksoft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Acme Travel ERP modular monolith.
 *
 * <p>The application is organized in three layers under {@code com.fksoft} (ADR 0012): {@code
 * domain} (pure hexagon core), {@code application} (delivery / driving adapters) and {@code infra}
 * (driven adapters + framework config). Business modules live under {@code
 * com.fksoft.domain.<module>} and start in SPEC-0002 — the foundation (SPEC-0001) ships only the
 * layers and the error kernel.
 */
@SpringBootApplication
public class FkErpApplication {

  public static void main(String[] args) {
    SpringApplication.run(FkErpApplication.class, args);
  }
}
