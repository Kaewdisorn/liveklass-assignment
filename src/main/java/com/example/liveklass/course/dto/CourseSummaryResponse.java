package com.example.liveklass.course.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.liveklass.course.enums.CourseStatus;

public record CourseSummaryResponse(
        Long courseId,
        String title,
        String description,
        BigDecimal price,
        Integer capacity,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status) {
}