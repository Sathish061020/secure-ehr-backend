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
 * Seeds the database with default users.
 *
 * ADMIN:
 *   Email: admin@ehr.com
 *   Password: Admin@123
 *
 * The existing ADMIN account is also reset to the known BCrypt password
 * on startup so an old/stale admin password does not prevent login.
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

        User admin = userRepository.findByEmail("admin@ehr.com").orElse(null);

        if (admin == null) {

            admin = User.builder()
                    .email("admin@ehr.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .firstName("System")
                    .lastName("Admin")
                    .phone("0000000000")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build();

            userRepository.save(admin);

            log.info("=================================================");
            log.info("DEFAULT ADMIN CREATED");
            log.info("Email: admin@ehr.com");
            log.info("Password: Admin@123");
            log.info("=================================================");

        } else {

            /*
             * Existing Admin found.
             *
             * Reset the password using BCrypt so an old password
             * stored in the database cannot prevent login.
             */
            admin.setPassword(passwordEncoder.encode("Admin@123"));

            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);

            /*
             * These fields exist in your User entity according to the
             * database columns we inspected.
             */
            admin.setActive(true);
            admin.setAccountLocked(false);
            admin.setTempPassword(false);

            userRepository.save(admin);

            log.info("=================================================");
            log.info("EXISTING ADMIN ACCOUNT RESET");
            log.info("Email: admin@ehr.com");
            log.info("Password: Admin@123");
            log.info("Account enabled: true");
            log.info("Account active: true");
            log.info("Account locked: false");
            log.info("=================================================");
        }
    }

    private void seedSampleUsers() {

        // =========================================================
        // SAMPLE DOCTOR
        // =========================================================

        if (!userRepository.existsByEmail("doctor@ehr.com")) {

            userRepository.save(
                    User.builder()
                            .email("doctor@ehr.com")
                            .password(passwordEncoder.encode("Doctor@123"))
                            .firstName("Ravi")
                            .lastName("Kumar")
                            .phone("9876543210")
                            .role(Role.DOCTOR)
                            .enabled(true)
                            .build()
            );

            log.info("Sample DOCTOR created: doctor@ehr.com / Doctor@123");
        }

        // =========================================================
        // SAMPLE PATIENT
        // =========================================================

        if (!userRepository.existsByEmail("patient@ehr.com")) {

            userRepository.save(
                    User.builder()
                            .email("patient@ehr.com")
                            .password(passwordEncoder.encode("Patient@123"))
                            .firstName("Priya")
                            .lastName("Sharma")
                            .phone("9123456789")
                            .role(Role.PATIENT)
                            .enabled(true)
                            .build()
            );

            log.info("Sample PATIENT created: patient@ehr.com / Patient@123");
        }

        // =========================================================
        // SAMPLE STAFF
        // =========================================================

        if (!userRepository.existsByEmail("staff@ehr.com")) {

            userRepository.save(
                    User.builder()
                            .email("staff@ehr.com")
                            .password(passwordEncoder.encode("Staff@123"))
                            .firstName("Anjali")
                            .lastName("Nair")
                            .phone("9012345678")
                            .role(Role.STAFF)
                            .enabled(true)
                            .build()
            );

            log.info("Sample STAFF created: staff@ehr.com / Staff@123");
        }
    }
}