package com.ehr.service;

import com.ehr.entity.TrustedDevice;
import com.ehr.entity.User;
import com.ehr.repository.TrustedDeviceRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustedDeviceService {

    private static final String COOKIE_NAME = "trusted_device";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final TrustedDeviceRepository trustedDeviceRepository;

    @Value("${app.trusted-device.cookie-max-age-days:30}")
    private int cookieMaxAgeDays;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Generate a new device token, persist its hash, and write the cookie.
     * Call this immediately after successful OTP verification when the user
     * opts in to "remember this device".
     */
    @Transactional
    public void registerDevice(User user, String userAgent, HttpServletResponse response) {
        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        TrustedDevice device = TrustedDevice.builder()
                .user(user)
                .deviceTokenHash(tokenHash)
                .deviceLabel(truncate(userAgent, 255))
                .build();

        trustedDeviceRepository.save(device);
        writeCookie(rawToken, cookieMaxAgeDays * 24 * 60 * 60, response);

        log.info("Trusted device registered for user {}", user.getEmail());
    }

    /**
     * Check whether the incoming request carries a valid trusted-device cookie
     * for the given user. If valid, updates lastUsedAt and returns true.
     */
    @Transactional
    public boolean isTrustedDevice(User user, HttpServletRequest request) {
        String rawToken = extractCookie(request);
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        String tokenHash = sha256Hex(rawToken);
        Optional<TrustedDevice> deviceOpt =
                trustedDeviceRepository.findByUserAndDeviceTokenHash(user, tokenHash);

        if (deviceOpt.isEmpty()) {
            return false;
        }

        // Refresh last-used timestamp
        TrustedDevice device = deviceOpt.get();
        device.setLastUsedAt(LocalDateTime.now());
        trustedDeviceRepository.save(device);

        log.info("Trusted device recognised for user {}", user.getEmail());
        return true;
    }

    /**
     * Delete all trusted devices for this user and clear the browser cookie.
     * Call when the user clicks "Log out of all devices".
     */
    @Transactional
    public void revokeAllDevices(User user, HttpServletResponse response) {
        trustedDeviceRepository.deleteAllByUser(user);
        writeCookie("", 0, response);   // max-age=0 instructs browser to delete the cookie
        log.info("All trusted devices revoked for user {}", user.getEmail());
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private String generateRawToken() {
        byte[] bytes = new byte[32];   // 256-bit entropy
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);   // 64 hex chars
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void writeCookie(String value, int maxAgeSeconds, HttpServletResponse response) {
        // Build a cookie string manually so we can set SameSite
        String cookieHeader = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=Lax",
                COOKIE_NAME, value, maxAgeSeconds
        );
        response.addHeader("Set-Cookie", cookieHeader);
    }

    private String extractCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String truncate(String s, int max) {
        if (s == null) return "Unknown";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
