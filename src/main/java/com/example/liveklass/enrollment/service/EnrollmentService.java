package com.example.liveklass.enrollment.service;

import java.util.List;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;

public interface EnrollmentService {

    // 수강 신청
    EnrollmentResponse createEnrollment(RequestUser requestUser, CreateEnrollmentRequest request);

    // 수강 신청 승인
    EnrollmentResponse confirmEnrollment(RequestUser requestUser, Long enrollmentId);

    // 수강 신청 취소
    EnrollmentResponse cancelEnrollment(RequestUser requestUser, Long enrollmentId);

    // 내 수강 신청 조회
    List<EnrollmentResponse> getMyEnrollments(RequestUser requestUser);
}