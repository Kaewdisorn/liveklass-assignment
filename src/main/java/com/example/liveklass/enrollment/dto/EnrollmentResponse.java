package com.example.liveklass.enrollment.dto;

import java.time.OffsetDateTime;

import com.example.liveklass.enrollment.enums.EnrollmentStatus;

public record EnrollmentResponse(
        Long enrollmentId,
        Long courseId,
        Long studentId,
        EnrollmentStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt) {
}