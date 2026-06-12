package com.ecommerce.userauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Child entity of {@link User}, per docs/lld/user-auth-lld.md §3.2. Maps to the
 * {@code user_addresses} table (V1__init.sql). {@code isDefault} is mutated only via
 * {@link User#setDefaultAddress(UUID)} to enforce INV-UA-01.
 */
@Entity
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "line1", nullable = false)
    private String line1;

    @Column(name = "line2")
    private String line2;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "pincode", nullable = false)
    private String pincode;

    @Column(name = "country", nullable = false)
    private String country;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserAddress() {
        // JPA
    }

    public UserAddress(String line1, String line2, String city, String state,
                        String pincode, String country) {
        this.id = UUID.randomUUID();
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.pincode = pincode;
        this.country = (country == null || country.isBlank()) ? "IN" : country;
        this.isDefault = false;
    }

    void assignTo(User owner) {
        this.user = owner;
    }

    void markDefault() {
        this.isDefault = true;
    }

    void clearDefault() {
        this.isDefault = false;
    }

    void markDeleted() {
        this.deletedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPincode() {
        return pincode;
    }

    public String getCountry() {
        return country;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
