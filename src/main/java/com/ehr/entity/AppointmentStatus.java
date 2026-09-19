package com.ehr.entity;

/**
 * Appointment status lifecycle: PENDING → CONFIRMED → COMPLETED (or CANCELLED at any stage).
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED
}
