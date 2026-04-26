package com.example.liveklass.enrollment.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.config.UserRole;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.entity.Course;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.entity.Enrollment;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;
import com.example.liveklass.enrollment.repository.EnrollmentRepository;

@Service
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentServiceImpl.class);

    private static final List<EnrollmentStatus> ACTIVE_STATUSES = List.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.CONFIRMED);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository, CourseRepository courseRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public EnrollmentResponse createEnrollment(RequestUser requestUser, CreateEnrollmentRequest request) {
        assertStudent(requestUser);

        Course course = courseRepository.findByIdForUpdate(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));

        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Course is not open for enrollment.");
        }

        boolean duplicated = enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(
                course.getId(),
                requestUser.userId(),
                ACTIVE_STATUSES);

        if (duplicated) {
            throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT, "Active enrollment already exists.");
        }

        long activeCount = enrollmentRepository.countByCourseIdAndStatusIn(course.getId(), ACTIVE_STATUSES);

        if (activeCount >= course.getCapacity()) {
            throw new BusinessException(ErrorCode.COURSE_FULL, "Course capacity has been reached.");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(course.getId());
        enrollment.setStudentId(requestUser.userId());
        enrollment.setStatus(EnrollmentStatus.PENDING);

        Enrollment saved = enrollmentRepository.saveAndFlush(enrollment);
        log.info("Enrollment created: enrollmentId={}, courseId={}, studentId={}",
                saved.getId(), saved.getCourseId(), saved.getStudentId());
        return toResponse(saved);

    }

    @Override
    @Transactional
    public EnrollmentResponse confirmEnrollment(RequestUser requestUser, Long enrollmentId) {
        assertCreator(requestUser);

        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found."));

        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Only PENDING enrollments can be confirmed.");
        }

        enrollment.setStatus(EnrollmentStatus.CONFIRMED);
        log.info("Enrollment confirmed: enrollmentId={}", enrollmentId);
        return toResponse(enrollment);
    }

    @Override
    @Transactional
    public EnrollmentResponse cancelEnrollment(RequestUser requestUser, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found."));

        boolean isOwner = enrollment.getStudentId().equals(requestUser.userId());
        boolean isCreator = requestUser.role() == UserRole.CREATOR;

        if (!isOwner && !isCreator) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot cancel this enrollment.");
        }

        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Enrollment is already cancelled.");
        }

        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        log.info("Enrollment cancelled: enrollmentId={} by userId={}", enrollmentId, requestUser.userId());
        return toResponse(enrollment);
    }

    @Override
    public List<EnrollmentResponse> getMyEnrollments(RequestUser requestUser) {
        return enrollmentRepository
                .findByStudentIdOrderByRequestedAtDesc(requestUser.userId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void assertStudent(RequestUser requestUser) {
        if (requestUser.role() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Student role is required.");
        }
    }

    private void assertCreator(RequestUser requestUser) {
        if (requestUser.role() != UserRole.CREATOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Creator role is required.");
        }
    }

    private EnrollmentResponse toResponse(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourseId(),
                enrollment.getStudentId(),
                enrollment.getStatus(),
                enrollment.getRequestedAt(),
                enrollment.getUpdatedAt());
    }

}