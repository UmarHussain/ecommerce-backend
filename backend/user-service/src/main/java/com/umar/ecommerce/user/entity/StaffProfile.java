package com.umar.ecommerce.user.entity;

import com.umar.ecommerce.user.domain.OnboardingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_profile")
public class StaffProfile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "employee_reference")
    private String employeeReference;

    @Column(name = "department")
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false)
    private OnboardingStatus onboardingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffProfile() {
    }

    public StaffProfile(UUID userId, String employeeReference, String department) {
        this.userId = userId;
        this.employeeReference = employeeReference;
        this.department = department;
        this.onboardingStatus = OnboardingStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void update(String employeeReference, String department) {
        this.employeeReference = employeeReference;
        this.department = department;
    }

    public void suspend() {
        this.onboardingStatus = OnboardingStatus.SUSPENDED;
    }

    public void activate() {
        this.onboardingStatus = OnboardingStatus.ACTIVE;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmployeeReference() {
        return employeeReference;
    }

    public String getDepartment() {
        return department;
    }

    public OnboardingStatus getOnboardingStatus() {
        return onboardingStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
