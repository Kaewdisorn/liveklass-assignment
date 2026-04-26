package com.example.liveklass.enrollment.dto;

import java.time.OffsetDateTime;

import com.example.liveklass.enrollment.enums.EnrollmentStatus;

public record CourseEnrollmentResponse(
        Long enrollmentId,
        Long studentId,
        EnrollmentStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt) {
}
