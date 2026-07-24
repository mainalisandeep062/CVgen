package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 401 - credentials are missing, invalid or expired. */
public class UnauthorizedException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String messageKey, Object... arguments) {
        super(HttpStatus.UNAUTHORIZED, messageKey, arguments);
    }
}
