package io.github.mainalisandeep.cvgen.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A credit pack.
 *
 * @param priceMinor price in minor units (paisa)
 * @param purchases  completed purchases of this pack; always 0 on the user-facing endpoint
 */
public record CreditPackResponseDto(
        UUID id,
        String name,
        int credits,
        long priceMinor,
        String currency,
        boolean active,
        boolean highlighted,
        int sortOrder,
        long purchases,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
