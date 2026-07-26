package io.github.mainalisandeep.cvgen.common.message;

/**
 * Message keys for successful responses. Values live in {@code messages.properties}
 * and are resolved through {@link CustomMessageSource}.
 */
public final class SuccessResponseConstant {

    private SuccessResponseConstant() {
    }

    /** Generic "{0} fetched successfully", argument is a {@link FieldConstantValue}. */
    public static final String FETCH_SUCCESS = "success.fetch";

    public static final String OTP_SENT = "success.otp.sent";
    public static final String OTP_REQUIRED = "success.otp.required";
    public static final String OTP_VERIFIED = "success.otp.verified";
    public static final String LOGIN_SUCCESS = "success.login";
    public static final String LOGOUT_SUCCESS = "success.logout";
    public static final String TOKEN_REFRESHED = "success.token.refreshed";
}
