package io.github.mainalisandeep.cvgen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Credentials for the local sign-in step.
 * <p>
 * There is no {@code rememberMe} here: trusting this device is decided on the OTP
 * screen and travels on {@link VerifyOtpRequestDto}, which is the only request that
 * can act on it — login either recognises an existing trusted-device cookie or falls
 * through to an OTP challenge.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginRequestDto {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    private String password;
}
