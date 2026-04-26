package com.example.liveklass.enrollment.service;

import java.util.List;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;

public interface EnrollmentService {

    EnrollmentResponse createEnrollment(RequestUser requestUser, CreateEnrollmentRequest request);
}