package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.entity.AppUser;
import com.umar.ecommerce.user.exception.NotFoundException;
import com.umar.ecommerce.user.repository.AppUserRepository;
import com.umar.ecommerce.user.repository.CustomerAddressRepository;
import com.umar.ecommerce.user.repository.CustomerProfileRepository;
import com.umar.ecommerce.user.repository.StaffProfileRepository;
import com.umar.ecommerce.user.web.dto.AddressRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileOwnershipTest {

    @Test
    void addressUpdateIsScopedToTheAuthenticatedUser() {
        AppUserRepository users = mock(AppUserRepository.class);
        CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
        ProfileService profiles = new ProfileService(
                users,
                mock(CustomerProfileRepository.class),
                addresses,
                mock(StaffProfileRepository.class)
        );
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        AppUser owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(userId);
        when(users.findByIssuerAndSubject("issuer", "subject")).thenReturn(Optional.of(owner));
        when(addresses.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        Actor actor = new Actor("issuer", "subject", "owner@example.test", "Owner", Set.of("PERM_profile.update_own"));
        AddressRequest request = new AddressRequest("Home", "1 Main", null, "Town", null, "12345", "US");

        assertThrows(NotFoundException.class, () -> profiles.updateAddress(actor, addressId, request));
        verify(addresses).findByIdAndUserId(addressId, userId);
    }
}
