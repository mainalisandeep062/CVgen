package io.github.mainalisandeep.cvgen.common.message;

/**
 * Message keys for failure responses. Values live in {@code messages.properties}
 * and are resolved through {@link CustomMessageSource}.
 */
public final class ErrorConstantValue {

    private ErrorConstantValue() {
    }

    /** Generic "{0} not found", argument is a {@link FieldConstantValue}. */
    public static final String RESOURCE_NOT_FOUND = "error.resource.not.found";

    public static final String EMAIL_ALREADY_REGISTERED = "error.email.already.registered";
    public static final String INVALID_CREDENTIALS = "error.invalid.credentials";
    public static final String OTP_INVALID = "error.otp.invalid";
    public static final String OTP_RESEND_COOLDOWN = "error.otp.resend.cooldown";
    public static final String EXCHANGE_CODE_INVALID = "error.oauth2.exchange.code.invalid";
    public static final String REFRESH_TOKEN_INVALID = "error.refresh.token.invalid";
    public static final String MAIL_SEND_FAILED = "error.mail.send.failed";

    public static final String UNAUTHORIZED = "error.unauthorized";
    public static final String FORBIDDEN = "error.forbidden";
    public static final String VALIDATION_FAILED = "error.validation.failed";
    public static final String REQUEST_MALFORMED = "error.request.malformed";
    public static final String METHOD_NOT_ALLOWED = "error.method.not.allowed";
    public static final String INTERNAL_SERVER = "error.internal.server";
    public static final String REFRESH_TOKEN_REUSED = "error.refresh.token.reused";

    public static final String FILE_EMPTY = "error.file.empty";
    public static final String FILE_TOO_LARGE = "error.file.too.large";
    public static final String FILE_TYPE_UNSUPPORTED = "error.file.type.unsupported";
    public static final String FILE_STORAGE_FAILED = "error.file.storage.failed";

    /** The linked account reports no avatar to copy. */
    public static final String PROFILE_PICTURE_UNAVAILABLE = "error.profile.picture.unavailable";

    /** The provider's avatar URL could not be fetched, or did not return a usable image. */
    public static final String PROFILE_PICTURE_FETCH_FAILED = "error.profile.picture.fetch.failed";
}
