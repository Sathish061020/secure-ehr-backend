package com.ehr.controller;

import com.ehr.dto.CreatePatientRequest;
import com.ehr.dto.PatientRecordDto;
import com.ehr.dto.UpdateRecordRequest;
import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.UserRepository;
import com.ehr.service.AuditService;
import com.ehr.service.PatientRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientRecordController {

    private final PatientRecordService patientRecordService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    /** STAFF: Register a new patient and create their shell record. */
    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<Map<String, Object>> registerPatient(
            @Valid @RequestBody CreatePatientRequest request,
            @AuthenticationPrincipal UserDetails staffUser,
            HttpServletRequest httpRequest) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EhrException("Email already registered", 409);
        }

        // Create patient user account
        User patient = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode("Temp@" + System.currentTimeMillis()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(Role.PATIENT)
                .enabled(true)
                .build();
        patient = userRepository.save(patient);

        // Create shell record (no medical data)
        patientRecordService.createShellRecord(patient, request.getAssignedDoctorId(), request.getReasonForVisit());

        auditService.log(staffUser.getUsername(), "STAFF", "PATIENT_REGISTERED",
                patient.getEmail(),
                "Assigned to doctor ID " + request.getAssignedDoctorId(),
                true, httpRequest.getRemoteAddr());

        return ResponseEntity.ok(Map.of(
                "message", "Patient registered successfully",
                "patientId", patient.getId(),
                "email", patient.getEmail()
        ));
    }

    /** DOCTOR/STAFF: List patients assigned to the calling doctor. */
    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'STAFF')")
    public ResponseEntity<List<PatientRecordDto>> getPatients(
            @AuthenticationPrincipal UserDetails userDetails) {
        User caller = resolveUser(userDetails);
        // STAFF gets all; DOCTOR gets only their own assigned patients
        List<PatientRecordDto> patients = caller.getRole() == Role.STAFF
                ? patientRecordService.getAllShells()
                : patientRecordService.getDoctorPatients(caller.getId());
        return ResponseEntity.ok(patients);
    }

    /** DOCTOR: View a specific patient's full decrypted record (consent enforced). */
    @GetMapping("/{patientId}/record")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientRecordDto> getPatientRecord(
            @PathVariable Long patientId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User doctor = resolveUser(userDetails);
        PatientRecordDto record = patientRecordService.getRecordForDoctor(patientId, doctor.getId());
        auditService.log(userDetails.getUsername(), "DOCTOR", "RECORD_VIEWED",
                null, "Patient ID: " + patientId, true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(record);
    }

    /** DOCTOR: Update a patient's medical fields (consent enforced). */
    @PutMapping("/{patientId}/record")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<PatientRecordDto> updateRecord(
            @PathVariable Long patientId,
            @RequestBody UpdateRecordRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User doctor = resolveUser(userDetails);
        PatientRecordDto updated = patientRecordService.updateRecord(patientId, doctor.getId(), request);
        auditService.log(userDetails.getUsername(), "DOCTOR", "RECORD_UPDATED",
                null, "Patient ID: " + patientId, true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(updated);
    }

    /** PATIENT: View their own record. */
    @GetMapping("/me/record")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientRecordDto> getOwnRecord(
            @AuthenticationPrincipal UserDetails userDetails) {
        User patient = resolveUser(userDetails);
        return ResponseEntity.ok(patientRecordService.getOwnRecord(patient.getId()));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EhrException("Authenticated user not found", 500));
    }
}
