package com.umar.ecommerce.catalog.web;

import com.umar.ecommerce.catalog.exception.InvalidRequestException;
import com.umar.ecommerce.catalog.exception.ResourceConflictException;
import com.umar.ecommerce.catalog.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                exception.getCode(),
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(
            ResourceConflictException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                exception.getCode(),
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(
            InvalidRequestException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getCode(),
                exception.getMessage(),
                request,
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ApiExceptionHandler::fieldError)
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                "CATALOG_VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintValidation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<Map<String, String>> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()
                ))
                .toList();
        return response(
                HttpStatus.BAD_REQUEST,
                "CATALOG_VALIDATION_FAILED",
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ProblemDetail> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "CATALOG_MALFORMED_REQUEST",
                "The request could not be parsed",
                request,
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                ResourceConflictException.CODE,
                "The request conflicts with existing catalog data",
                request,
                null
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "CATALOG_CONCURRENT_MODIFICATION",
                "Catalog data changed concurrently; retry with current data",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error(
                "Unexpected catalog request failure correlationId={} type={}",
                correlationId(request),
                exception.getClass().getSimpleName()
        );
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CATALOG_INTERNAL_ERROR",
                "An unexpected error occurred",
                request,
                null
        );
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request,
            List<Map<String, String>> fieldErrors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("correlationId", correlationId(request));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private static Map<String, String> fieldError(FieldError error) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("field", error.getField());
        result.put("message", error.getDefaultMessage() == null
                ? "Invalid value"
                : error.getDefaultMessage());
        return result;
    }

    static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unavailable" : value.toString();
    }
}
