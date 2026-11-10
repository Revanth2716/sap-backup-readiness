package com.revzz.sap.readiness.seed;

import com.revzz.sap.readiness.config.AppProperties;
import com.revzz.sap.readiness.domain.AppUser;
import com.revzz.sap.readiness.domain.PortalRole;
import com.revzz.sap.readiness.repo.PlatformRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the demo accounts.
 *
 * <p>The password is hashed with BCrypt at runtime rather than stored as a
 * literal hash in a SQL file, so no reusable hash is committed to the
 * repository.</p>
 */
@Component
@Order(1)
public class DemoUserSeeder implements CommandLineRunner {

    private final PlatformRepository platform;
    private final PasswordEncoder encoder;
    private final AppProperties properties;

    public DemoUserSeeder(PlatformRepository platform, PasswordEncoder encoder,
                          AppProperties properties) {
        this.platform = platform;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.isSeedDemoUsers() || platform.countAccounts() > 0) {
            return;
        }
        create("continuity.lead", "Continuity Lead", PortalRole.OPS_ADMIN);
        create("analyst", "Continuity Analyst", PortalRole.ANALYST);
        create("viewer", "Viewer", PortalRole.VIEWER);
    }

    private void create(String username, String displayName, PortalRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setEnabled(true);
        user.setPasswordHash(encoder.encode(properties.getDemoPassword()));
        platform.insertAccount(user);
    }
}
