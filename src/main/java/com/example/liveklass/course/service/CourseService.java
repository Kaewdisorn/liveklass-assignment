package com.example.liveklass.course.service;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;

public interface CourseService {
    CourseDetailResponse createCourse(RequestUser requestUser, CreateCourseRequest request);

    CourseDetailResponse getCourse(Long courseId);

}
