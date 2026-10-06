package com.umar.ecommerce.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "issuer", nullable = false, updatable = false)
    private String issuer;

    @Column(name = "subject", nullable = false, updatable = false)
    private String subject;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "email_snapshot")
    private String emailSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected AppUser() {
    }

    public AppUser(String issuer, String subject, String displayName, String emailSnapshot) {
        this.id = UUID.randomUUID();
        this.issuer = issuer;
        this.subject = subject;
        this.displayName = displayName;
        this.emailSnapshot = emailSnapshot;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void refreshSnapshot(String displayName, String emailSnapshot) {
        this.displayName = displayName;
        this.emailSnapshot = emailSnapshot;
    }

    public UUID getId() {
        return id;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmailSnapshot() {
        return emailSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
