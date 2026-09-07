package io.github.mainalisandeep.cvgen.dto;

import java.util.List;

/**
 * A page of CV summaries.
 * <p>
 * An explicit shape rather than a serialised {@code Page}: Spring's page implementation has no
 * stable JSON contract - it says so itself on every serialisation - so clients would be exposed
 * to an envelope that changes under a Spring upgrade.
 *
 * @param page zero-based index of the returned page
 */
public record CvListResponseDto(
        List<CvSummaryResponseDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
