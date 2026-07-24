package io.github.mainalisandeep.cvgen.common.exception;

import io.github.mainalisandeep.cvgen.common.message.CustomMessageSource;
import io.github.mainalisandeep.cvgen.common.message.ErrorConstantValue;
import io.github.mainalisandeep.cvgen.common.response.GlobalApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Turns every exception leaving a controller into a {@link GlobalApiResponse} whose
 * message is resolved from {@code messages.properties} for the caller locale.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final CustomMessageSource customMessageSource;

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleBaseException(BaseException exception) {
        log.debug("Handled application exception: {}", exception.getMessageKey());
        return build(exception.getStatus(), customMessageSource.get(exception.getMessageKey(), exception.getArguments()), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleValidation(MethodArgumentNotValidException exception) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, customMessageSource.get(ErrorConstantValue.VALIDATION_FAILED), errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleConstraintViolation(ConstraintViolationException exception) {
        List<String> errors = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, customMessageSource.get(ErrorConstantValue.VALIDATION_FAILED), errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<GlobalApiResponse<Object>> handleMalformedRequest(Exception exception) {
        log.debug("Malformed request: {}", exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, customMessageSource.get(ErrorConstantValue.REQUEST_MALFORMED), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                customMessageSource.get(ErrorConstantValue.METHOD_NOT_ALLOWED, exception.getMethod()), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleAuthentication(AuthenticationException exception) {
        log.debug("Authentication failure: {}", exception.getMessage());
        return build(HttpStatus.UNAUTHORIZED, customMessageSource.get(ErrorConstantValue.UNAUTHORIZED), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleAccessDenied(AccessDeniedException exception) {
        return build(HttpStatus.FORBIDDEN, customMessageSource.get(ErrorConstantValue.FORBIDDEN), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GlobalApiResponse<Object>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, customMessageSource.get(ErrorConstantValue.INTERNAL_SERVER), null);
    }

    private ResponseEntity<GlobalApiResponse<Object>> build(HttpStatus status, String message, List<String> errors) {
        return ResponseEntity.status(status).body(
                GlobalApiResponse.builder()
                        .status(false)
                        .message(message)
                        .error(errors)
                        .build()
        );
    }
}
