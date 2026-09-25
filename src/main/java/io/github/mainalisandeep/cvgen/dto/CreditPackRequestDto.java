package io.github.mainalisandeep.cvgen.dto;

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

/** Create or full replacement of a credit pack. Currency is not accepted; it is NPR for now. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreditPackRequestDto {

    @NotBlank(message = "{validation.pack.name.required}")
    @Size(max = 80, message = "{validation.pack.name.size}")
    private String name;

    @NotNull(message = "{validation.pack.credits.required}")
    @Min(value = 1, message = "{validation.pack.credits.range}")
    @Max(value = 100000, message = "{validation.pack.credits.range}")
    private Integer credits;

    @NotNull(message = "{validation.pack.price.required}")
    @Min(value = 0, message = "{validation.pack.price.range}")
    @Max(value = 100000000, message = "{validation.pack.price.range}")
    private Long priceMinor;

    /** Defaults to true. */
    private Boolean active;

    private Boolean highlighted;

    private Integer sortOrder;
}
