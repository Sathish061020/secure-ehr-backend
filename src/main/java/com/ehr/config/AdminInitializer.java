package com.ehr.config;

import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@secureehr.com}")
    private String adminEmail;

    @Value("${app.admin.initial-password:AdminPass123!}")
    private String adminInitialPassword;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        long adminCount = userRepository.countByRole(Role.ADMIN);
        if (adminCount == 0) {
            log.info("No ADMIN user found. Bootstrapping initial admin account for email: {}", adminEmail);
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminInitialPassword))
                    .firstName("Admin")
                    .lastName("User")
                    .phone("0000000000")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .accountLocked(false)
                    .isTempPassword(false)
                    .active(true)
                    .build();

            userRepository.save(admin);
            log.info("Initial ADMIN user created successfully.");
        } else {
            log.info("ADMIN user already exists (count={}). Skipping bootstrap initialization.", adminCount);
        }
    }
}
