package com.ehr.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReceiptDto {
    private Long   appointmentId;
    private String patientName;
    private String healthId;
    private Integer age;
    private String sex;
    private String mobile;
    private String address;
    private String department;
    private String doctorName;
    private LocalDate appointmentDate;
    private Integer tokenNumber;
}

