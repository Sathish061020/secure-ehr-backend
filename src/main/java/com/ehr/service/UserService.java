package com.ehr.service;

import com.ehr.dto.CreateUserRequest;
import com.ehr.dto.UserDto;
import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(role)
                .enabled(true)
                .build();

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
                .createdAt(user.getCreatedAt())
                .build();
    }
}
