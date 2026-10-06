package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.domain.OperationStatus;
import com.umar.ecommerce.user.domain.OperationType;
import com.umar.ecommerce.user.entity.IdentityOperation;
import com.umar.ecommerce.user.exception.ConflictException;
import com.umar.ecommerce.user.exception.NotFoundException;
import com.umar.ecommerce.user.repository.IdentityOperationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class IdentityOperationService {

    private final IdentityOperationRepository repository;

    public IdentityOperationService(IdentityOperationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IdentityOperation begin(
            String idempotencyKey,
            String canonicalRequest,
            OperationType type,
            String targetIdentity
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new com.umar.ecommerce.user.exception.InvalidRequestException(
                    "Idempotency-Key is required for directory-changing operations"
            );
        }
        String hash = sha256(canonicalRequest);
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> reuse(existing, hash))
                .orElseGet(() -> repository.save(new IdentityOperation(idempotencyKey, hash, type, targetIdentity)));
    }

    @Transactional
    public IdentityOperation markInProgress(UUID id) {
        IdentityOperation operation = require(id);
        operation.markInProgress();
        return repository.save(operation);
    }

    @Transactional
    public IdentityOperation succeed(UUID id, UUID userId) {
        IdentityOperation operation = require(id);
        operation.markSucceeded(userId);
        return repository.save(operation);
    }

    @Transactional
    public IdentityOperation uncertain(UUID id, String error) {
        IdentityOperation operation = require(id);
        operation.markUncertain(error);
        return repository.save(operation);
    }

    @Transactional
    public IdentityOperation fail(UUID id, String error) {
        IdentityOperation operation = require(id);
        operation.markFailed(error);
        return repository.save(operation);
    }

    @Transactional(readOnly = true)
    public IdentityOperation get(UUID id) {
        return require(id);
    }

    @Transactional(readOnly = true)
    public List<IdentityOperation> dueUncertain() {
        return repository.findByStatusAndNextAttemptAtLessThanEqual(OperationStatus.UNCERTAIN, Instant.now());
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private IdentityOperation reuse(IdentityOperation existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) {
            throw new ConflictException(
                    "USER_IDEMPOTENCY_CONFLICT",
                    "Idempotency-Key was reused with a different request body"
            );
        }
        return existing;
    }

    private IdentityOperation require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Operation was not found"));
    }
}
