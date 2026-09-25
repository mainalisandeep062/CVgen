package io.github.mainalisandeep.cvgen.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Manual credit grant (positive) or deduction (negative). The note is required because the ledger
 * row is the only explanation a user will ever get for a balance they did not cause.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreditAdjustmentRequestDto {

    @NotNull(message = "{validation.credits.required}")
    @Min(value = -100000, message = "{validation.credits.range}")
    @Max(value = 100000, message = "{validation.credits.range}")
    private Integer credits;

    @NotBlank(message = "{validation.note.required}")
    @Size(max = 500, message = "{validation.note.size}")
    private String note;

    /** A zero adjustment would write a ledger row that changes nothing. */
    @JsonIgnore
    @AssertTrue(message = "{validation.credits.nonzero}")
    public boolean isCreditsNonZero() {
        return credits == null || credits != 0;
    }
}
