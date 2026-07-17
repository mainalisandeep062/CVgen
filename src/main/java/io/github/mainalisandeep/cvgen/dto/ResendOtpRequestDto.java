package io.github.mainalisandeep.cvgen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResendOtpRequestDto {
    @NotBlank
    @Email
    String email;

    @NotBlank String purpose;
}
