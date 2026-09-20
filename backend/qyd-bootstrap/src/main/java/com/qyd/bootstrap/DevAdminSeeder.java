package com.qyd.bootstrap;

import com.qyd.auth.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevAdminSeeder implements CommandLineRunner {
    private final AuthService authService;
    private final String username;
    private final String password;

    public DevAdminSeeder(AuthService authService,
                          @Value("${qyd.seed.admin.username:admin}") String username,
                          @Value("${qyd.seed.admin.password:}") String password) {
        this.authService = authService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("QYD_ADMIN_PASSWORD is required in dev profile");
        }
        authService.createPlatformAdminIfAbsent(username, password);
    }
}
