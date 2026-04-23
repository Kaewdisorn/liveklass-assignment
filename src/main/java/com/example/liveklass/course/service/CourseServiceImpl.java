package com.example.liveklass.course.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.config.UserRole;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.entity.Course;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;
import com.example.liveklass.enrollment.repository.EnrollmentRepository;

@Service
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES = List.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.CONFIRMED);

    private final CourseRepository courseRepository;

    public CourseServiceImpl(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public CourseDetailResponse createCourse(RequestUser requestUser, CreateCourseRequest request) {
        assertCreator(requestUser);

        Course course = new Course();
        course.setCreatorId(requestUser.userId());
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setPrice(request.price());
        course.setCapacity(request.capacity());
        course.setStartDate(request.startDate());
        course.setEndDate(request.endDate());
        course.setStatus(CourseStatus.DRAFT);

        Course savedCourse = courseRepository.save(course);
        return toDetailResponse(savedCourse, 0L);
    }

    private void assertCreator(RequestUser requestUser) {
        if (requestUser.role() != UserRole.CREATOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Creator role is required.");
        }
    }

    private CourseDetailResponse toDetailResponse(Course course, long activeEnrollmentCount) {
        return new CourseDetailResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCapacity(),
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus(),
                activeEnrollmentCount,
                course.getCreatedAt(),
                course.getUpdatedAt());
    }

}