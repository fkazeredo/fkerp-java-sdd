/**
 * Assets module (SPEC-0021): the <strong>internal patrimony</strong> context — the Acme's own
 * equipment, software licenses and other goods (OVERVIEW Part 5, line 134/162). It is a
 * Supporting/Generic context, deliberately <strong>lean</strong>: a registry that ties an asset's
 * cost (Finance) and document (Compliance) together and alerts on expiring licenses — <em>not</em>
 * a full asset-management system (no depreciation, maintenance or resale stock — DL-0065). It is a
 * separate context from {@code Portfolio} (the commercial representation, SPEC-0020) — two distinct
 * contexts (Q2, DL-0064/DL-0060).
 *
 * <p><strong>Asset</strong> ({@link com.fksoft.domain.assets.internal.Asset}) is the aggregate root
 * (BR1): a type (EQUIPMENT | SOFTWARE_LICENSE | OTHER), an identifier, an ACTIVE/RETIRED status,
 * the acquisition date and cost ({@code Money}), and — for a SOFTWARE_LICENSE — a mandatory {@code
 * expiresAt}. The acquisition may reference the Compliance document and the Finance cost entry by
 * <strong>value</strong> ({@code documentId}/{@code financeEntryId}, never an FK — BR2). Retiring
 * an asset is audited (who/when/reason) and terminal (DL-0068). A license whose {@code expiresAt}
 * is near is signalled once by a controlled-clock sweep that publishes {@link
 * com.fksoft.domain.assets.AssetLicenseExpiring} (BR3/DL-0066). Assets is patrimony, not a product:
 * it never prices a sale nor enters the commercial flow (BR5).
 *
 * <p>Spring Modulith application module (the 18th business module). It is a <strong>leaf</strong>
 * producer (DL-0067): it publishes {@link com.fksoft.domain.assets.AssetRegistered} and {@link
 * com.fksoft.domain.assets.AssetLicenseExpiring} in-process but depends on <em>no</em> other
 * business module — only the {@code money} and {@code error} kernels — so the module graph stays
 * <strong>acyclic</strong> (Spring Modulith verify). The {@code internal} sub-package (the
 * aggregate and its repository) is module-private.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Assets")
package com.fksoft.domain.assets;
