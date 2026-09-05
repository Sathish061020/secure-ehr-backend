package com.ehr.controller;

import com.ehr.dto.AuthResponse;
import com.ehr.dto.LoginRequest;
import com.ehr.dto.LoginResponse;
import com.ehr.dto.OtpVerifyRequest;
import com.ehr.dto.RefreshTokenRequest;
import com.ehr.dto.RegisterRequest;
import com.ehr.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Step 1: Validate credentials → send OTP email.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest  httpRequest,
            HttpServletResponse httpResponse) {
        LoginResponse result = authService.login(
                request,
                httpRequest.getRemoteAddr(),
                httpRequest,
                httpResponse);
        return ResponseEntity.ok(result);
    }

    /**
     * Step 2: Verify OTP → issue JWT access + refresh tokens.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request,
            HttpServletRequest  httpRequest,
            HttpServletResponse httpResponse) {
        String userAgent = httpRequest.getHeader("User-Agent");
        return ResponseEntity.ok(authService.verifyOtpAndIssueTokens(
                request,
                httpRequest.getRemoteAddr(),
                userAgent,
                httpResponse));
    }

    /**
     * Resend OTP: fires a fresh code to the email without re-validating the password.
     * Safe to call from the OTP page's "Resend" button.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String email = body.getOrDefault("email", "").trim();
        authService.resendOtp(email, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "If that email is registered, a new code has been sent."));
    }

    /**
     * Refresh: swap valid refresh token for a new access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken(), httpRequest.getRemoteAddr()));
    }

    /**
     * Self-service registration: create a new account, no auto-login.
     * Returns 201 Created with a success message; client should redirect to /login.
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        authService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of("message", "Account created successfully. Please sign in."));
    }
}
