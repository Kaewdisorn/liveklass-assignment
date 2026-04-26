package com.example.liveklass.course.service;

import java.util.List;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CourseSummaryResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.dto.UpdateCourseStatusRequest;
import com.example.liveklass.course.enums.CourseStatus;

public interface CourseService {

    // 강의 생성
    CourseDetailResponse createCourse(RequestUser requestUser, CreateCourseRequest request);

    // 강의 상세 조회
    CourseDetailResponse getCourse(Long courseId);

    // 강의 목록 조회 (상태별)
    List<CourseSummaryResponse> getCourses(CourseStatus status);

    // 강의 상태 업데이트
    CourseDetailResponse updateCourseStatus(
            RequestUser requestUser,
            Long courseId,
            UpdateCourseStatusRequest request);

}