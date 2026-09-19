package com.ehr.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PatientRecordDto {
    private Long   id;
    private Long   patientId;
    private String patientFirstName;
    private String patientLastName;
    private String patientEmail;
    private String patientPhone;
    private String healthId;
    private Integer age;
    private String sex;
    private String address;
    private Long   assignedDoctorId;

    private String assignedDoctorName;
    // Sensitive fields — only populated when caller has FULL access (assigned doctor or consent)
    private String diagnosis;
    private String prescription;
    private String medicalHistory;
    // Non-sensitive vitals
    private String bloodPressure;
    private String bloodSugar;
    private String reasonForVisit;
    /**
     * "FULL" — assigned doctor or consent granted.
     * "READ_ONLY" — confirmed appointment only, no consent.
     * null — own record view (PATIENT).
     */
    private String accessLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

