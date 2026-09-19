package com.ehr.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserDto {
    private Long   id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String role;
    private boolean enabled;
    private boolean accountLocked;
    private boolean active;
    private boolean isTempPassword;
    private String healthId;
    private String department;
    private Integer age;
    private String sex;
    private String address;
    private LocalDateTime createdAt;
}

