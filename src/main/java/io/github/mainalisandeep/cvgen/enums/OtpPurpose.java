package io.github.mainalisandeep.cvgen.enums;

/**
 * Why an OTP was issued. Persisted by name in {@code otp_codes.purpose} and
 * accepted/emitted as the same name over JSON.
 */
public enum OtpPurpose {

    SIGNUP,
    LOGIN
}
