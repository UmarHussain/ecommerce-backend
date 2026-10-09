package com.umar.ecommerce.cart.service;

import com.umar.ecommerce.cart.catalog.CatalogVariant;
import com.umar.ecommerce.cart.catalog.HttpPublicCatalogAdapter;
import com.umar.ecommerce.cart.domain.SkuRules;
import com.umar.ecommerce.cart.dto.response.CartResponse;
import com.umar.ecommerce.cart.exception.CartProblem;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Coordinates catalog checks and cart writes. Remote calls stay outside the
 * database transaction. The writer rechecks {@code expectedVersion} after the
 * remote call returns.
 */
@Service
public class CartCommandService {

    private final CartQueryService queries;
    private final CartTransactionService writer;
    private final HttpPublicCatalogAdapter catalog;
    private final CartPresenter presenter;

    public CartCommandService(
            CartQueryService queries,
            CartTransactionService writer,
            HttpPublicCatalogAdapter catalog,
            CartPresenter presenter
    ) {
        this.queries = queries;
        this.writer = writer;
        this.catalog = catalog;
        this.presenter = presenter;
    }

    @Transactional(propagation = Propagation.NEVER)
    public CartResponse get(Owner owner, String correlationId) {
        return present(readOrCreate(owner), correlationId);
    }

    @Transactional(propagation = Propagation.NEVER)
    public CartResponse setQuantity(Owner owner, String sku, int quantity, long expectedVersion, String correlationId) {
        String normalizedSku = SkuRules.normalize(sku);
        Optional<StoredCart> current = queries.read(owner);
        boolean needsCatalog = current.flatMap(cart -> cart.line(normalizedSku))
                .map(line -> quantity > line.quantity())
                .orElse(true);
        CatalogVariant snapshot = needsCatalog ? catalog.requireActive(normalizedSku, correlationId) : null;
        StoredCart updated = write(() -> writer.setQuantity(owner, normalizedSku, quantity, expectedVersion, snapshot));
        return present(updated, correlationId);
    }

    @Transactional(propagation = Propagation.NEVER)
    public CartResponse remove(Owner owner, String sku, long expectedVersion, String correlationId) {
        String normalizedSku = SkuRules.normalize(sku);
        StoredCart updated = writer.remove(owner, normalizedSku, expectedVersion);
        return present(updated, correlationId);
    }

    @Transactional(propagation = Propagation.NEVER)
    public CartResponse clear(Owner owner, long expectedVersion, String correlationId) {
        StoredCart updated = writer.clear(owner, expectedVersion);
        return present(updated, correlationId);
    }

    private StoredCart readOrCreate(Owner owner) {
        Optional<StoredCart> existing = queries.read(owner);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return writer.createEmpty(owner);
        } catch (DataIntegrityViolationException exception) {
            return queries.read(owner).orElseThrow(() -> new CartProblem(
                    org.springframework.http.HttpStatus.CONFLICT,
                    CartProblem.STALE_VERSION,
                    "The cart changed since it was loaded; reload and review"
            ));
        }
    }

    private StoredCart write(java.util.function.Supplier<StoredCart> attempt) {
        try {
            return attempt.get();
        } catch (DataIntegrityViolationException first) {
            if (!ownerConflict(first)) {
                throw integrity(first);
            }
            try {
                return attempt.get();
            } catch (DataIntegrityViolationException second) {
                if (!ownerConflict(second)) {
                    throw integrity(second);
                }
                throw new CartProblem(
                        org.springframework.http.HttpStatus.CONFLICT,
                        CartProblem.STALE_VERSION,
                        "The cart changed since it was loaded; reload and review"
                );
            }
        }
    }

    private static boolean ownerConflict(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains("uq_cart_owner");
    }

    private static CartProblem integrity(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage() == null
                ? ""
                : exception.getMostSpecificCause().getMessage();
        if (message.contains("CART_ITEM_LIMIT")) {
            return new CartProblem(
                    org.springframework.http.HttpStatus.CONFLICT,
                    CartProblem.ITEM_LIMIT,
                    "A cart can hold at most 100 different items"
            );
        }
        return new CartProblem(
                org.springframework.http.HttpStatus.CONFLICT,
                CartProblem.STALE_VERSION,
                "The cart changed since it was loaded; reload and review"
        );
    }

    private CartResponse present(StoredCart cart, String correlationId) {
        if (cart.lines().isEmpty()) {
            return presenter.present(cart, CatalogRefresh.FRESH, Map.of());
        }
        try {
            Map<String, CatalogVariant> live = catalog.findAll(
                    cart.lines().stream().map(StoredLine::sku).toList(),
                    correlationId
            );
            return presenter.present(cart, CatalogRefresh.FRESH, live);
        } catch (CartProblem problem) {
            if (CartProblem.CATALOG_UNAVAILABLE.equals(problem.code())
                    || CartProblem.CATALOG_TIMEOUT.equals(problem.code())) {
                return presenter.present(cart, CatalogRefresh.UNKNOWN, null);
            }
            throw problem;
        }
    }
}
