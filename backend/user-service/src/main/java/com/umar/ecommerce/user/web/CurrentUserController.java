package com.umar.ecommerce.user.web;

import com.umar.ecommerce.user.application.Actor;
import com.umar.ecommerce.user.application.ProfileService;
import com.umar.ecommerce.user.web.dto.AddressRequest;
import com.umar.ecommerce.user.web.dto.AddressResponse;
import com.umar.ecommerce.user.web.dto.ProfileResponse;
import com.umar.ecommerce.user.web.dto.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Own profile")
public class CurrentUserController {

    private final ProfileService profiles;

    public CurrentUserController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/v1/users/me")
    @Operation(summary = "Idempotently initialize and return the caller's application profile")
    public ProfileResponse me(JwtAuthenticationToken authentication) {
        return profiles.initializeOwnProfile(Actor.from(authentication));
    }

    @PatchMapping("/api/v1/users/me")
    public ProfileResponse update(JwtAuthenticationToken authentication, @Valid @RequestBody ProfileUpdateRequest request) {
        return profiles.updateOwnProfile(Actor.from(authentication), request);
    }

    @PostMapping("/api/v1/users/me/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse addAddress(JwtAuthenticationToken authentication, @Valid @RequestBody AddressRequest request) {
        return profiles.addAddress(Actor.from(authentication), request);
    }

    @PutMapping("/api/v1/users/me/addresses/{addressId}")
    public AddressResponse updateAddress(
            JwtAuthenticationToken authentication,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return profiles.updateAddress(Actor.from(authentication), addressId, request);
    }

    @DeleteMapping("/api/v1/users/me/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(JwtAuthenticationToken authentication, @PathVariable UUID addressId) {
        profiles.deleteAddress(Actor.from(authentication), addressId);
    }
}
