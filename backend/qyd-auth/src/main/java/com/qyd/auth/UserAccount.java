package com.qyd.auth;

import com.qyd.shared.domain.BaseEntity;
import com.qyd.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "qyd_user")
public class UserAccount extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;
    @Column(nullable = false)
    private boolean enabled = true;

    protected UserAccount() {}

    public UserAccount(String username, String passwordHash, Role role) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    void update(Role role, boolean enabled) { this.role = role; this.enabled = enabled; }
}
