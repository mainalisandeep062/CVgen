package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 400 - the request was understood but cannot be accepted as-is. */
public class BadRequestException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BadRequestException(String messageKey, Object... arguments) {
        super(HttpStatus.BAD_REQUEST, messageKey, arguments);
    }
}
