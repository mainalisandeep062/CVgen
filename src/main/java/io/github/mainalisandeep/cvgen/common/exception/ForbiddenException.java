package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 403 - the caller is known but may not do this (suspended account, ...). */
public class ForbiddenException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ForbiddenException(String messageKey, Object... arguments) {
        super(HttpStatus.FORBIDDEN, messageKey, arguments);
    }
}
