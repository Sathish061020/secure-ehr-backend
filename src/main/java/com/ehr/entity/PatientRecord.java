package com.ehr.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Patient medical record.
 * The diagnosis, prescription, and medicalHistory fields are stored AES-256-GCM encrypted.
 * Never return these encrypted bytes raw — always decrypt via EncryptionService first.
 */
@Entity
@Table(name = "patient_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_doctor_id")
    private User assignedDoctor;

    /** AES-256-GCM encrypted, Base64-encoded. DOCTOR only (with consent). */
    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    /** AES-256-GCM encrypted, Base64-encoded. DOCTOR only (with consent). */
    @Column(columnDefinition = "TEXT")
    private String prescription;

    /** AES-256-GCM encrypted, Base64-encoded. DOCTOR only (with consent). */
    @Column(columnDefinition = "TEXT")
    private String medicalHistory;

    /** Non-sensitive vitals — available to DOCTOR (consent) and PATIENT (own). */
    @Column(length = 20)
    private String bloodPressure;

    @Column(length = 20)
    private String bloodSugar;

    /** Set by STAFF on registration — non-sensitive. */
    @Column(length = 255)
    private String reasonForVisit;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
