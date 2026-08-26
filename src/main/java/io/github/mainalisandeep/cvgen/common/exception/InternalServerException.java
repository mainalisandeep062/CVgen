package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 500 - the request was valid but a dependency this side of it failed. */
public class InternalServerException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InternalServerException(String messageKey, Object... arguments) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, messageKey, arguments);
    }
}
