package io.github.mainalisandeep.cvgen.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Optional reason recorded on the REFUND row. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefundRequestDto {

    @Size(max = 500, message = "{validation.note.size}")
    private String note;
}
