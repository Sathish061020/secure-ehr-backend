package com.ehr.service;

import com.ehr.dto.PatientRecordDto;
import com.ehr.dto.UpdateRecordRequest;
import com.ehr.entity.PatientRecord;
import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.ConsentRepository;
import com.ehr.repository.PatientRecordRepository;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientRecordService {

    private final PatientRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public PatientRecord createShellRecord(User patient, Long doctorId, String reasonForVisit) {

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new EhrException("Doctor not found", 404));

        if (doctor.getRole() != Role.DOCTOR) {
            throw new EhrException("Assigned user is not a doctor", 400);
        }

        if (recordRepository.findByPatient(patient).isPresent()) {
            throw new EhrException("A record already exists for this patient", 409);
        }

        PatientRecord record = PatientRecord.builder()
                .patient(patient)
                .assignedDoctor(doctor)
                .reasonForVisit(reasonForVisit)
                .build();

        return recordRepository.save(record);
    }

    /**
     * DOCTOR — get decrypted record. Enforces consent check.
     */
    @Transactional(readOnly = true)
    public PatientRecordDto getRecordForDoctor(Long patientId, Long doctorId) {

        User patient = getUser(patientId, "Patient");
        User doctor = getUser(doctorId, "Doctor");

        if (!consentRepository.existsByPatientAndDoctorAndGrantedTrue(patient, doctor)) {
            throw new EhrException("Access denied: patient consent not granted", 403);
        }

        PatientRecord record = recordRepository.findByPatient(patient)
                .orElseThrow(() -> new EhrException("Patient record not found", 404));

        return toDecryptedDto(record);
    }

    /**
     * PATIENT — view own record (decrypted).
     */
    @Transactional(readOnly = true)
    public PatientRecordDto getOwnRecord(Long patientId) {

        User patient = getUser(patientId, "Patient");

        PatientRecord record = recordRepository.findByPatient(patient)
                .orElseThrow(() -> new EhrException("Your record was not found", 404));

        return toDecryptedDto(record);
    }

    /**
     * DOCTOR — update encrypted medical fields. Enforces consent.
     */
    @Transactional
    public PatientRecordDto updateRecord(
            Long patientId,
            Long doctorId,
            UpdateRecordRequest request) {

        User patient = getUser(patientId, "Patient");
        User doctor = getUser(doctorId, "Doctor");

        if (!consentRepository.existsByPatientAndDoctorAndGrantedTrue(patient, doctor)) {
            throw new EhrException("Access denied: patient consent not granted", 403);
        }

        PatientRecord record = recordRepository.findByPatient(patient)
                .orElseThrow(() -> new EhrException("Patient record not found", 404));

        if (request.getDiagnosis() != null) {
            record.setDiagnosis(
                    encryptionService.encrypt(request.getDiagnosis())
            );
        }

        if (request.getPrescription() != null) {
            record.setPrescription(
                    encryptionService.encrypt(request.getPrescription())
            );
        }

        if (request.getMedicalHistory() != null) {
            record.setMedicalHistory(
                    encryptionService.encrypt(request.getMedicalHistory())
            );
        }

        if (request.getBloodPressure() != null) {
            record.setBloodPressure(request.getBloodPressure());
        }

        if (request.getBloodSugar() != null) {
            record.setBloodSugar(request.getBloodSugar());
        }

        return toDecryptedDto(recordRepository.save(record));
    }

    /**
     * DOCTOR/STAFF — get list of patients assigned to a doctor.
     */
    @Transactional(readOnly = true)
    public List<PatientRecordDto> getDoctorPatients(Long doctorId) {

        return recordRepository.findByAssignedDoctorId(doctorId)
                .stream()
                .map(this::toShellDto)
                .collect(Collectors.toList());
    }

    /**
     * All patient shells for STAFF today's registrations view.
     */
    @Transactional(readOnly = true)
    public List<PatientRecordDto> getAllShells() {

        return recordRepository.findAll()
                .stream()
                .map(this::toShellDto)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────

    private User getUser(Long id, String label) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new EhrException(label + " not found", 404));
    }

    /**
     * Decrypted DTO — only for DOCTOR with consent
     * or PATIENT viewing own record.
     */
    private PatientRecordDto toDecryptedDto(PatientRecord r) {

        return PatientRecordDto.builder()
                .id(r.getId())
                .patientId(r.getPatient().getId())
                .patientFirstName(r.getPatient().getFirstName())
                .patientLastName(r.getPatient().getLastName())
                .patientEmail(r.getPatient().getEmail())
                .patientPhone(r.getPatient().getPhone())

                .assignedDoctorId(
                        r.getAssignedDoctor() != null
                                ? r.getAssignedDoctor().getId()
                                : null
                )

                .assignedDoctorName(
                        r.getAssignedDoctor() != null
                                ? r.getAssignedDoctor().getFirstName()
                                + " "
                                + r.getAssignedDoctor().getLastName()
                                : null
                )

                .diagnosis(
                        encryptionService.decrypt(r.getDiagnosis())
                )

                .prescription(
                        encryptionService.decrypt(r.getPrescription())
                )

                .medicalHistory(
                        encryptionService.decrypt(r.getMedicalHistory())
                )

                .bloodPressure(r.getBloodPressure())
                .bloodSugar(r.getBloodSugar())

                .reasonForVisit(
                        r.getReasonForVisit()
                )

                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    /**
     * Shell DTO — no sensitive medical content.
     * Safe for STAFF / doctor patient lists.
     */
    private PatientRecordDto toShellDto(PatientRecord r) {

        return PatientRecordDto.builder()
                .id(r.getId())
                .patientId(r.getPatient().getId())
                .patientFirstName(r.getPatient().getFirstName())
                .patientLastName(r.getPatient().getLastName())
                .patientEmail(r.getPatient().getEmail())
                .patientPhone(r.getPatient().getPhone())

                .assignedDoctorId(
                        r.getAssignedDoctor() != null
                                ? r.getAssignedDoctor().getId()
                                : null
                )

                .assignedDoctorName(
                        r.getAssignedDoctor() != null
                                ? r.getAssignedDoctor().getFirstName()
                                + " "
                                + r.getAssignedDoctor().getLastName()
                                : null
                )

                .bloodPressure(r.getBloodPressure())
                .bloodSugar(r.getBloodSugar())

                .reasonForVisit(
                        r.getReasonForVisit()
                )

                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}