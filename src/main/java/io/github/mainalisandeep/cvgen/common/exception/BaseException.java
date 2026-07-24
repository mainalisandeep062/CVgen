package io.github.mainalisandeep.cvgen.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Root of all application exceptions.
 * <p>
 * Carries a message <em>key</em> (see {@code common.message.ErrorConstantValue}) plus its
 * arguments; {@link GlobalExceptionHandler} resolves it against the caller locale, so no
 * layer below the handler ever builds user-facing text.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String messageKey;
    private final transient Object[] arguments;
    private final HttpStatus status;

    protected BaseException(HttpStatus status, String messageKey, Object... arguments) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.arguments = arguments;
    }
}
