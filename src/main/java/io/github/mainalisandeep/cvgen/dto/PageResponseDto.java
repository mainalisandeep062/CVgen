package io.github.mainalisandeep.cvgen.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of anything, in the same shape as {@link CvListResponseDto}.
 * <p>
 * An explicit record rather than a serialised {@code Page}: Spring's page implementation has no
 * stable JSON contract, so clients would be exposed to an envelope that changes under an upgrade.
 *
 * @param page zero-based index of the returned page
 */
public record PageResponseDto<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Page metadata from {@code source}, items already mapped by the caller. */
    public static <T> PageResponseDto<T> of(Page<?> source, List<T> items) {
        return new PageResponseDto<>(
                items,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages()
        );
    }
}
