package com.ehr.repository;

import com.ehr.entity.PatientRecord;
import com.ehr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PatientRecordRepository extends JpaRepository<PatientRecord, Long> {
    Optional<PatientRecord> findByPatient(User patient);
    Optional<PatientRecord> findByPatientId(Long patientId);
    List<PatientRecord> findByAssignedDoctor(User doctor);
    List<PatientRecord> findByAssignedDoctorId(Long doctorId);
}
