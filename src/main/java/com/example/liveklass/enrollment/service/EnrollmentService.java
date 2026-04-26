package com.example.liveklass.enrollment.service;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedCourseEnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedEnrollmentResponse;

import org.springframework.data.domain.Pageable;

public interface EnrollmentService {

    // 수강 신청
    EnrollmentResponse createEnrollment(RequestUser requestUser, CreateEnrollmentRequest request);

    // 수강 신청 승인
    EnrollmentResponse confirmEnrollment(RequestUser requestUser, Long enrollmentId);

    // 수강 신청 취소
    EnrollmentResponse cancelEnrollment(RequestUser requestUser, Long enrollmentId);

    // 내 수강 신청 조회
    PagedEnrollmentResponse getMyEnrollments(RequestUser requestUser, Pageable pageable);

    // 강의별 수강생 목록 조회 (크리에이터 전용)
    PagedCourseEnrollmentResponse getCourseEnrollments(RequestUser requestUser, Long courseId, Pageable pageable);
}