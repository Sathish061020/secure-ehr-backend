package com.ehr.controller;

import com.ehr.entity.AuditLog;
import com.ehr.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /** ADMIN: Paginated full audit trail (actions only — no diagnosis/prescription content). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAllLogs(PageRequest.of(page, Math.min(size, 100))));
    }

    /** ADMIN: Count of denied/failed audit events for dashboard stat card. */
    @GetMapping("/denied-count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getDeniedCount() {
        return ResponseEntity.ok(Map.of("deniedCount", auditService.getDeniedCount()));
    }

    /** Any authenticated user: view their own recent audit actions. */
    @GetMapping("/my")
    public ResponseEntity<List<AuditLog>> getMyAuditLogs(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(auditService.getLogsByActor(userDetails.getUsername()));
    }
}
