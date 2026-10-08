package com.balaji.sync_engine.exception;

import com.balaji.sync_engine.dto.ApiResult;
import com.balaji.sync_engine.dto.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleBadRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(NoSuchElementException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResult<Void>> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    /** Funnels every standard Spring MVC error (404 route, 405, 415, unreadable body, bad path type...) into the envelope. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String message = (body instanceof ProblemDetail problem && problem.getDetail() != null)
                ? problem.getDetail()
                : HttpStatus.valueOf(statusCode.value()).getReasonPhrase();
        ApiResult<Void> envelope = ApiResult.failure(ErrorCode.fromStatus(statusCode.value()), message);
        return ResponseEntity.status(statusCode).headers(headers).body(envelope);
    }

    private ResponseEntity<ApiResult<Void>> build(HttpStatus status, String message) {
        ApiResult<Void> envelope = ApiResult.failure(ErrorCode.fromStatus(status.value()), message);
        return ResponseEntity.status(status).body(envelope);
    }
}