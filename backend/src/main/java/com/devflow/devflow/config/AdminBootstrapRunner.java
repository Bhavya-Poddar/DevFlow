package com.devflow.devflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.devflow.devflow.entity.User;
import com.devflow.devflow.repository.UserRepository;
import com.devflow.devflow.util.EmailAddress;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public AdminBootstrapRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${devflow.bootstrap.admin.email:}") String adminEmail,
            @Value("${devflow.bootstrap.admin.password:}") String adminPassword,
            @Value("${devflow.bootstrap.admin.name:}") String adminName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    public void run(String... args) {
        if (isBlank(adminEmail) || isBlank(adminPassword) || isBlank(adminName)) {
            logger.warn("Bootstrap admin skipped: set DEVFLOW_ADMIN_EMAIL, DEVFLOW_ADMIN_PASSWORD, and DEVFLOW_ADMIN_NAME to enable it.");
            return;
        }

        String email = EmailAddress.normalize(adminEmail);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            logger.info("Bootstrap admin already exists; leaving its password and role unchanged.");
            return;
        }

        User admin = new User();
        admin.setName(adminName.trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");

        userRepository.save(admin);
        logger.info("Created the configured bootstrap ADMIN account.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
