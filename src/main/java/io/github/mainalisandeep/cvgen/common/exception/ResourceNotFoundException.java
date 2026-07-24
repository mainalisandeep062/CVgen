package io.github.mainalisandeep.cvgen.common.exception;

import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/** 404 - the requested resource does not exist. */
public class ResourceNotFoundException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String messageKey, Object... arguments) {
        super(HttpStatus.NOT_FOUND, messageKey, arguments);
    }

    /** Convenience for the generic "{0} not found" template. */
    public static ResourceNotFoundException of(String field) {
        return new ResourceNotFoundException(ErrorConstantValue.RESOURCE_NOT_FOUND, field);
    }
}
