package com.ehr.controller;

import com.ehr.dto.CreateUserRequest;
import com.ehr.dto.UserDto;
import com.ehr.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** ADMIN: Get all users. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /** ADMIN: Aggregate stats (counts per role). */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getUserStats() {
        return ResponseEntity.ok(userService.getUserStats());
    }

    /** ADMIN/STAFF/PATIENT: List doctors (for consent or assignment purposes). */
    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'PATIENT', 'DOCTOR')")
    public ResponseEntity<List<UserDto>> getDoctors() {
        return ResponseEntity.ok(userService.getDoctors());
    }

    /** ADMIN: Get a specific user. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /** ADMIN: Create a user (any role). */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUser(request));
    }

    /** ADMIN: Delete a user. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    /** ADMIN: Toggle account lock/unlock. */
    @PostMapping("/{id}/toggle-lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> toggleLock(@PathVariable Long id) {
        return ResponseEntity.ok(userService.toggleUserLock(id));
    }
}
