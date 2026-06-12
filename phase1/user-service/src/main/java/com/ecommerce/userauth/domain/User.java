package com.ecommerce.userauth.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root per docs/lld/user-auth-lld.md §3.1. Maps to the {@code users} table
 * (V1__init.sql). Every command validates {@link #status} against the transition rules
 * in §5 and throws {@link IllegalStateTransitionException} on an illegal transition.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @Convert(converter = EmailConverter.class)
    @Column(name = "email", nullable = false, unique = true)
    private Email email;

    @Convert(converter = PasswordHashConverter.class)
    @Column(name = "password_hash", nullable = false)
    private PasswordHash passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserAddress> addresses = new ArrayList<>();

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
        // JPA
    }

    private User(UUID id, Email email, PasswordHash passwordHash, String fullName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = UserStatus.UNVERIFIED;
        this.role = UserRole.CUSTOMER;
    }

    /**
     * T-UA entry point — creates a new {@code UNVERIFIED}/{@code CUSTOMER} user.
     * Caller (AuthService) is responsible for the {@code UserRegistered} outbox event
     * and the {@code email_verifications} row.
     */
    public static User register(Email email, String rawPassword, String fullName) {
        return new User(UserId.newId().value(), email, PasswordHash.hash(rawPassword), fullName);
    }

    /**
     * T-UA-01: {@code UNVERIFIED -> ACTIVE}. Idempotent — re-verifying an already
     * {@code ACTIVE} account is a no-op (verification links may be clicked twice).
     */
    public void verifyEmail() {
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateTransitionException("Cannot verify email for a deactivated account");
        }
        if (status == UserStatus.ACTIVE) {
            return;
        }
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = Instant.now();
    }

    /**
     * Login guard per §5: permitted only when {@code status = ACTIVE}.
     *
     * @throws IllegalStateTransitionException if the account is not {@code ACTIVE}
     * @throws InvalidCredentialsException     if {@code rawPassword} does not match
     */
    public void login(String rawPassword) {
        if (status == UserStatus.UNVERIFIED) {
            throw new IllegalStateTransitionException("Account not verified");
        }
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateTransitionException("Account deactivated");
        }
        if (!passwordHash.matches(rawPassword)) {
            throw new InvalidCredentialsException();
        }
    }

    /**
     * @throws IllegalStateTransitionException if the account is {@code DEACTIVATED}
     * @throws InvalidCredentialsException     if {@code currentRaw} does not match
     */
    public void changePassword(String currentRaw, String newRaw) {
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateTransitionException("Account deactivated");
        }
        if (!passwordHash.matches(currentRaw)) {
            throw new InvalidCredentialsException();
        }
        this.passwordHash = PasswordHash.hash(newRaw);
    }

    /**
     * Applies a password reset (token validation against {@code password_reset_tokens}
     * is the responsibility of {@code PasswordResetService}).
     *
     * @throws IllegalStateTransitionException if the account is {@code DEACTIVATED}
     */
    public void resetPassword(String newRaw) {
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateTransitionException("Account deactivated");
        }
        this.passwordHash = PasswordHash.hash(newRaw);
    }

    /**
     * T-UA-02 / T-UA-03: {@code ACTIVE|UNVERIFIED -> DEACTIVATED}. Terminal — no
     * reactivation flow per §5. Revoking refresh-token sessions and blacklisting the
     * current access token's {@code jti} is the caller's responsibility (§6.2).
     */
    public void deactivate() {
        if (status == UserStatus.DEACTIVATED) {
            throw new IllegalStateTransitionException("Account already deactivated");
        }
        this.status = UserStatus.DEACTIVATED;
        this.deactivatedAt = Instant.now();
    }

    public void addAddress(UserAddress address) {
        address.assignTo(this);
        this.addresses.add(address);
    }

    /**
     * INV-UA-01: setting {@code isDefault = true} on one address atomically clears the
     * flag on all other addresses of this user.
     *
     * @throws java.util.NoSuchElementException if {@code addressId} is not one of this
     *                                           user's addresses
     */
    public void setDefaultAddress(UUID addressId) {
        UserAddress target = addresses.stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new java.util.NoSuchElementException("Address not found: " + addressId));

        for (UserAddress address : addresses) {
            if (address == target) {
                address.markDefault();
            } else {
                address.clearDefault();
            }
        }
    }

    public void removeAddress(UUID addressId) {
        addresses.stream()
                .filter(a -> a.getId().equals(addressId))
                .findFirst()
                .ifPresent(UserAddress::markDeleted);
    }

    public UUID getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public UserStatus getStatus() {
        return status;
    }

    public UserRole getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    public List<UserAddress> getAddresses() {
        return List.copyOf(addresses);
    }

    public Optional<Instant> getEmailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    public Optional<Instant> getDeactivatedAt() {
        return Optional.ofNullable(deactivatedAt);
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Optional<Instant> getDeletedAt() {
        return Optional.ofNullable(deletedAt);
    }
}
