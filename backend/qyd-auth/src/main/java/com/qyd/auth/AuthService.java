package com.qyd.auth;

import com.qyd.shared.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Duration refreshTtl;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, JwtService jwt,
                       @Value("${qyd.security.jwt.refresh-ttl:P30D}") Duration refreshTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public Tokens login(String username, String password) {
        UserAccount user = users.findByUsername(username)
                .filter(UserAccount::isEnabled)
                .filter(it -> encoder.matches(password, it.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return issue(user);
    }

    @Transactional
    public Tokens refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .filter(it -> !it.isRevoked() && it.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        UserAccount user = users.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Unknown user"));
        stored.revoke();
        return issue(user);
    }

    private Tokens issue(UserAccount user) {
        String rawRefresh = UUID.randomUUID() + "." + UUID.randomUUID();
        refreshTokens.save(new RefreshToken(hash(rawRefresh), user.getId(), Instant.now().plus(refreshTtl)));
        return new Tokens(jwt.issue(user), rawRefresh, "Bearer");
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public void createPlatformAdminIfAbsent(String username, String password) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new UserAccount(username, encoder.encode(password), Role.PLATFORM_ADMIN));
        }
    }

    public record Tokens(String accessToken, String refreshToken, String tokenType) {}
}
