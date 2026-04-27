package com.example.liveklass.enrollment.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.config.UserRole;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.entity.Course;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;
import com.example.liveklass.enrollment.dto.CourseEnrollmentResponse;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedCourseEnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedEnrollmentResponse;
import com.example.liveklass.enrollment.entity.Enrollment;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;
import com.example.liveklass.enrollment.repository.EnrollmentRepository;

@Service
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentServiceImpl.class);

    private static final List<EnrollmentStatus> SEAT_STATUSES = List.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.CONFIRMED);

    private static final List<EnrollmentStatus> ACTIVE_STATUSES = List.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.CONFIRMED,
            EnrollmentStatus.WAITLISTED);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final int cancelWindowDays;

    public EnrollmentServiceImpl(
            EnrollmentRepository enrollmentRepository,
            CourseRepository courseRepository,
            @Value("${enrollment.cancel.window-days:7}") int cancelWindowDays) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.cancelWindowDays = cancelWindowDays;
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

        long activeCount = enrollmentRepository.countByCourseIdAndStatusIn(course.getId(), SEAT_STATUSES);

        EnrollmentStatus initialStatus;
        if (activeCount >= course.getCapacity()) {
            initialStatus = EnrollmentStatus.WAITLISTED;
        } else {
            initialStatus = EnrollmentStatus.PENDING;
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(course.getId());
        enrollment.setStudentId(requestUser.userId());
        enrollment.setStatus(initialStatus);

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

        Course course = courseRepository.findById(enrollment.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));

        if (!course.getCreatorId().equals(requestUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the course creator can confirm enrollments.");
        }

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
        boolean isCourseCreator = false;

        if (!isOwner && requestUser.role() == UserRole.CREATOR) {
            Course course = courseRepository.findById(enrollment.getCourseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));
            isCourseCreator = course.getCreatorId().equals(requestUser.userId());
        }

        if (!isOwner && !isCourseCreator) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot cancel this enrollment.");
        }

        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Enrollment is already cancelled.");
        }

        // 취소 가능 상태 체크 - 학생 본인의 CONFIRMED 신청에만 적용됩니다.
        // CONFIRMED 상태 아닌 경우 취소 가능 기간 체크를 하지 않습니다.
        // Creator가 취소하는 경우에도 기간 체크를 하지 않습니다.
        if (isOwner && enrollment.getStatus() == EnrollmentStatus.CONFIRMED) {
            OffsetDateTime deadline = enrollment.getUpdatedAt().plusDays(cancelWindowDays);
            if (OffsetDateTime.now().isAfter(deadline)) {
                throw new BusinessException(
                        ErrorCode.CANCELLATION_WINDOW_EXPIRED,
                        "Cancellation window of " + cancelWindowDays + " days has expired.");
            }
        }

        // 상태 변경 전에 원래 상태를 확인하기 위한 변수
        EnrollmentStatus originalStatus = enrollment.getStatus();

        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        log.info("Enrollment cancelled: enrollmentId={} by userId={}", enrollmentId, requestUser.userId());

        // 대기자 명단에서 가장 오래된 신청을 찾아서 PENDING으로 변경
        if (originalStatus == EnrollmentStatus.PENDING || originalStatus == EnrollmentStatus.CONFIRMED) {
            enrollmentRepository
                    .findFirstByCourseIdAndStatusOrderByRequestedAtAsc(
                            enrollment.getCourseId(), EnrollmentStatus.WAITLISTED)
                    .ifPresent(waitlisted -> {
                        waitlisted.setStatus(EnrollmentStatus.PENDING);
                        log.info("Waitlist promoted: enrollmentId={} courseId={}",
                                waitlisted.getId(), waitlisted.getCourseId());
                    });
        }

        return toResponse(enrollment);
    }

    @Override
    public PagedEnrollmentResponse getMyEnrollments(RequestUser requestUser, Pageable pageable) {
        Page<Enrollment> page = enrollmentRepository
                .findByStudentIdOrderByRequestedAtDesc(requestUser.userId(), pageable);

        return new PagedEnrollmentResponse(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    @Override
    public PagedCourseEnrollmentResponse getCourseEnrollments(RequestUser requestUser, Long courseId,
            Pageable pageable) {
        if (requestUser.role() != UserRole.CREATOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Creator role is required.");
        }

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));

        if (!course.getCreatorId().equals(requestUser.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only the course creator can view enrollments.");
        }

        Page<Enrollment> page = enrollmentRepository.findByCourseIdOrderByRequestedAtDesc(courseId, pageable);

        return new PagedCourseEnrollmentResponse(
                page.getContent().stream().map(this::toCourseEnrollmentResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
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

    private CourseEnrollmentResponse toCourseEnrollmentResponse(Enrollment enrollment) {
        return new CourseEnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudentId(),
                enrollment.getStatus(),
                enrollment.getRequestedAt(),
                enrollment.getUpdatedAt());
    }

}