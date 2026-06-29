/**
 * Money domain kernel: the shared {@link com.fksoft.domain.money.Money} value object (amount +
 * currency), used by several modules (Commissioning, Quoting, Reconciliation) and their DTOs.
 *
 * <p>Like {@code com.fksoft.domain.error}, this is a <strong>non-module</strong> domain kernel
 * (deliberately <em>not</em> annotated with {@code @ApplicationModule}): modules may depend on it
 * freely, the same way they depend on the JDK or Spring, without crossing a Spring Modulith
 * boundary. It is not a junk-drawer "shared" module — it holds exactly one cohesive primitive.
 */
package com.fksoft.domain.money;
