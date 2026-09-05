package com.ehr.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ConsentDto {
    private Long   id;
    private Long   patientId;
    private String patientName;
    private Long   doctorId;
    private String doctorName;
    private String doctorEmail;
    private boolean granted;
    private LocalDateTime grantedAt;
    private LocalDateTime revokedAt;
}
