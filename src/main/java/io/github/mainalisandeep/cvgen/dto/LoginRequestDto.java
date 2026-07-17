package io.github.mainalisandeep.cvgen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginRequestDto {

    @Email
    @NotBlank
    String email;

    @NotBlank
    String password;

    Boolean rememberMe;
}
