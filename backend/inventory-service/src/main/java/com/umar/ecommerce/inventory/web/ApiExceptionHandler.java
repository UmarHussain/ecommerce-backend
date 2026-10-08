package com.umar.ecommerce.inventory.web;

import com.umar.ecommerce.inventory.exception.InventoryProblem;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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

    @ExceptionHandler(InventoryProblem.class)
    ResponseEntity<ProblemDetail> handleProblem(InventoryProblem exception, HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = response(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request,
                null
        );
        if (InventoryProblem.COMMAND_IN_PROGRESS.equals(exception.code())) {
            return ResponseEntity.status(exception.status())
                    .header("Retry-After", "1")
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::fieldError)
                .toList();
        return response(HttpStatus.BAD_REQUEST, InventoryProblem.VALIDATION_FAILED, "Request validation failed", request, fieldErrors);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class, MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> handleConstraint(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, InventoryProblem.VALIDATION_FAILED, "Request validation failed", request, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleMalformed(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, InventoryProblem.MALFORMED_REQUEST, "The request could not be parsed", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimistic(ObjectOptimisticLockingFailureException exception, HttpServletRequest request) {
        return response(
                HttpStatus.CONFLICT,
                InventoryProblem.STALE_VERSION,
                "Stock changed concurrently; reload and review the current values",
                request,
                null
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        String message = exception.getMostSpecificCause().getMessage() == null
                ? ""
                : exception.getMostSpecificCause().getMessage();
        if (message.contains("ck_stock")) {
            return response(
                    HttpStatus.CONFLICT,
                    InventoryProblem.STOCK_INVARIANT,
                    "The stock change violates a quantity invariant",
                    request,
                    null
            );
        }
        return response(
                HttpStatus.CONFLICT,
                InventoryProblem.VARIANT_ALREADY_STOCKED,
                "The request conflicts with existing inventory data",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected inventory request failure correlationId={} type={}",
                CorrelationIdFilter.correlationId(request),
                exception.getClass().getSimpleName()
        );
        return response(HttpStatus.INTERNAL_SERVER_ERROR, InventoryProblem.INTERNAL_ERROR, "An unexpected error occurred", request, null);
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
        problem.setProperty("correlationId", CorrelationIdFilter.correlationId(request));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(problem);
    }

    private static Map<String, String> fieldError(FieldError error) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("field", error.getField());
        result.put("message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
        return result;
    }
}
