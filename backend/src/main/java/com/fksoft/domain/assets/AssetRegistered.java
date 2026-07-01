package com.fksoft.domain.assets;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: an internal asset was registered (SPEC-0021 Events). Published in-process so other
 * contexts may react — per the spec, {@code finance} (cost) and {@code intelligence} (fixed/infra
 * cost) are the intended consumers, but Assets is a leaf producer and wires none of them in this
 * slice (DL-0067): registering an asset is a fact, not a financial posting. Carries only values (no
 * personal data — patrimony has no PII).
 *
 * @param assetId the registered asset's id (value)
 * @param type the asset-type cadastro code (was {@code AssetType}; SPEC-0031)
 * @param occurredAt when it was registered (UTC)
 */
public record AssetRegistered(UUID assetId, String type, Instant occurredAt) {}
