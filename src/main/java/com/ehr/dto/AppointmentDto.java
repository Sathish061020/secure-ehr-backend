package com.ehr.dto;

import com.ehr.entity.AppointmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AppointmentDto {
    private Long   id;
    private Long   patientId;
    private String patientName;
    private String healthId;
    private Long   doctorId;
    private String doctorName;
    private LocalDate appointmentDate;
    private AppointmentStatus status;
    private Integer tokenNumber;
    private LocalDateTime createdAt;
}
