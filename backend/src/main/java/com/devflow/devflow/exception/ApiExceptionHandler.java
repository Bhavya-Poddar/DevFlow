package com.devflow.devflow.exception;

import java.util.NoSuchElementException;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String message = exception.getReason();
        if (status.is5xxServerError()) message = "An unexpected server error occurred.";
        if (message == null || message.isBlank()) message = status.getReasonPhrase();
        if (status == HttpStatus.UNAUTHORIZED) message = "Authentication is required or invalid.";
        if (status == HttpStatus.FORBIDDEN) message = "You are not allowed to perform this action.";
        return response(status, status.name(), message, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is malformed or contains invalid values.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data.", request);
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The HTTP method is not supported for this resource.", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "The request content type is not supported.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request contains invalid values.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        // Keep logs free of exception messages, SQL, request bodies, and credentials.
        logger.error("Unhandled API exception: method={} path={} type={}",
                request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected server error occurred.", request);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code,
                                                       String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                new ApiErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI()));
    }
}
