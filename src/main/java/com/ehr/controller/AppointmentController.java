package com.ehr.controller;

import com.ehr.dto.AppointmentDto;
import com.ehr.dto.BookAppointmentRequest;
import com.ehr.dto.ReceiptDto;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.UserRepository;
import com.ehr.service.AppointmentService;
import com.ehr.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.ehr.dto.LookupResponseDto;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** DOCTOR / STAFF: Scan / Search lookup by health ID or token number. */
    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('DOCTOR', 'STAFF')")
    public ResponseEntity<LookupResponseDto> lookupAppointment(
            @RequestParam String code,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User caller = resolveUser(userDetails);
        LookupResponseDto response = appointmentService.lookupAppointment(
                code, caller, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    /** PATIENT: Book an appointment with a doctor. */

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentDto> bookAppointment(
            @Valid @RequestBody BookAppointmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User patient = resolveUser(userDetails);
        AppointmentDto dto = appointmentService.bookAppointment(patient.getId(), request);
        auditService.log(userDetails.getUsername(), "PATIENT", "APPOINTMENT_BOOKED",
                null, "Doctor ID: " + request.getDoctorId() + ", Date: " + request.getAppointmentDate(),
                true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(dto);
    }

    /** DOCTOR: Confirm a pending appointment (assigns token number). */
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentDto> confirmAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User doctor = resolveUser(userDetails);
        AppointmentDto dto = appointmentService.confirmAppointment(id, doctor.getId());
        auditService.log(userDetails.getUsername(), "DOCTOR", "APPOINTMENT_CONFIRMED",
                null, "Appointment ID: " + id + ", Token: " + dto.getTokenNumber(),
                true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(dto);
    }

    /** DOCTOR: Mark a confirmed appointment as completed. */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<AppointmentDto> completeAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        User doctor = resolveUser(userDetails);
        AppointmentDto dto = appointmentService.completeAppointment(id, doctor.getId());
        auditService.log(userDetails.getUsername(), "DOCTOR", "APPOINTMENT_COMPLETED",
                null, "Appointment ID: " + id, true, httpRequest.getRemoteAddr());
        return ResponseEntity.ok(dto);
    }

    /** PATIENT: Get own appointment/token history. */
    @GetMapping("/patient")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentDto>> getPatientAppointments(
            @AuthenticationPrincipal UserDetails userDetails) {
        User patient = resolveUser(userDetails);
        return ResponseEntity.ok(appointmentService.getPatientAppointments(patient.getId()));
    }

    /** DOCTOR: Get today's appointment list. */
    @GetMapping("/doctor")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentDto>> getDoctorTodayAppointments(
            @AuthenticationPrincipal UserDetails userDetails) {
        User doctor = resolveUser(userDetails);
        return ResponseEntity.ok(appointmentService.getDoctorTodayAppointments(doctor.getId()));
    }

    /** DOCTOR/PATIENT: Get receipt data for a confirmed/completed appointment. */
    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT')")
    public ResponseEntity<ReceiptDto> getReceipt(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.getReceipt(id));
    }

    private User resolveUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new EhrException("Authenticated user not found", 500));
    }
}
