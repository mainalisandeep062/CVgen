package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 503 - the work is valid but cannot be done right now (render budget exceeded, ...). Retrying may succeed. */
public class ServiceUnavailableException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String messageKey, Object... arguments) {
        super(HttpStatus.SERVICE_UNAVAILABLE, messageKey, arguments);
    }
}
