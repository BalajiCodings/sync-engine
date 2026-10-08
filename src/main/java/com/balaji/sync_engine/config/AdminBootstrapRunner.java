package com.balaji.sync_engine.config;

import com.balaji.sync_engine.security.Role;
import com.balaji.sync_engine.security.User;
import com.balaji.sync_engine.security.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.username:admin}")
    private String bootstrapUsername;

    @Value("${admin.bootstrap.password:}")
    private String bootstrapPassword;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        boolean anyAdminExists = userRepository.findByUsername(bootstrapUsername).isPresent();
        if (anyAdminExists) {
            return;
        }

        if (bootstrapPassword.isBlank()) {
            log.warn("No ADMIN account exists and admin.bootstrap.password is not set — "
                    + "skipping admin bootstrap. Set ADMIN_BOOTSTRAP_PASSWORD to seed one.");
            return;
        }

        User admin = new User();
        admin.setUsername(bootstrapUsername);
        admin.setPasswordHash(passwordEncoder.encode(bootstrapPassword));
        admin.setRole(Role.ADMIN);
        admin.setDeviceId(null);
        admin.setEnabled(true);
        userRepository.save(admin);

        log.info("Bootstrapped initial ADMIN account '{}'. Log in and rotate this password "
                + "via a proper credential-management flow before any real deployment.", bootstrapUsername);
    }
}