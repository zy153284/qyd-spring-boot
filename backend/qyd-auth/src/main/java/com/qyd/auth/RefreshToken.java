package com.qyd.auth;

import com.qyd.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_token")
public class RefreshToken extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean revoked;

    protected RefreshToken() {}
    public RefreshToken(String tokenHash, String userId, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }
    public String getTokenHash() { return tokenHash; }
    public String getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void revoke() { revoked = true; }
}
