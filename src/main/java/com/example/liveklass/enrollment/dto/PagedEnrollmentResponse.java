package com.example.liveklass.enrollment.dto;

import java.util.List;

public record PagedEnrollmentResponse(
        List<EnrollmentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
