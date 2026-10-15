package com.familyvault.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(length = 100, unique = true)
    private String googleId;

    @Column(length = 500)
    private String avatarUrl;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private long securityVersion = 0;

    @Column(nullable = false)
    private long vaultVersion = 0;

    public long getVaultVersion() { return vaultVersion; }
    public void setVaultVersion(long vaultVersion) { this.vaultVersion = vaultVersion; }

    public long getSecurityVersion() { return securityVersion; }
    public void setSecurityVersion(long securityVersion) { this.securityVersion = securityVersion; }

    @Column(length = 100)
    private String vaultPinHash;

    @Column(nullable = false, columnDefinition = "int default 0")
    private int vaultPinFailedAttempts = 0;

    private LocalDateTime vaultPinLockedUntil;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVaultPinHash() {
        return vaultPinHash;
    }

    public void setVaultPinHash(String vaultPinHash) {
        this.vaultPinHash = vaultPinHash;
    }

    public int getVaultPinFailedAttempts() {
        return vaultPinFailedAttempts;
    }

    public void setVaultPinFailedAttempts(int vaultPinFailedAttempts) {
        this.vaultPinFailedAttempts = vaultPinFailedAttempts;
    }

    public LocalDateTime getVaultPinLockedUntil() {
        return vaultPinLockedUntil;
    }

    public void setVaultPinLockedUntil(LocalDateTime vaultPinLockedUntil) {
        this.vaultPinLockedUntil = vaultPinLockedUntil;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
}
