package com.ehr.controller;

import com.ehr.dto.ConsentDto;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.UserRepository;
import com.ehr.service.AuditService;
import com.ehr.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consent")
@RequiredArgsConstructor
public class ConsentController {

    private final ConsentService consentService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** PATIENT: Get all consent records for the calling patient. */
    @GetMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<ConsentDto>> getMyConsents(
            @AuthenticationPrincipal UserDetails userDetails) {
        User patient = resolveUser(userDetails);
        return ResponseEntity.ok(consentService.getPatientConsents(patient.getId()));
    }

    /** PATIENT: Grant consent to a specific doctor. */
    @PostMapping("/grant/{doctorId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ConsentDto> grantConsent(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User patient = resolveUser(userDetails);
        ConsentDto consent = consentService.grantConsent(patient.getId(), doctorId);
        auditService.log(userDetails.getUsername(), "PATIENT", "CONSENT_GRANTED",
                null, "Granted access to doctor ID " + doctorId, true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(consent);
    }

    /** PATIENT: Revoke consent from a specific doctor. */
    @PostMapping("/revoke/{doctorId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Map<String, String>> revokeConsent(
            @PathVariable Long doctorId,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User patient = resolveUser(userDetails);
        consentService.revokeConsent(patient.getId(), doctorId);
        auditService.log(userDetails.getUsername(), "PATIENT", "CONSENT_REVOKED",
                null, "Revoked access from doctor ID " + doctorId, true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(Map.of("message", "Consent revoked successfully"));
    }

    /** DOCTOR/PATIENT: Check if consent exists between a patient and doctor. */
    @GetMapping("/check/{patientId}/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ResponseEntity<Map<String, Boolean>> checkConsent(
            @PathVariable Long patientId,
            @PathVariable Long doctorId) {
        return ResponseEntity.ok(Map.of("granted", consentService.hasConsent(patientId, doctorId)));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EhrException("Authenticated user not found", 500));
    }
}
