package com.ehr.service;

import com.ehr.entity.PatientRecord;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FirestorePatientRecordService {

    private final Firestore firestore;

    public void savePatientRecord(PatientRecord record) throws Exception {

        Map<String, Object> data = new HashMap<>();

        data.put("recordId", record.getId());
        data.put("patientId", record.getPatient().getId());

        if (record.getAssignedDoctor() != null) {
            data.put("assignedDoctorId", record.getAssignedDoctor().getId());
        }

        // These values are already AES-256-GCM encrypted
        data.put("diagnosis", record.getDiagnosis());
        data.put("prescription", record.getPrescription());
        data.put("medicalHistory", record.getMedicalHistory());

        data.put("bloodPressure", record.getBloodPressure());
        data.put("bloodSugar", record.getBloodSugar());
        data.put("reasonForVisit", record.getReasonForVisit());

        data.put("createdAt", record.getCreatedAt() != null
                ? record.getCreatedAt().toString()
                : null);

        data.put("updatedAt", record.getUpdatedAt() != null
                ? record.getUpdatedAt().toString()
                : null);

        firestore.collection("patient_records")
                .document(String.valueOf(record.getId()))
                .set(data)
                .get();
    }
}