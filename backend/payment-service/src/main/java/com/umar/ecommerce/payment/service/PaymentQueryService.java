package com.umar.ecommerce.payment.service;

import com.umar.ecommerce.payment.dto.PaymentAttemptResponse;
import com.umar.ecommerce.payment.repository.PaymentAttemptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class PaymentQueryService {

    private final PaymentAttemptRepository attempts;
    private final PaymentMapper mapper;

    public PaymentQueryService(PaymentAttemptRepository attempts, PaymentMapper mapper) {
        this.attempts = attempts;
        this.mapper = mapper;
    }

    public PaymentAttemptResponse require(UUID operationId) {
        return attempts.findById(operationId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
