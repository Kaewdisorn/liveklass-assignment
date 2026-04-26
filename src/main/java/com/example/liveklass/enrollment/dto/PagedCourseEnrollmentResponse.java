package com.example.liveklass.enrollment.dto;

import java.util.List;

public record PagedCourseEnrollmentResponse(
        List<CourseEnrollmentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
