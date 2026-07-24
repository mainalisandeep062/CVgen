package io.github.mainalisandeep.cvgen.records;

import io.github.mainalisandeep.cvgen.enums.OtpPurpose;

/**
 * Payload of an OTP challenge response. Outcome and wording live in the surrounding
 * {@code GlobalApiResponse}; tokens are never part of an OTP response.
 */
public record OtpResponse(String email, OtpPurpose purpose) {
}
