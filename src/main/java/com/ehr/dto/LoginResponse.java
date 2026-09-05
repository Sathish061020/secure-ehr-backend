package com.ehr.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Returned by POST /api/auth/login.
 *
 * trusted=true  → device is recognised, authData carries the full JWT response,
 *                  frontend skips the OTP page entirely.
 * trusted=false → OTP was dispatched, message explains next step,
 *                  authData is null.
 */
@Data
@Builder
public class LoginResponse {
    private boolean trusted;
    private String  message;
    private AuthResponse authData;
}
