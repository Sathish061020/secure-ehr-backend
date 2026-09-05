package com.ehr.service;

import com.ehr.entity.AuditLog;
import com.ehr.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Hash-chained audit logging service.
 *
 * Each entry's currentHash = SHA-256(own data fields concatenated + previousHash).
 * The chain starts with "GENESIS" as the seed.
 * Any tampering with a historical record will break the chain from that point forward.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String actorEmail, String actorRole, String action,
                    String targetEmail, String details, boolean success, String ipAddress) {
        try {
            String previousHash = auditLogRepository.findFirstByOrderByIdDesc()
                    .map(AuditLog::getCurrentHash)
                    .orElse("GENESIS");

            // Build deterministic data string for hashing
            String dataToHash = String.join("|",
                    safe(actorEmail),
                    safe(actorRole),
                    safe(action),
                    safe(targetEmail),
                    safe(details),
                    String.valueOf(success),
                    previousHash
            );
            String currentHash = sha256(dataToHash);

            AuditLog entry = AuditLog.builder()
                    .actorEmail(actorEmail)
                    .actorRole(actorRole)
                    .action(action)
                    .targetEmail(targetEmail)
                    .details(details)
                    .success(success)
                    .ipAddress(ipAddress)
                    .previousHash(previousHash)
                    .currentHash(currentHash)
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never let audit failure break the main business flow
            log.error("Audit log write failed for action={} actor={}: {}", action, actorEmail, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getLogsByActor(String email) {
        return auditLogRepository.findByActorEmailOrderByTimestampDesc(email);
    }

    @Transactional(readOnly = true)
    public long getDeniedCount() {
        return auditLogRepository.countBySuccessFalse();
    }

    // ── Helpers ──────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
