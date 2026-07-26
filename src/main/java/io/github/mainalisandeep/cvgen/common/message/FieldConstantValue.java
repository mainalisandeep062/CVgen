package io.github.mainalisandeep.cvgen.common.message;

/**
 * Human-readable subject names passed as {@code {0}} arguments to the generic
 * message templates in {@link SuccessResponseConstant} and {@link ErrorConstantValue}.
 */
public final class FieldConstantValue {

    private FieldConstantValue() {
    }

    public static final String USER = "User";
    public static final String EMAIL = "Email";
    public static final String OTP = "OTP";
    public static final String TRUSTED_DEVICE = "Trusted device";
    public static final String REFRESH_TOKEN = "Refresh token";
    public static final String EXCHANGE_CODE = "Exchange code";
}
