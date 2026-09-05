package com.ehr.config;

import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with a default ADMIN user on first startup.
 * Change the default credentials immediately after first login.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedSampleUsers();
    }

    private void seedAdmin() {
        if (!userRepository.existsByEmail("admin@ehr.com")) {
            User admin = User.builder()
                    .email("admin@ehr.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .firstName("System")
                    .lastName("Admin")
                    .phone("0000000000")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("=== Default ADMIN created: admin@ehr.com / Admin@123 ===");
            log.info("=== CHANGE THIS PASSWORD IMMEDIATELY AFTER FIRST LOGIN ===");
        }
    }

    private void seedSampleUsers() {
        // Seed a sample doctor
        if (!userRepository.existsByEmail("doctor@ehr.com")) {
            userRepository.save(User.builder()
                    .email("doctor@ehr.com")
                    .password(passwordEncoder.encode("Doctor@123"))
                    .firstName("Ravi")
                    .lastName("Kumar")
                    .phone("9876543210")
                    .role(Role.DOCTOR)
                    .enabled(true)
                    .build());
            log.info("Sample DOCTOR created: doctor@ehr.com / Doctor@123");
        }

        // Seed a sample patient
        if (!userRepository.existsByEmail("patient@ehr.com")) {
            userRepository.save(User.builder()
                    .email("patient@ehr.com")
                    .password(passwordEncoder.encode("Patient@123"))
                    .firstName("Priya")
                    .lastName("Sharma")
                    .phone("9123456789")
                    .role(Role.PATIENT)
                    .enabled(true)
                    .build());
            log.info("Sample PATIENT created: patient@ehr.com / Patient@123");
        }

        // Seed a sample staff member
        if (!userRepository.existsByEmail("staff@ehr.com")) {
            userRepository.save(User.builder()
                    .email("staff@ehr.com")
                    .password(passwordEncoder.encode("Staff@123"))
                    .firstName("Anjali")
                    .lastName("Nair")
                    .phone("9012345678")
                    .role(Role.STAFF)
                    .enabled(true)
                    .build());
            log.info("Sample STAFF created: staff@ehr.com / Staff@123");
        }
    }
}
