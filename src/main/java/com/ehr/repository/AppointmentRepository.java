package com.ehr.repository;

import com.ehr.entity.Appointment;
import com.ehr.entity.AppointmentStatus;
import com.ehr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** All appointments for a patient (for patient history view). */
    List<Appointment> findByPatientOrderByCreatedAtDesc(User patient);

    /** Doctor's appointments for a specific date (for today's list). */
    List<Appointment> findByDoctorAndAppointmentDate(User doctor, LocalDate date);

    /** Count of CONFIRMED appointments for a doctor on a date — used for token numbering. */
    long countByDoctorAndAppointmentDateAndStatus(User doctor, LocalDate date, AppointmentStatus status);

    /** Check if a doctor has any CONFIRMED appointment with a patient (any date). */
    boolean existsByDoctorAndPatientAndStatus(User doctor, User patient, AppointmentStatus status);

    /** Find appointment for doctor, date and token number. */
    List<Appointment> findByDoctorAndAppointmentDateAndTokenNumber(User doctor, LocalDate date, Integer tokenNumber);

    /** Find appointments by date and token number. */
    List<Appointment> findByAppointmentDateAndTokenNumber(LocalDate date, Integer tokenNumber);

    /** Count of confirmed appointments for doctor on date with lower token number (queue position). */
    long countByDoctorAndAppointmentDateAndStatusAndTokenNumberLessThan(User doctor, LocalDate date, AppointmentStatus status, Integer tokenNumber);
}
