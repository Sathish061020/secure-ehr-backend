package com.ehr.service;

import com.ehr.dto.AuthResponse;
import com.ehr.dto.LoginRequest;
import com.ehr.dto.LoginResponse;
import com.ehr.dto.OtpVerifyRequest;
import com.ehr.dto.RegisterRequest;
import com.ehr.dto.SetPasswordRequest;

import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepository;
    private final OtpService            otpService;
    private final JwtService            jwtService;
    private final AuditService          auditService;
    private final PasswordEncoder       passwordEncoder;
    private final TrustedDeviceService  trustedDeviceService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.rate-limit.max-login-attempts}")
    private int maxAttempts;

    @Value("${app.rate-limit.lock-duration-minutes}")
    private int lockDurationMinutes;

    @Value("${app.admin.allowed-emails:admin@ehr.com}")
    private String allowedAdminEmailsStr;


    // ── Step 1 ────────────────────────────────────────────────────────────

    /**
     * Validate credentials.
     * <ul>
     *   <li>If the browser carries a valid trusted-device cookie → skip OTP,
     *       issue JWT immediately and return {@code trusted=true}.</li>
     *   <li>Otherwise → send OTP and return {@code trusted=false}.</li>
     * </ul>
     */
    @Transactional
    public LoginResponse login(LoginRequest request,
                               String ipAddress,
                               HttpServletRequest  httpRequest,
                               HttpServletResponse httpResponse) {

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user == null) {
            log.warn("LOGIN FAILED: User not found for email: {}", request.getEmail());
            auditService.log("ANONYMOUS", "UNKNOWN", "LOGIN_ATTEMPT",
                    request.getEmail(), "Unknown email", false, ipAddress);
            throw new EhrException("Invalid credentials", 401);
        }

        // ── Active check ──────────────────────────────────────────────────
        if (!user.isActive()) {
            log.warn("LOGIN FAILED for {}: Account is deactivated", user.getEmail());
            auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_BLOCKED",
                    null, "Account deactivated", false, ipAddress);
            throw new EhrException("Account is deactivated", 403);
        }

        // ── Admin whitelist check ─────────────────────────────────────────
        if (user.getRole() == Role.ADMIN) {
            List<String> allowed = Arrays.stream(allowedAdminEmailsStr.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            if (!allowed.contains(user.getEmail().trim().toLowerCase())) {
                log.warn("LOGIN FAILED for {}: Admin email not in allowed list [{}]", user.getEmail(), allowedAdminEmailsStr);
                auditService.log(user.getEmail(), "ADMIN", "LOGIN_REJECTED",
                        null, "Admin email not in allowed-emails whitelist", false, ipAddress);
                throw new EhrException("Admin login rejected: email not authorized", 403);
            }
        }

        // ── Account lock check ────────────────────────────────────────────
        if (user.isAccountLocked()) {
            LocalDateTime unlockTime = user.getLockTime() != null
                    ? user.getLockTime().plusMinutes(lockDurationMinutes)
                    : null;

            if (unlockTime != null && LocalDateTime.now().isBefore(unlockTime)) {
                log.warn("LOGIN FAILED for {}: Account locked until {}", user.getEmail(), unlockTime);
                auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_BLOCKED",
                        null, "Account locked due to repeated failures", false, ipAddress);
                throw new EhrException("Account temporarily locked. Try again later.", 423);
            }
            // Auto-unlock after duration
            user.setAccountLocked(false);
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
        }

        // ── Password validation ───────────────────────────────────────────
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxAttempts) {
                user.setAccountLocked(true);
                user.setLockTime(LocalDateTime.now());
                userRepository.save(user);
                log.warn("LOGIN FAILED for {}: Account locked after {} failed attempts", user.getEmail(), attempts);
                auditService.log(user.getEmail(), user.getRole().name(), "ACCOUNT_LOCKED",
                        null, "Locked after " + attempts + " failed attempts", false, ipAddress);
                throw new EhrException("Account locked after too many failed attempts.", 423);
            }

            userRepository.save(user);
            log.warn("LOGIN FAILED for {}: Password mismatch (attempt {}/{})", user.getEmail(), attempts, maxAttempts);
            auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_ATTEMPT",
                    null, "Wrong password. Attempt " + attempts + "/" + maxAttempts, false, ipAddress);
            throw new EhrException("Invalid credentials", 401);
        }

        // Credentials valid — reset failure counter
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        // ── Temp Password Check (Skip OTP) ────────────────────────────────
        if (user.isTempPassword()) {
            auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_TEMP_PASSWORD",
                    null, "Login with temporary password — password reset required", true, ipAddress);

            String accessToken  = jwtService.generateAccessToken(user.getEmail(), user.getRole());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail());

            AuthResponse authData = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .role(user.getRole().name())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .userId(user.getId())
                    .isTempPassword(true)
                    .healthId(user.getHealthId())
                    .build();

            return LoginResponse.builder()
                    .trusted(true)
                    .isTempPassword(true)
                    .message("Temporary password verified. Please set a new password.")
                    .authData(authData)
                    .build();
        }

        // ── Trusted device check ──────────────────────────────────────────
        if (trustedDeviceService.isTrustedDevice(user, httpRequest)) {
            String accessToken  = jwtService.generateAccessToken(user.getEmail(), user.getRole());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail());

            auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_SUCCESS",
                    null, "JWT issued via trusted device (OTP skipped)", true, ipAddress);

            AuthResponse authData = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .role(user.getRole().name())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .userId(user.getId())
                    .isTempPassword(false)
                    .healthId(user.getHealthId())
                    .build();

            return LoginResponse.builder()
                    .trusted(true)
                    .isTempPassword(false)
                    .authData(authData)
                    .build();
        }


        // ── Normal OTP flow ───────────────────────────────────────────────
        otpService.generateAndSendOtp(user.getEmail());
        auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_OTP_SENT",
                null, "Credentials verified, OTP dispatched", true, ipAddress);

        return LoginResponse.builder()
                .trusted(false)
                .message("Verification code sent to your registered email address")
                .build();
    }

    // ── Resend OTP ────────────────────────────────────────────────────────

    /**
     * Re-send the OTP to the given email without requiring the password again.
     * Only fires if the user exists; always returns success to avoid email enumeration.
     */
    @Transactional
    public void resendOtp(String email, String ipAddress) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Don't reveal whether the email is registered
            log.warn("Resend OTP requested for unknown email: {}", email);
            return;
        }
        otpService.generateAndSendOtp(email);
        auditService.log(email, user.getRole().name(), "OTP_RESENT",
                null, "OTP resent on user request", true, ipAddress);
    }

    // ── Step 2 ────────────────────────────────────────────────────────────

    /**
     * Verify OTP and issue JWT tokens.
     * If {@code request.isRememberDevice()} is true, registers a trusted-device cookie.
     */
    @Transactional
    public AuthResponse verifyOtpAndIssueTokens(OtpVerifyRequest request,
                                                String ipAddress,
                                                String userAgent,
                                                HttpServletResponse httpResponse) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EhrException("User not found", 404));

        boolean valid = otpService.verifyOtp(request.getEmail(), request.getOtp());

        if (!valid) {
            auditService.log(user.getEmail(), user.getRole().name(), "OTP_FAILED",
                    null, "Invalid or expired OTP", false, ipAddress);
            throw new EhrException("Invalid or expired OTP", 401);
        }

        String accessToken  = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        // Optionally register this device
        if (request.isRememberDevice()) {
            trustedDeviceService.registerDevice(user, userAgent, httpResponse);
        }

        auditService.log(user.getEmail(), user.getRole().name(), "LOGIN_SUCCESS",
                null, "JWT issued after OTP verification"
                      + (request.isRememberDevice() ? " (device remembered)" : ""),
                true, ipAddress);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .userId(user.getId())
                .build();
    }

    // ── Refresh ───────────────────────────────────────────────────────────

    public AuthResponse refreshToken(String refreshToken, String ipAddress) {
        try {
            String email = jwtService.extractEmail(refreshToken);
            if (jwtService.isTokenExpired(refreshToken)) {
                throw new EhrException("Refresh token expired", 401);
            }
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new EhrException("User not found", 404));

            String newAccessToken  = jwtService.generateAccessToken(user.getEmail(), user.getRole());
            String newRefreshToken = jwtService.generateRefreshToken(user.getEmail());

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .role(user.getRole().name())
                    .email(user.getEmail())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .userId(user.getId())
                    .build();
        } catch (EhrException e) {
            throw e;
        } catch (Exception e) {
            throw new EhrException("Invalid refresh token", 401);
        }
    }

    // ── Revoke all devices ────────────────────────────────────────────────

    /**
     * Deletes all trusted-device records for the authenticated user and
     * instructs the browser to delete the cookie. The user will need to
     * complete OTP on every login until they opt in again.
     */
    @Transactional
    public void revokeAllDevices(String email, HttpServletResponse httpResponse) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EhrException("User not found", 404));
        trustedDeviceService.revokeAllDevices(user, httpResponse);
        auditService.log(email, user.getRole().name(), "TRUSTED_DEVICES_REVOKED",
                null, "User revoked all trusted devices", true, null);
    }

    // ── Registration ──────────────────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest request, String ipAddress) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EhrException("Email already registered", 409);
        }

        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new EhrException("Invalid role: " + request.getRole(), 400);
        }

        if (role != Role.PATIENT) {
            throw new EhrException("Public registration only permits PATIENT role", 400);
        }

        String full = request.getFullName().trim();
        int spaceIdx = full.indexOf(' ');
        String firstName = spaceIdx > 0 ? full.substring(0, spaceIdx) : full;
        String lastName  = spaceIdx > 0 ? full.substring(spaceIdx + 1) : "";

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(firstName)
                .lastName(lastName)
                .phone("")
                .role(role)
                .enabled(true)
                .active(true)
                .isTempPassword(false)
                .healthId(generateUniqueHealthId())
                .build();

        userRepository.save(user);

        auditService.log(request.getEmail(), role.name(), "USER_REGISTERED",
                request.getEmail(), "Self-service patient registration", true, ipAddress);

        log.info("New patient registered: {} ({})", request.getEmail(), role);
    }

    // ── Set New Password (for temp password users) ───────────────────────

    @Transactional
    public AuthResponse setPassword(SetPasswordRequest request, String ipAddress) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new EhrException("User not found", 404));

        if (!passwordEncoder.matches(request.getTempPassword(), user.getPassword())) {
            throw new EhrException("Temporary password does not match", 400);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTempPassword(false);
        userRepository.save(user);

        auditService.log(user.getEmail(), user.getRole().name(), "PASSWORD_CHANGED",
                null, "Temporary password updated to permanent password", true, ipAddress);

        String accessToken  = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .role(user.getRole().name())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .userId(user.getId())
                .isTempPassword(false)
                .healthId(user.getHealthId())
                .build();
    }


    // ── Health ID generation ──────────────────────────────────────────────

    /**
     * Generates a unique health ID in the format {@code EHR-YYYY-NNNNN}.
     * Uses SecureRandom for the numeric part and retries up to 20 times
     * to guarantee uniqueness before giving up.
     */
    public String generateUniqueHealthId() {
        int year = Year.now().getValue();
        for (int attempt = 0; attempt < 20; attempt++) {
            int num = SECURE_RANDOM.nextInt(100_000); // 0-99999
            String candidate = String.format("EHR-%d-%05d", year, num);
            if (!userRepository.existsByHealthId(candidate)) {
                return candidate;
            }
        }
        throw new EhrException("Could not generate a unique Health ID. Please try again.", 500);
    }
}
