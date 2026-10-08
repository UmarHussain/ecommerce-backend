package com.umar.ecommerce.inventory.controller;

import com.umar.ecommerce.inventory.dto.request.AdjustStockRequest;
import com.umar.ecommerce.inventory.dto.request.SetupStockRequest;
import com.umar.ecommerce.inventory.dto.response.PageResponse;
import com.umar.ecommerce.inventory.dto.response.StockAdjustmentResponse;
import com.umar.ecommerce.inventory.dto.response.StockItemResponse;
import com.umar.ecommerce.inventory.service.Actor;
import com.umar.ecommerce.inventory.service.CommandOutcome;
import com.umar.ecommerce.inventory.service.StockCommandService;
import com.umar.ecommerce.inventory.service.StockQueryService;
import com.umar.ecommerce.inventory.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@Validated
@Tag(name = "Admin Inventory", description = "Stock setup, adjustments, and history")
@SecurityRequirement(name = "bearerAuth")
public class AdminInventoryController {

    private final StockQueryService queries;
    private final StockCommandService commands;

    public AdminInventoryController(StockQueryService queries, StockCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @PreAuthorize("hasAuthority('PERM_inventory.read')")
    @GetMapping("/stock-items")
    @Operation(summary = "List stock items", description = "Sort fields: sku, onHand, createdAt, updatedAt. Tie-break is id ascending.")
    public PageResponse<StockItemResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "sku,asc") String sort
    ) {
        return queries.list(search, page, size, sort);
    }

    @PreAuthorize("hasAuthority('PERM_inventory.adjust')")
    @PostMapping("/stock-items")
    @Operation(summary = "Set up stock for a catalog variant", description = """
            Requires inventory.adjust and catalog.read. The service loads the variant from catalog with the caller token
            and stores the canonical SKU. A browser-supplied SKU is ignored. Opening balance may be zero.
            Idempotency-Key is required. 201 includes Location.
            """)
    public ResponseEntity<String> setup(
            JwtAuthenticationToken authentication,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String idempotencyKey,
            @Valid @RequestBody SetupStockRequest request,
            HttpServletRequest http
    ) {
        CommandOutcome outcome = commands.setup(
                Actor.from(authentication),
                authentication.getToken(),
                authentication.getToken().getTokenValue(),
                CorrelationIdFilter.correlationId(http),
                idempotencyKey,
                request
        );
        return respond(outcome);
    }

    @PreAuthorize("hasAuthority('PERM_inventory.read')")
    @GetMapping("/stock-items/{id}")
    @Operation(summary = "Get one stock item")
    public StockItemResponse get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PreAuthorize("hasAuthority('PERM_inventory.adjust')")
    @PostMapping("/stock-items/{id}/adjustments")
    @Operation(summary = "Apply a signed stock adjustment", description = """
            Requires expectedVersion and a non-zero delta. Does not re-check catalog activation.
            INBOUND_RECEIPT and RETURN require a positive delta. DAMAGE_LOSS requires a negative delta.
            CORRECTION allows either sign. OPENING_BALANCE is rejected.
            """)
    public ResponseEntity<String> adjust(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key")
            @NotBlank
            @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
            String idempotencyKey,
            @Valid @RequestBody AdjustStockRequest request,
            HttpServletRequest http
    ) {
        CommandOutcome outcome = commands.adjust(
                Actor.from(authentication),
                CorrelationIdFilter.correlationId(http),
                id,
                idempotencyKey,
                request
        );
        return respond(outcome);
    }

    @PreAuthorize("hasAuthority('PERM_inventory.read')")
    @GetMapping("/stock-items/{id}/adjustments")
    @Operation(summary = "List immutable stock history", description = "Default sort is createdAt,desc then id in the same direction.")
    public PageResponse<StockAdjustmentResponse> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return queries.history(id, page, size, sort);
    }

    private static ResponseEntity<String> respond(CommandOutcome outcome) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.status())
                .contentType(MediaType.parseMediaType(outcome.contentType()));
        if (outcome.location() != null) {
            builder.location(URI.create(outcome.location()));
        }
        return builder.body(outcome.body());
    }
}
