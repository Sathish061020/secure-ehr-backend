package com.ehr.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetPasswordRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    private String tempPassword;

    @NotBlank @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;
}
