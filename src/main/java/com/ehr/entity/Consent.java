package com.ehr.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Consent record: a patient explicitly grants or revokes a doctor's access to their record.
 * A doctor without an active Consent (granted=true) MUST NOT see any medical content.
 */
@Entity
@Table(name = "consents", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"patient_id", "doctor_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Column(nullable = false)
    @Builder.Default
    private boolean granted = true;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @Column
    private LocalDateTime revokedAt;
}
