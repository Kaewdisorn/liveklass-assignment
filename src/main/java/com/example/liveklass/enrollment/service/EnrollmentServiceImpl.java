package com.example.liveklass.enrollment.service;

import java.util.List;

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

        System.out.println(
                "Creating enrollment for userId: " + requestUser.userId() + ", courseId: " + request.courseId());

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

        return toResponse(enrollmentRepository.saveAndFlush(enrollment));

    }

    private void assertStudent(RequestUser requestUser) {
        if (requestUser.role() != UserRole.STUDENT) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Student role is required.");
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