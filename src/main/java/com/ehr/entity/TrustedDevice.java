package com.ehr.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trusted_devices", indexes = {
        @Index(name = "idx_td_user_token", columnList = "user_id, device_token_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * SHA-256 hex digest of the raw token that lives in the browser cookie.
     * We never store the raw token in the database.
     */
    @Column(name = "device_token_hash", nullable = false, length = 64)
    private String deviceTokenHash;

    /** Human-readable label derived from the User-Agent header (optional). */
    @Column(length = 255)
    private String deviceLabel;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt  = LocalDateTime.now();
        lastUsedAt = LocalDateTime.now();
    }
}
