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

    /** The content document is missing, or is not a JSON object. */
    public static final String CV_CONTENT_INVALID = "error.cv.content.invalid";

    /** The content document is larger than the configured cap. */
    public static final String CV_CONTENT_TOO_LARGE = "error.cv.content.too.large";

    /** The document declares a schema version this server cannot write. */
    public static final String CV_SCHEMA_VERSION_UNSUPPORTED = "error.cv.schema.version.unsupported";

    /** The user already owns as many CVs as they are allowed. */
    public static final String CV_LIMIT_REACHED = "error.cv.limit.reached";

    /** The template key names no template in the registry. */
    public static final String CV_TEMPLATE_UNKNOWN = "error.cv.template.unknown";

    /** A concurrent write changed the row first; the client should reload and retry. */
    public static final String CONCURRENT_UPDATE = "error.concurrent.update";

    /** A premium template the caller has not unlocked. */
    public static final String CV_TEMPLATE_LOCKED = "error.cv.template.locked";

    /** Rendering the PDF took longer than the configured budget. */
    public static final String CV_EXPORT_TIMEOUT = "error.cv.export.timeout";

    /** The renderer failed on a document it should have handled. */
    public static final String CV_EXPORT_FAILED = "error.cv.export.failed";

    /** The upload is neither a PDF nor a Word (.docx) document, judged by its bytes. */
    public static final String CV_IMPORT_UNSUPPORTED_TYPE = "error.cv.import.unsupported.type";

    /** The upload is larger than the import cap. */
    public static final String CV_IMPORT_TOO_LARGE = "error.cv.import.too.large";

    /** The PDF has more pages than a CV plausibly does. */
    public static final String CV_IMPORT_TOO_MANY_PAGES = "error.cv.import.too.many.pages";

    /** The PDF is password protected. */
    public static final String CV_IMPORT_ENCRYPTED = "error.cv.import.encrypted";

    /** The file is damaged, or not what its first bytes claim. */
    public static final String CV_IMPORT_UNREADABLE = "error.cv.import.unreadable";

    /** The file has no extractable text - usually a scanned image. */
    public static final String CV_IMPORT_NO_TEXT = "error.cv.import.no.text";

    /** Not a syntactically valid GitHub username. */
    public static final String GITHUB_USERNAME_INVALID = "error.github.username.invalid";

    /** GitHub refused because the unauthenticated rate limit is spent. */
    public static final String GITHUB_RATE_LIMITED = "error.github.rate.limited";

    /** GitHub could not be reached, or answered with something unusable. */
    public static final String GITHUB_UNAVAILABLE = "error.github.unavailable";

    /** The requested gateway is not configured on this server. */
    public static final String PAYMENT_GATEWAY_DISABLED = "error.payment.gateway.disabled";

    /** The gateway could not be reached or answered with something unusable. */
    public static final String PAYMENT_GATEWAY_UNAVAILABLE = "error.payment.gateway.unavailable";

    /** The pack does not exist or is no longer on sale. */
    public static final String PAYMENT_PACK_UNAVAILABLE = "error.payment.pack.unavailable";

    /** The account is suspended; refused at token issue and on every authenticated request. */
    public static final String ACCOUNT_SUSPENDED = "error.account.suspended";

    /** An admin tried to change their own role or status, or to delete themselves. */
    public static final String ADMIN_SELF_MODIFICATION = "error.admin.self.modification";

    /** The change would leave no active admin. */
    public static final String ADMIN_LAST_ADMIN = "error.admin.last.admin";

    public static final String TEMPLATE_KEY_EXISTS = "error.template.key.exists";

    /** {0} is the number of CVs still using the template. */
    public static final String TEMPLATE_IN_USE = "error.template.in.use";

    /** The configured default template cannot be deleted or deactivated. */
    public static final String TEMPLATE_DEFAULT = "error.template.default";

    public static final String TEMPLATE_LAYOUT_UNKNOWN = "error.template.layout.unknown";
    public static final String TEMPLATE_SECTION_UNSUPPORTED = "error.template.section.unsupported";

    public static final String CREDIT_PACK_IN_USE = "error.credit.pack.in.use";

    /** The change would take a credit balance below zero. */
    public static final String CREDITS_INSUFFICIENT = "error.credits.insufficient";

    /** Only a completed purchase can be refunded, and only once. */
    public static final String TRANSACTION_NOT_REFUNDABLE = "error.transaction.not.refundable";

    /** {0} is the property a caller asked to sort by. */
    public static final String SORT_UNSUPPORTED = "error.sort.unsupported";
}
