package com.ehr.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class LookupResponseDto {
    private Long patientId;
    private String patientName;
    private Integer age;
    private String sex;
    private String mobile;
    private String healthId;
    private String department;
    private String doctorName;
    private LocalDate visitDate;
    private Integer tokenNumber;
    private String status;
    private long queuePosition;
    private long estimatedWaitMinutes;
    private String accessLevel; // "FULL", "READ_ONLY", or "RESTRICTED"

    // Conditional sensitive fields (null if restricted/staff)
    private String diagnosis;
    private String prescription;
    private String medicalHistory;
}
