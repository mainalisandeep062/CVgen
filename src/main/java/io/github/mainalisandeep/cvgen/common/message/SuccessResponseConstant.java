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

    public static final String PROFILE_PICTURE_UPDATED = "success.profile.picture.updated";
    public static final String PROFILE_PICTURE_REMOVED = "success.profile.picture.removed";

    public static final String CV_CREATED = "success.cv.created";
    public static final String CV_UPDATED = "success.cv.updated";
    public static final String CV_DELETED = "success.cv.deleted";
    public static final String CV_ANALYZED = "success.cv.analyzed";
    public static final String CV_IMPORTED = "success.cv.imported";
    public static final String CHECKOUT_STARTED = "success.checkout.started";
    public static final String PAYMENT_CONFIRMED = "success.payment.confirmed";
    public static final String TEMPLATE_UNLOCKED = "success.template.unlocked";

    public static final String USER_UPDATED = "success.user.updated";
    public static final String USER_DELETED = "success.user.deleted";
    public static final String CREDITS_ADJUSTED = "success.credits.adjusted";

    public static final String TEMPLATE_CREATED = "success.template.created";
    public static final String TEMPLATE_UPDATED = "success.template.updated";
    public static final String TEMPLATE_DELETED = "success.template.deleted";

    public static final String CREDIT_PACK_CREATED = "success.credit.pack.created";
    public static final String CREDIT_PACK_UPDATED = "success.credit.pack.updated";
    public static final String CREDIT_PACK_DELETED = "success.credit.pack.deleted";
    public static final String TRANSACTION_REFUNDED = "success.transaction.refunded";

    public static final String NOTIFICATION_SENT = "success.notification.sent";
    public static final String NOTIFICATION_DELETED = "success.notification.deleted";
    public static final String NOTIFICATION_READ = "success.notification.read";
    public static final String NOTIFICATIONS_READ_ALL = "success.notifications.read.all";
}
