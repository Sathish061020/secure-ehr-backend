package com.ehr.service;

import com.ehr.dto.AppointmentDto;
import com.ehr.dto.BookAppointmentRequest;
import com.ehr.dto.ReceiptDto;
import com.ehr.entity.Appointment;
import com.ehr.entity.AppointmentStatus;
import com.ehr.entity.Role;
import com.ehr.entity.User;
import com.ehr.exception.EhrException;
import com.ehr.repository.AppointmentRepository;
import com.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.ehr.dto.LookupResponseDto;
import com.ehr.entity.PatientRecord;
import com.ehr.repository.ConsentRepository;
import com.ehr.repository.PatientRecordRepository;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final PatientRecordRepository recordRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;


    /**
     * PATIENT: Book an appointment with a doctor.
     * Initial status is PENDING; token not yet assigned.
     */
    @Transactional
    public AppointmentDto bookAppointment(Long patientId, BookAppointmentRequest request) {
        User patient = getUser(patientId, "Patient");
        User doctor  = getUser(request.getDoctorId(), "Doctor");

        if (doctor.getRole() != Role.DOCTOR) {
            throw new EhrException("Target user is not a doctor", 400);
        }

        Appointment appointment = Appointment.builder()
                .patient(patient)
                .doctor(doctor)
                .appointmentDate(request.getAppointmentDate())
                .status(AppointmentStatus.PENDING)
                .build();

        return toDto(appointmentRepository.save(appointment));
    }

    /**
     * DOCTOR: Confirm a pending appointment.
     * Assigns the next sequential token number for that doctor on that day.
     */
    @Transactional
    public AppointmentDto confirmAppointment(Long appointmentId, Long doctorId) {
        Appointment appointment = getAppointment(appointmentId);

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new EhrException("You are not authorized to confirm this appointment", 403);
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new EhrException("Only PENDING appointments can be confirmed", 400);
        }

        // Token = count of already-CONFIRMED appointments for this doctor on this date + 1
        long confirmedCount = appointmentRepository.countByDoctorAndAppointmentDateAndStatus(
                appointment.getDoctor(), appointment.getAppointmentDate(), AppointmentStatus.CONFIRMED);
        int tokenNumber = (int) confirmedCount + 1;

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setTokenNumber(tokenNumber);

        log.info("Appointment {} confirmed: doctor={}, date={}, token={}",
                appointmentId, doctorId, appointment.getAppointmentDate(), tokenNumber);

        return toDto(appointmentRepository.save(appointment));
    }

    /**
     * DOCTOR: Mark an appointment as completed.
     */
    @Transactional
    public AppointmentDto completeAppointment(Long appointmentId, Long doctorId) {
        Appointment appointment = getAppointment(appointmentId);

        if (!appointment.getDoctor().getId().equals(doctorId)) {
            throw new EhrException("You are not authorized to complete this appointment", 403);
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new EhrException("Only CONFIRMED appointments can be marked complete", 400);
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        return toDto(appointmentRepository.save(appointment));
    }

    /**
     * PATIENT: Get all of their appointments (newest first).
     */
    @Transactional(readOnly = true)
    public List<AppointmentDto> getPatientAppointments(Long patientId) {
        User patient = getUser(patientId, "Patient");
        return appointmentRepository.findByPatientOrderByCreatedAtDesc(patient)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * DOCTOR: Get today's appointment list.
     */
    @Transactional(readOnly = true)
    public List<AppointmentDto> getDoctorTodayAppointments(Long doctorId) {
        User doctor = getUser(doctorId, "Doctor");
        return appointmentRepository.findByDoctorAndAppointmentDate(doctor, LocalDate.now())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * DOCTOR/PATIENT: Get receipt data for a confirmed appointment.
     */
    @Transactional(readOnly = true)
    public ReceiptDto getReceipt(Long appointmentId) {
        Appointment a = getAppointment(appointmentId);

        if (a.getStatus() != AppointmentStatus.CONFIRMED && a.getStatus() != AppointmentStatus.COMPLETED) {
            throw new EhrException("Receipt is only available for confirmed or completed appointments", 400);
        }

        return ReceiptDto.builder()
                .appointmentId(a.getId())
                .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                .healthId(a.getPatient().getHealthId())
                .age(a.getPatient().getAge())
                .sex(a.getPatient().getSex())
                .mobile(a.getPatient().getPhone())
                .address(a.getPatient().getAddress())
                .department(a.getDoctor().getDepartment())
                .doctorName("Dr. " + a.getDoctor().getFirstName() + " " + a.getDoctor().getLastName())
                .appointmentDate(a.getAppointmentDate())
                .tokenNumber(a.getTokenNumber())
                .build();
    }


    // ── Helpers ───────────────────────────────────────────────

    private User getUser(Long id, String label) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EhrException(label + " not found", 404));
    }

    private Appointment getAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new EhrException("Appointment not found", 404));
    }

    private AppointmentDto toDto(Appointment a) {
        return AppointmentDto.builder()
                .id(a.getId())
                .patientId(a.getPatient().getId())
                .patientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName())
                .healthId(a.getPatient().getHealthId())
                .doctorId(a.getDoctor().getId())
                .doctorName("Dr. " + a.getDoctor().getFirstName() + " " + a.getDoctor().getLastName())
                .appointmentDate(a.getAppointmentDate())
                .status(a.getStatus())
                .tokenNumber(a.getTokenNumber())
                .createdAt(a.getCreatedAt())
                .build();
    }

    /**
     * DOCTOR / STAFF: Scan/Search lookup by health ID or token number.
     * Enforces role-based tiered access for sensitive medical details and logs audit entry.
     */
    @Transactional
    public LookupResponseDto lookupAppointment(String code, User caller, String ipAddress) {
        if (code == null || code.trim().isEmpty()) {
            throw new EhrException("Lookup code is required", 400);
        }

        String trimmedCode = code.trim();
        Appointment appointment = null;

        // Option 1: Search by Health ID
        Optional<User> patientOpt = userRepository.findByHealthId(trimmedCode);
        if (patientOpt.isPresent()) {
            User patient = patientOpt.get();
            List<Appointment> apts = appointmentRepository.findByPatientOrderByCreatedAtDesc(patient);
            if (!apts.isEmpty()) {
                appointment = apts.get(0);
            }
        }

        // Option 2: Search by Token Number
        if (appointment == null && trimmedCode.matches("\\d+")) {
            int tokenNum = Integer.parseInt(trimmedCode);
            LocalDate today = LocalDate.now();

            if (caller.getRole() == Role.DOCTOR) {
                List<Appointment> doctorApts = appointmentRepository
                        .findByDoctorAndAppointmentDateAndTokenNumber(caller, today, tokenNum);
                if (!doctorApts.isEmpty()) {
                    appointment = doctorApts.get(0);
                }
            }

            if (appointment == null) {
                List<Appointment> allApts = appointmentRepository
                        .findByAppointmentDateAndTokenNumber(today, tokenNum);
                if (!allApts.isEmpty()) {
                    appointment = allApts.get(0);
                }
            }
        }

        if (appointment == null) {
            auditService.log(caller.getEmail(), caller.getRole().name(), "SCAN_LOOKUP_FAILED",
                    null, "No appointment/patient found for code: " + trimmedCode, false, ipAddress);
            throw new EhrException("No appointment or patient found for code: " + trimmedCode, 404);
        }

        User patient = appointment.getPatient();
        User doctor  = appointment.getDoctor();

        // Calculate queue position: count of CONFIRMED appointments for this doctor on same day with lower token number
        long queuePos = 0;
        if (appointment.getTokenNumber() != null && appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            queuePos = appointmentRepository.countByDoctorAndAppointmentDateAndStatusAndTokenNumberLessThan(
                    doctor, appointment.getAppointmentDate(), AppointmentStatus.CONFIRMED, appointment.getTokenNumber());
        }

        long estimatedWait = queuePos * 5;

        // Tiered Record Access for lookup details
        String accessLevel = "RESTRICTED";
        String diagnosis = null;
        String prescription = null;
        String medicalHistory = null;

        if (caller.getRole() == Role.DOCTOR) {
            boolean isAssigned = recordRepository.findByPatient(patient)
                    .map(r -> r.getAssignedDoctor() != null && r.getAssignedDoctor().getId().equals(caller.getId()))
                    .orElse(false);
            boolean hasConsent = consentRepository.existsByPatientAndDoctorAndGrantedTrue(patient, caller);

            if (isAssigned || hasConsent) {
                accessLevel = "FULL";
                Optional<PatientRecord> recOpt = recordRepository.findByPatient(patient);
                if (recOpt.isPresent()) {
                    PatientRecord r = recOpt.get();
                    diagnosis = encryptionService.decrypt(r.getDiagnosis());
                    prescription = encryptionService.decrypt(r.getPrescription());
                    medicalHistory = encryptionService.decrypt(r.getMedicalHistory());
                }
            } else {
                accessLevel = "READ_ONLY";
            }
        }

        auditService.log(caller.getEmail(), caller.getRole().name(), "SCAN_LOOKUP",
                patient.getEmail(), "Looked up code: " + trimmedCode + ", access: " + accessLevel, true, ipAddress);

        return LookupResponseDto.builder()
                .patientId(patient.getId())
                .patientName(patient.getFirstName() + " " + patient.getLastName())
                .age(patient.getAge())
                .sex(patient.getSex())
                .mobile(patient.getPhone())
                .healthId(patient.getHealthId())
                .department(doctor.getDepartment())
                .doctorName("Dr. " + doctor.getFirstName() + " " + doctor.getLastName())
                .visitDate(appointment.getAppointmentDate())
                .tokenNumber(appointment.getTokenNumber())
                .status(appointment.getStatus().name())
                .queuePosition(queuePos)
                .estimatedWaitMinutes(estimatedWait)
                .accessLevel(accessLevel)
                .diagnosis(diagnosis)
                .prescription(prescription)
                .medicalHistory(medicalHistory)
                .build();
    }
}

