package com.umar.ecommerce.cart.web;

import com.umar.ecommerce.cart.exception.CartProblem;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler(CartProblem.class)
    ResponseEntity<ProblemDetail> handleProblem(CartProblem exception, HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = response(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request
        );
        if (CartProblem.COMMAND_IN_PROGRESS.equals(exception.code())) {
            return ResponseEntity.status(exception.status())
                    .header("Retry-After", "1")
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler({PessimisticLockException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleLock(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", "1")
                .body(response(
                        HttpStatus.CONFLICT,
                        CartProblem.COMMAND_IN_PROGRESS,
                        "Another cart update is in progress; reload and review",
                        request
                ).getBody());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MissingServletRequestParameterException.class
    })
    ResponseEntity<ProblemDetail> handleValidation(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, CartProblem.VALIDATION_FAILED, "Request validation failed", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleMalformed(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, CartProblem.MALFORMED_REQUEST, "The request could not be parsed", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "Unexpected cart request failure correlationId={} type={}",
                CorrelationIdFilter.correlationId(request),
                exception.getClass().getSimpleName()
        );
        return response(HttpStatus.INTERNAL_SERVER_ERROR, CartProblem.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private static ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String code,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        problem.setProperty("correlationId", CorrelationIdFilter.correlationId(request));
        return ResponseEntity.status(status).body(problem);
    }
}
