package com.example.liveklass.course.dto;

import com.example.liveklass.course.enums.CourseStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateCourseStatusRequest(
        @NotNull(message = "status is required") CourseStatus status) {
}