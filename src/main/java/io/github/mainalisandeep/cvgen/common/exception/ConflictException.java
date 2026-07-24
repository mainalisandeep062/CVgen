package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 409 - the request conflicts with the current state of the resource. */
public class ConflictException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException(String messageKey, Object... arguments) {
        super(HttpStatus.CONFLICT, messageKey, arguments);
    }
}
