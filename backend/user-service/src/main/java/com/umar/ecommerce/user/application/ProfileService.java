package com.umar.ecommerce.user.application;

import com.umar.ecommerce.user.domain.OnboardingStatus;
import com.umar.ecommerce.user.entity.AppUser;
import com.umar.ecommerce.user.entity.CustomerAddress;
import com.umar.ecommerce.user.entity.CustomerProfile;
import com.umar.ecommerce.user.entity.StaffProfile;
import com.umar.ecommerce.user.exception.NotFoundException;
import com.umar.ecommerce.user.repository.AppUserRepository;
import com.umar.ecommerce.user.repository.CustomerAddressRepository;
import com.umar.ecommerce.user.repository.CustomerProfileRepository;
import com.umar.ecommerce.user.repository.StaffProfileRepository;
import com.umar.ecommerce.user.web.dto.AddressRequest;
import com.umar.ecommerce.user.web.dto.AddressResponse;
import com.umar.ecommerce.user.web.dto.ProfileResponse;
import com.umar.ecommerce.user.web.dto.ProfileUpdateRequest;
import com.umar.ecommerce.user.web.dto.StaffProfileResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileService {

    private final AppUserRepository users;
    private final CustomerProfileRepository customers;
    private final CustomerAddressRepository addresses;
    private final StaffProfileRepository staff;

    public ProfileService(
            AppUserRepository users,
            CustomerProfileRepository customers,
            CustomerAddressRepository addresses,
            StaffProfileRepository staff
    ) {
        this.users = users;
        this.customers = customers;
        this.addresses = addresses;
        this.staff = staff;
    }

    @Transactional
    public ProfileResponse initializeOwnProfile(Actor actor) {
        AppUser user = findOrCreate(actor.issuer(), actor.subject(), actor.displayName(), actor.email());
        user.refreshSnapshot(actor.displayName(), actor.email());
        ensureCustomerProfile(user.getId());
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse updateOwnProfile(Actor actor, ProfileUpdateRequest request) {
        AppUser user = requireOwn(actor);
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.refreshSnapshot(request.displayName().trim(), actor.email());
        }
        CustomerProfile profile = ensureCustomerProfile(user.getId());
        if (request.preferences() != null) {
            profile.replacePreferences(request.preferences());
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getById(UUID userId) {
        return toResponse(users.findById(userId).orElseThrow(() -> new NotFoundException("User was not found")));
    }

    @Transactional
    public AppUser requireLinked(String issuer, String subject, String displayName, String email) {
        return findOrCreate(issuer, subject, displayName, email);
    }

    @Transactional
    public AddressResponse addAddress(Actor actor, AddressRequest request) {
        AppUser user = requireOwn(actor);
        ensureCustomerProfile(user.getId());
        CustomerAddress address = addresses.save(new CustomerAddress(
                user.getId(),
                request.label(),
                request.line1(),
                request.line2(),
                request.city(),
                request.region(),
                request.postalCode(),
                request.countryCode()
        ));
        return AddressResponse.from(address);
    }

    @Transactional
    public AddressResponse updateAddress(Actor actor, UUID addressId, AddressRequest request) {
        AppUser user = requireOwn(actor);
        CustomerAddress address = addresses.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new NotFoundException("Address was not found"));
        address.apply(
                request.label(),
                request.line1(),
                request.line2(),
                request.city(),
                request.region(),
                request.postalCode(),
                request.countryCode()
        );
        return AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(Actor actor, UUID addressId) {
        AppUser user = requireOwn(actor);
        CustomerAddress address = addresses.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new NotFoundException("Address was not found"));
        addresses.delete(address);
    }

    @Transactional
    public StaffProfile ensureStaffProfile(UUID userId, String employeeReference, String department) {
        return staff.findById(userId).map(existing -> {
            existing.update(employeeReference, department);
            if (existing.getOnboardingStatus() == OnboardingStatus.SUSPENDED) {
                existing.activate();
            }
            return existing;
        }).orElseGet(() -> staff.save(new StaffProfile(userId, employeeReference, department)));
    }

    private AppUser requireOwn(Actor actor) {
        return users.findByIssuerAndSubject(actor.issuer(), actor.subject())
                .orElseGet(() -> findOrCreate(actor.issuer(), actor.subject(), actor.displayName(), actor.email()));
    }

    private AppUser findOrCreate(String issuer, String subject, String displayName, String email) {
        return users.findByIssuerAndSubject(issuer, subject).orElseGet(() -> {
            try {
                return users.saveAndFlush(new AppUser(issuer, subject, displayName, email));
            } catch (DataIntegrityViolationException duplicate) {
                return users.findByIssuerAndSubject(issuer, subject)
                        .orElseThrow(() -> duplicate);
            }
        });
    }

    private CustomerProfile ensureCustomerProfile(UUID userId) {
        return customers.findById(userId).orElseGet(() -> customers.save(new CustomerProfile(userId)));
    }

    private ProfileResponse toResponse(AppUser user) {
        CustomerProfile customer = customers.findById(user.getId()).orElse(null);
        List<AddressResponse> addressList = addresses.findByUserIdOrderByCreatedAtAsc(user.getId())
                .stream()
                .map(AddressResponse::from)
                .toList();
        StaffProfile staffProfile = staff.findById(user.getId()).orElse(null);
        return new ProfileResponse(
                user.getId(),
                user.getIssuer(),
                user.getSubject(),
                user.getEmailSnapshot(),
                user.getDisplayName(),
                customer == null ? Map.of() : customer.getPreferences(),
                addressList,
                staffProfile == null ? null : new StaffProfileResponse(
                        staffProfile.getEmployeeReference(),
                        staffProfile.getDepartment(),
                        staffProfile.getOnboardingStatus().name()
                ),
                true
        );
    }
}
