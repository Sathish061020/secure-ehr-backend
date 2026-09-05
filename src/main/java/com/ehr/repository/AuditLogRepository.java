package com.ehr.repository;

import com.ehr.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Returns the most recent audit log entry for hash-chain continuation. */
    Optional<AuditLog> findFirstByOrderByIdDesc();

    /** Paginated full audit trail — Admin only. */
    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    /** Per-actor audit trail — available to any authenticated user for their own logs. */
    List<AuditLog> findByActorEmailOrderByTimestampDesc(String email);

    /** Count of failed actions for Admin dashboard stat card. */
    long countBySuccessFalse();
}
