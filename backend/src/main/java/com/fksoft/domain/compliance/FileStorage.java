package com.fksoft.domain.compliance;

/**
 * Port for the document binary store (SPEC-0008; DL-0015). The domain depends only on this
 * interface, never on a storage SDK (messaging-and-integrations.md §Files). The adapter
 * (filesystem/S3/…) lives in {@code com.fksoft.infra.integration}. The returned {@code fileRef} is
 * opaque (never a filesystem path) so paths never leak (security.md).
 */
public interface FileStorage {

  /**
   * Stores the content and returns an opaque reference to it.
   *
   * @param content the document bytes
   * @param originalFilename the original filename (for extension/content checks; not the ref)
   * @param contentType the declared content type
   * @return an opaque storage reference
   */
  String store(byte[] content, String originalFilename, String contentType);

  /**
   * Reads back the content for an opaque reference.
   *
   * @param fileRef the opaque reference returned by {@link #store}
   * @return the stored bytes
   */
  byte[] read(String fileRef);

  /**
   * Deletes the content for an opaque reference (called only after the retention deadline, BR7).
   *
   * @param fileRef the opaque reference
   */
  void delete(String fileRef);
}
