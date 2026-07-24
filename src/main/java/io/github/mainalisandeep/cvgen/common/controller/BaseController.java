package io.github.mainalisandeep.cvgen.common.controller;

import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Base for every REST controller: uniform {@link GlobalApiResponse} envelopes and
 * access to {@link CustomMessageSource} for locale-aware text.
 * <p>
 * The message source is injected through a setter so subclasses stay free to use
 * {@code @RequiredArgsConstructor} for their own dependencies.
 * <p>
 * Failures are <em>not</em> built here - throw a
 * {@code common.exception.BaseException} subclass and let
 * {@code GlobalExceptionHandler} render it.
 */
public abstract class BaseController {

    /** API success and error status flags carried by {@link GlobalApiResponse#getStatus()}. */
    protected static final boolean API_SUCCESS_STATUS = true;
    protected static final boolean API_ERROR_STATUS = false;

    protected CustomMessageSource customMessageSource;

    @Autowired
    public final void setCustomMessageSource(CustomMessageSource customMessageSource) {
        this.customMessageSource = customMessageSource;
    }

    // --- Global API responses ---

    protected <T> GlobalApiResponse<T> successResponse(String message, T data) {
        return GlobalApiResponse.<T>builder()
                .status(API_SUCCESS_STATUS)
                .message(message)
                .data(data)
                .build();
    }

    protected GlobalApiResponse<Object> successResponse(String message) {
        return successResponse(message, null);
    }

    protected <T> GlobalApiResponse<T> errorResponse(String message, List<String> errors) {
        return GlobalApiResponse.<T>builder()
                .status(API_ERROR_STATUS)
                .message(message)
                .error(errors)
                .build();
    }

    /** Resolves {@code messageKey} and wraps {@code data} in a 200 response. */
    protected <T> ResponseEntity<GlobalApiResponse<T>> ok(String messageKey, T data, Object... arguments) {
        return ResponseEntity.ok(successResponse(customMessageSource.get(messageKey, arguments), data));
    }

    /** Resolves {@code messageKey} and wraps {@code data} in a response with the given status. */
    protected <T> ResponseEntity<GlobalApiResponse<T>> respond(HttpStatus status, String messageKey, T data, Object... arguments) {
        return ResponseEntity.status(status).body(successResponse(customMessageSource.get(messageKey, arguments), data));
    }
}
