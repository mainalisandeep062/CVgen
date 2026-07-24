package io.github.mainalisandeep.cvgen.service;

import io.github.mainalisandeep.cvgen.enums.OtpPurpose;

public interface MailService {

    /** Sends the OTP mail asynchronously; delivery failures are logged, never propagated to the caller. */
    void sendOtpEmail(String to, String otp, int expiryMinutes, OtpPurpose purpose);
}
