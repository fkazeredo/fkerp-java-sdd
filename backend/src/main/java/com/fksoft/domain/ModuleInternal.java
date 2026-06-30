package com.fksoft.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a domain type as <strong>module-internal</strong>: it is part of a module's implementation,
 * not its public API. Replaces the former {@code internal} sub-package convention (Phase 9 / ADR
 * 0016): after flattening {@code domain.<module>.internal.*} into {@code domain.<module>.*}, the
 * base package would otherwise become the module's whole public surface (Spring Modulith's unnamed
 * named interface). This marker re-establishes the boundary on the <em>type</em> instead of the
 * folder.
 *
 * <p>Encapsulation is enforced by ArchUnit (see {@code ArchitectureTest}): no class belonging to
 * another domain module may depend on a {@code @ModuleInternal} type. The module itself (including
 * its tests) and the {@code infra} layer (which may operate a module's persistence — ADR 0010/0012)
 * are exempt. The companion teeth test proves the rule actually fails when a foreign module touches
 * such a type.
 *
 * <p>Lives in the {@code com.fksoft.domain} kernel (like {@code domain.error} and {@code
 * domain.money}): a non-module type modules may reference freely. {@link RetentionPolicy#CLASS} so
 * the marker is visible to ArchUnit's bytecode analysis while staying out of the runtime.
 *
 * <p>Apply it only to <strong>public</strong> module-internal types (entities, repositories, public
 * codecs/helpers). Package-private types are already hidden from other modules by the Java compiler
 * and need no marker.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface ModuleInternal {}
