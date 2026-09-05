package com.ehr.dto;

import lombok.Data;

@Data
public class UpdateRecordRequest {
    private String diagnosis;
    private String prescription;
    private String medicalHistory;
    private String bloodPressure;
    private String bloodSugar;
}
