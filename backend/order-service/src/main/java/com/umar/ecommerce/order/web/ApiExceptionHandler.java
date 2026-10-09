package com.umar.ecommerce.order.web;

import com.umar.ecommerce.order.exception.OrderProblem;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderProblem.class)
    ResponseEntity<ProblemDetail> handle(OrderProblem exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle(exception.status().getReasonPhrase());
        problem.setProperty("code", exception.code());
        if (exception.reason() != null) {
            problem.setProperty("reason", exception.reason());
        }
        problem.setInstance(URI.create(request.getRequestURI()));
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.status());
        if (OrderProblem.IDEMPOTENCY_IN_PROGRESS.equals(exception.code())) {
            builder.header("Retry-After", "1");
        }
        return builder.body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected order request failure path={} correlationId={}",
                request.getRequestURI(), CorrelationIdFilter.correlationId(request), exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "The order request could not be completed");
        problem.setProperty("code", OrderProblem.INTERNAL_ERROR);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
