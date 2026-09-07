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
    public static final String PROFILE_PICTURE_URL = "Profile picture URL";
    public static final String PROFILE_PICTURE = "Profile picture";
    public static final String PROFILE_PICTURE_OPTIONS = "Profile picture options";
    public static final String USER_IDENTITY = "Linked account";
    public static final String CV = "CV";
    public static final String CV_LIST = "CVs";
}
