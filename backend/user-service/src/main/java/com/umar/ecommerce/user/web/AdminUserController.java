package com.umar.ecommerce.user.web;

import com.umar.ecommerce.user.application.Actor;
import com.umar.ecommerce.user.application.IdentityOperationService;
import com.umar.ecommerce.user.application.UserAdministrationService;
import com.umar.ecommerce.user.web.dto.CreateUserRequest;
import com.umar.ecommerce.user.web.dto.OnboardStaffRequest;
import com.umar.ecommerce.user.web.dto.OperationResponse;
import com.umar.ecommerce.user.web.dto.PageResponse;
import com.umar.ecommerce.user.web.dto.RoleChangeRequest;
import com.umar.ecommerce.user.web.dto.UserDetailResponse;
import com.umar.ecommerce.user.web.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@Tag(name = "User administration")
public class AdminUserController {

    private final UserAdministrationService administration;
    private final IdentityOperationService operations;

    public AdminUserController(UserAdministrationService administration, IdentityOperationService operations) {
        this.administration = administration;
        this.operations = operations;
    }

    @GetMapping("/api/v1/admin/users")
    public PageResponse<UserSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return administration.search(q, page, size);
    }

    @GetMapping("/api/v1/admin/users/{userId}")
    public UserDetailResponse get(@PathVariable UUID userId) {
        return administration.get(userId);
    }

    @PostMapping("/api/v1/admin/users")
    public ResponseEntity<Object> create(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest http
    ) {
        return pendingOrOk(administration.createUser(
                Actor.from(authentication),
                request,
                idempotencyKey,
                CorrelationIdFilter.current(http)
        ));
    }

    @PostMapping("/api/v1/admin/users/{userId}/staff")
    public ResponseEntity<Object> onboard(
            JwtAuthenticationToken authentication,
            @PathVariable UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody OnboardStaffRequest request,
            HttpServletRequest http
    ) {
        return pendingOrOk(administration.onboardStaff(
                Actor.from(authentication),
                userId,
                request,
                idempotencyKey,
                CorrelationIdFilter.current(http)
        ));
    }

    @PostMapping("/api/v1/admin/users/{userId}/staff/suspend")
    public ResponseEntity<Object> suspend(
            JwtAuthenticationToken authentication,
            @PathVariable UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http
    ) {
        return pendingOrOk(administration.suspendStaff(
                Actor.from(authentication),
                userId,
                idempotencyKey,
                CorrelationIdFilter.current(http)
        ));
    }

    @GetMapping("/api/v1/admin/users/{userId}/roles")
    public UserDetailResponse roles(@PathVariable UUID userId) {
        return administration.get(userId);
    }

    @PostMapping("/api/v1/admin/users/{userId}/roles")
    public ResponseEntity<Object> assign(
            JwtAuthenticationToken authentication,
            @PathVariable UUID userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RoleChangeRequest request,
            HttpServletRequest http
    ) {
        return pendingOrOk(administration.assignRoles(
                Actor.from(authentication),
                userId,
                request,
                idempotencyKey,
                CorrelationIdFilter.current(http)
        ));
    }

    @DeleteMapping("/api/v1/admin/users/{userId}/roles/{roleName}")
    public ResponseEntity<Object> remove(
            JwtAuthenticationToken authentication,
            @PathVariable UUID userId,
            @PathVariable String roleName,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http
    ) {
        return pendingOrOk(administration.removeRoles(
                Actor.from(authentication),
                userId,
                Set.of(roleName),
                idempotencyKey,
                CorrelationIdFilter.current(http)
        ));
    }

    @GetMapping("/api/v1/admin/operations/{operationId}")
    public OperationResponse operation(@PathVariable UUID operationId) {
        return OperationResponse.from(operations.get(operationId));
    }

    private static ResponseEntity<Object> pendingOrOk(Object body) {
        if (body instanceof OperationResponse operation && !"SUCCEEDED".equals(operation.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .location(URI.create("/api/v1/admin/operations/" + operation.id()))
                    .body(operation);
        }
        return ResponseEntity.ok(body);
    }
}
