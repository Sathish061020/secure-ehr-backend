package com.ehr.service;

import com.ehr.dto.ConsentDto;
import com.ehr.entity.Consent;
import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.ConsentRepository;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConsentDto grantConsent(Long patientId, Long doctorId) {
        User patient = getUser(patientId, "Patient");
        User doctor  = getUser(doctorId,  "Doctor");

        if (doctor.getRole() != Role.DOCTOR) {
            throw new EhrException("Target user is not a doctor", 400);
        }

        Optional<Consent> existing = consentRepository.findByPatientIdAndDoctorId(patientId, doctorId);

        Consent consent;
        if (existing.isPresent()) {
            consent = existing.get();
            consent.setGranted(true);
            consent.setGrantedAt(LocalDateTime.now());
            consent.setRevokedAt(null);
        } else {
            consent = Consent.builder()
                    .patient(patient)
                    .doctor(doctor)
                    .granted(true)
                    .grantedAt(LocalDateTime.now())
                    .build();
        }

        return toDto(consentRepository.save(consent));
    }

    @Transactional
    public void revokeConsent(Long patientId, Long doctorId) {
        Consent consent = consentRepository.findByPatientIdAndDoctorId(patientId, doctorId)
                .orElseThrow(() -> new EhrException("Consent record not found", 404));

        consent.setGranted(false);
        consent.setRevokedAt(LocalDateTime.now());
        consentRepository.save(consent);
    }

    @Transactional(readOnly = true)
    public List<ConsentDto> getPatientConsents(Long patientId) {
        User patient = getUser(patientId, "Patient");
        return consentRepository.findByPatient(patient).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasConsent(Long patientId, Long doctorId) {
        User patient = getUser(patientId, "Patient");
        User doctor  = getUser(doctorId,  "Doctor");
        return consentRepository.existsByPatientAndDoctorAndGrantedTrue(patient, doctor);
    }

    // ── Helpers ──────────────────────────────────────────────

    private User getUser(Long id, String label) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EhrException(label + " not found", 404));
    }

    private ConsentDto toDto(Consent c) {
        return ConsentDto.builder()
                .id(c.getId())
                .patientId(c.getPatient().getId())
                .patientName(c.getPatient().getFirstName() + " " + c.getPatient().getLastName())
                .doctorId(c.getDoctor().getId())
                .doctorName(c.getDoctor().getFirstName() + " " + c.getDoctor().getLastName())
                .doctorEmail(c.getDoctor().getEmail())
                .granted(c.isGranted())
                .grantedAt(c.getGrantedAt())
                .revokedAt(c.getRevokedAt())
                .build();
    }
}
