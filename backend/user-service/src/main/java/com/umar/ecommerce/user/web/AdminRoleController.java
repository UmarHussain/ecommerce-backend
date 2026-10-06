package com.umar.ecommerce.user.web;

import com.umar.ecommerce.user.application.Actor;
import com.umar.ecommerce.user.application.RoleBundleService;
import com.umar.ecommerce.user.web.dto.CreateRoleBundleRequest;
import com.umar.ecommerce.user.web.dto.RoleDefinitionResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Role catalog")
public class AdminRoleController {

    private final RoleBundleService bundles;

    public AdminRoleController(RoleBundleService bundles) {
        this.bundles = bundles;
    }

    @GetMapping("/api/v1/admin/roles")
    public List<RoleDefinitionResponse> list() {
        return bundles.list();
    }

    @PostMapping("/api/v1/admin/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDefinitionResponse create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateRoleBundleRequest request,
            HttpServletRequest http
    ) {
        return bundles.create(Actor.from(authentication), request, CorrelationIdFilter.current(http));
    }
}
