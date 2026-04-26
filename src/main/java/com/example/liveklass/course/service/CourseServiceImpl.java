package com.example.liveklass.course.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.config.UserRole;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CourseSummaryResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.dto.UpdateCourseStatusRequest;
import com.example.liveklass.course.entity.Course;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;

@Service
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

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

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));

        return toDetailResponse(course, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> getCourses(CourseStatus status) {
        List<Course> courses;

        if (status != null) {
            courses = courseRepository.findByStatus(status);
        } else {
            courses = courseRepository.findAll();
        }

        return courses.stream()
                .map(c -> new CourseSummaryResponse(
                        c.getId(),
                        c.getTitle(),
                        c.getDescription(),
                        c.getPrice(),
                        c.getCapacity(),
                        c.getStartDate(),
                        c.getEndDate(),
                        c.getStatus()))
                .toList();
    }

    @Override
    @Transactional
    public CourseDetailResponse updateCourseStatus(
            RequestUser requestUser,
            Long courseId,
            UpdateCourseStatusRequest request) {
        assertCreator(requestUser);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));

        validateTransition(course.getStatus(), request.status());
        course.setStatus(request.status());

        return toDetailResponse(course, 0L);

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

    private void validateTransition(CourseStatus currentStatus, CourseStatus nextStatus) {
        boolean valid = false;

        if (currentStatus == CourseStatus.DRAFT && nextStatus == CourseStatus.OPEN ||
                currentStatus == CourseStatus.OPEN && nextStatus == CourseStatus.CLOSED) {
            valid = true;

        }

        if (!valid) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "Invalid course status transition.");
        }
    }

}