package io.github.mainalisandeep.cvgen.enums;

/** What an admin did, as recorded in {@code admin_audit_logs.action}. */
public enum AdminAction {
    USER_UPDATED,
    USER_ROLE_CHANGED,
    USER_STATUS_CHANGED,
    USER_DELETED,
    CREDITS_ADJUSTED,
    TRANSACTION_REFUNDED,
    TEMPLATE_CREATED,
    TEMPLATE_UPDATED,
    TEMPLATE_DELETED,
    PACK_CREATED,
    PACK_UPDATED,
    PACK_DELETED,
    NOTIFICATION_SENT,
    NOTIFICATION_DELETED
}
