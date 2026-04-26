package com.example.liveklass.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(
                @NotNull(message = "courseId is required") Long courseId) {
}