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
import com.example.liveklass.enrollment.enums.EnrollmentStatus;

@Service
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES = List.of(
            EnrollmentStatus.PENDING,
            EnrollmentStatus.CONFIRMED);

    @Override
    @Transactional
    public CourseDetailResponse createCourse(RequestUser requestUser, CreateCourseRequest request) {
        assertCreator(requestUser);

        Course course = new Course();
        // Check params
        System.out.println("User ID: " + requestUser.userId());
        throw new UnsupportedOperationException("Unimplemented method 'createCourse'");
    }

    private void assertCreator(RequestUser requestUser) {
        if (requestUser.role() != UserRole.CREATOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Creator role is required.");
        }
    }

}