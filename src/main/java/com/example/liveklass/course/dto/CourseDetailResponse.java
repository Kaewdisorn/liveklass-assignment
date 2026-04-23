package com.example.liveklass.course.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.example.liveklass.course.enums.CourseStatus;

public record CourseDetailResponse(
        Long courseId,
        Long creatorId,
        String title,
        String description,
        BigDecimal price,
        Integer capacity,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status,
        long activeEnrollmentCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}