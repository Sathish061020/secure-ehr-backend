package com.ehr.repository;

import com.ehr.entity.Consent;
import com.ehr.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {
    Optional<Consent> findByPatientAndDoctorAndGrantedTrue(User patient, User doctor);
    Optional<Consent> findByPatientIdAndDoctorId(Long patientId, Long doctorId);
    List<Consent> findByPatient(User patient);
    List<Consent> findByDoctorAndGrantedTrue(User doctor);
    boolean existsByPatientAndDoctorAndGrantedTrue(User patient, User doctor);
}
