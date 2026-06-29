package com.fksoft.infra.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Pagination envelope that controllers wrap a {@link Page} with before returning it, so the JSON
 * contract is stable and independent of Spring Data's internal page representation (ADR 0012).
 *
 * @param <T> the element type
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  /** Maps a Spring Data {@link Page} into the stable envelope. */
  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
