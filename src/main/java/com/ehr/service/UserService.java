package com.ehr.service;

import com.ehr.dto.CreateUserRequest;
import com.ehr.dto.UpdateUserRequest;
import com.ehr.dto.UserDto;
import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    private static final String TEMP_PWD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EhrException("User not found", 404));
    }

    /**
     * Admin creates DOCTOR or STAFF user.
     * Generates a 10-character temporary password, sets is_temp_password=true,
     * and emails credentials to the new user.
     */
    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EhrException("Email already in use", 409);
        }
        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EhrException("Invalid role: " + request.getRole(), 400);
        }

        if (role != Role.DOCTOR && role != Role.STAFF) {
            throw new EhrException("Admin can only create DOCTOR or STAFF accounts", 400);
        }

        if (role == Role.DOCTOR && (request.getDepartment() == null || request.getDepartment().isBlank())) {
            throw new EhrException("Department is required for DOCTOR role", 400);
        }

        String tempPassword = generateTempPassword();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(tempPassword))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone() != null ? request.getPhone() : "")
                .department(role == Role.DOCTOR ? request.getDepartment().trim() : null)
                .role(role)
                .enabled(true)
                .active(true)
                .isTempPassword(true)
                .build();

        User saved = userRepository.save(user);

        // Email temporary password to new user
        otpService.sendTempPasswordEmail(saved.getEmail(), tempPassword);

        return toDto(saved);
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EhrException("User not found", 404));

        Role newRole;
        try {
            newRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new EhrException("Invalid role: " + request.getRole(), 400);
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setRole(newRole);
        if (newRole == Role.DOCTOR && request.getDepartment() != null) {
            user.setDepartment(request.getDepartment().trim());
        } else if (newRole != Role.DOCTOR) {
            user.setDepartment(null);
        }

        return toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EhrException("User not found", 404));
        userRepository.delete(user);
    }

    @Transactional
    public UserDto toggleUserLock(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EhrException("User not found", 404));
        user.setAccountLocked(!user.isAccountLocked());
        if (!user.isAccountLocked()) {
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
        }
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserDto toggleUserActive(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EhrException("User not found", 404));
        user.setActive(!user.isActive());
        return toDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getUserStats() {
        return Map.of(
                "total",    userRepository.count(),
                "doctors",  userRepository.countByRole(Role.DOCTOR),
                "patients", userRepository.countByRole(Role.PATIENT),
                "staff",    userRepository.countByRole(Role.STAFF),
                "admins",   userRepository.countByRole(Role.ADMIN)
        );
    }

    @Transactional(readOnly = true)
    public List<UserDto> getDoctors() {
        return userRepository.findByRole(Role.DOCTOR).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .accountLocked(user.isAccountLocked())
                .active(user.isActive())
                .isTempPassword(user.isTempPassword())
                .healthId(user.getHealthId())
                .department(user.getDepartment())
                .age(user.getAge())
                .sex(user.getSex())
                .address(user.getAddress())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PWD_CHARS.charAt(RANDOM.nextInt(TEMP_PWD_CHARS.length())));
        }
        return sb.toString();
    }
}

