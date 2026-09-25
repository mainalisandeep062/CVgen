package io.github.mainalisandeep.cvgen.common.message;

/**
 * Message keys for text the server composes as data rather than as a response message: audit log
 * summaries and the dashboard's recent-activity titles. Values live in {@code messages.properties}.
 * <p>
 * An audit summary is resolved once, when the entry is written, and stored as text - it records what
 * the acting admin saw, so it is deliberately not re-translated on read.
 */
public final class ActivityMessageConstant {

    private ActivityMessageConstant() {
    }

    /** {0} email, {1} new name. */
    public static final String AUDIT_USER_RENAMED = "audit.user.renamed";
    /** {0} email, {1} old role, {2} new role. */
    public static final String AUDIT_USER_ROLE_CHANGED = "audit.user.role.changed";
    /** {0} email. */
    public static final String AUDIT_USER_SUSPENDED = "audit.user.suspended";
    /** {0} email. */
    public static final String AUDIT_USER_REACTIVATED = "audit.user.reactivated";
    /** {0} email. */
    public static final String AUDIT_USER_DELETED = "audit.user.deleted";
    /** {0} credits, {1} email. */
    public static final String AUDIT_CREDITS_GRANTED = "audit.credits.granted";
    /** {0} credits, {1} email. */
    public static final String AUDIT_CREDITS_DEDUCTED = "audit.credits.deducted";
    /** {0} credits, {1} email. */
    public static final String AUDIT_TRANSACTION_REFUNDED = "audit.transaction.refunded";
    /** {0} template key. */
    public static final String AUDIT_TEMPLATE_CREATED = "audit.template.created";
    public static final String AUDIT_TEMPLATE_UPDATED = "audit.template.updated";
    public static final String AUDIT_TEMPLATE_DELETED = "audit.template.deleted";
    /** {0} pack name. */
    public static final String AUDIT_PACK_CREATED = "audit.pack.created";
    public static final String AUDIT_PACK_UPDATED = "audit.pack.updated";
    public static final String AUDIT_PACK_DELETED = "audit.pack.deleted";
    /** {0} title. */
    public static final String AUDIT_NOTIFICATION_BROADCAST = "audit.notification.broadcast";
    /** {0} title, {1} recipient email. */
    public static final String AUDIT_NOTIFICATION_SENT = "audit.notification.sent";
    /** {0} title. */
    public static final String AUDIT_NOTIFICATION_DELETED = "audit.notification.deleted";

    /** {0} display name. */
    public static final String ACTIVITY_USER_SIGNUP = "activity.user.signup";
    /** {0} display name. */
    public static final String ACTIVITY_CV_CREATED = "activity.cv.created";
    /** {0} display name, {1} credits. */
    public static final String ACTIVITY_PURCHASE = "activity.purchase";
}
