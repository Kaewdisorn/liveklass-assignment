package com.example.liveklass.enrollment.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.liveklass.common.config.CurrentUser;
import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedEnrollmentResponse;
import com.example.liveklass.enrollment.service.EnrollmentService;

@Validated
@RestController
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    // 강의 신청
    @PostMapping
    public ResponseEntity<EnrollmentResponse> createEnrollment(
            @CurrentUser RequestUser requestUser,
            @Valid @RequestBody CreateEnrollmentRequest request) {
        return ResponseEntity.ok(enrollmentService.createEnrollment(requestUser,
                request));
    }

    // 강의 신청 승인
    @PostMapping("/{enrollmentId}/confirm")
    public ResponseEntity<EnrollmentResponse> confirmEnrollment(
            @CurrentUser RequestUser requestUser,
            @PathVariable Long enrollmentId) {
        return ResponseEntity.ok(enrollmentService.confirmEnrollment(requestUser,
                enrollmentId));
    }

    // 강의 신청 취소
    @PostMapping("/{enrollmentId}/cancel")
    public ResponseEntity<EnrollmentResponse> cancelEnrollment(
            @CurrentUser RequestUser requestUser,
            @PathVariable Long enrollmentId) {
        return ResponseEntity.ok(enrollmentService.cancelEnrollment(requestUser,
                enrollmentId));
    }

    @GetMapping("/me")
    public ResponseEntity<PagedEnrollmentResponse> getMyEnrollments(
            @CurrentUser RequestUser requestUser,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(enrollmentService.getMyEnrollments(requestUser, pageable));
    }
}