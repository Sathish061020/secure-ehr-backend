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
    private Long   assignedDoctorId;
    private String assignedDoctorName;
    // Sensitive fields — only populated when caller has consent
    private String diagnosis;
    private String prescription;
    private String medicalHistory;
    // Non-sensitive vitals
    private String bloodPressure;
    private String bloodSugar;
    private String reasonForVisit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
