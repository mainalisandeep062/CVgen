package io.github.mainalisandeep.cvgen.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 429 - the caller is rate limited (OTP resend cooldown, ...). */
public class TooManyRequestsException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TooManyRequestsException(String messageKey, Object... arguments) {
        super(HttpStatus.TOO_MANY_REQUESTS, messageKey, arguments);
    }
}
