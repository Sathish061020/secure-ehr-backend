package com.ehr.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Hash-chained audit log entry.
 * Each row stores a SHA-256 hash of its own data combined with the previous row's hash,
 * creating a tamper-evident chain. Any modification to a historical row will break the chain.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String actorEmail;

    @Column(nullable = false, length = 10)
    private String actorRole;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 100)
    private String targetEmail;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /** SHA-256(own data fields + previousHash) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String currentHash;

    /** Previous row's currentHash. "GENESIS" for the first row. */
    @Column(columnDefinition = "TEXT")
    private String previousHash;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
