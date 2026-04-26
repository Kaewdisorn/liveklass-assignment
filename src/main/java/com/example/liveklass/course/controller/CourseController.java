package com.example.liveklass.course.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.liveklass.common.config.CurrentUser;
import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CourseSummaryResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.dto.UpdateCourseStatusRequest;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.service.CourseService;
import com.example.liveklass.enrollment.dto.PagedCourseEnrollmentResponse;
import com.example.liveklass.enrollment.service.EnrollmentService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/classes")
public class CourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public CourseController(CourseService courseService, EnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    // Course 등록
    @PostMapping
    public ResponseEntity<CourseDetailResponse> createCourse(
            @CurrentUser RequestUser requestUser,
            @Valid @RequestBody CreateCourseRequest request) {
        CourseDetailResponse response = courseService.createCourse(requestUser, request);
        return ResponseEntity
                .created(URI.create("/classes/" + response.courseId()))
                .body(response);
    }

    // Course 목록 조회 (상태별)
    @GetMapping
    public ResponseEntity<List<CourseSummaryResponse>> getCourses(
            @RequestParam(required = false) CourseStatus status) {
        return ResponseEntity.ok(courseService.getCourses(status));
    }

    // Course 상세 조회
    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourse(courseId));
    }

    // Course 상태 업데이트
    @PatchMapping("/{courseId}/status")
    public ResponseEntity<CourseDetailResponse> updateCourseStatus(
            @CurrentUser RequestUser requestUser,
            @PathVariable Long courseId,
            @Valid @RequestBody UpdateCourseStatusRequest request) {
        return ResponseEntity.ok(courseService.updateCourseStatus(requestUser, courseId, request));
    }

    // 강의별 수강생 목록 조회 (크리에이터 전용)
    @GetMapping("/{courseId}/enrollments")
    public ResponseEntity<PagedCourseEnrollmentResponse> getCourseEnrollments(
            @CurrentUser RequestUser requestUser,
            @PathVariable Long courseId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(enrollmentService.getCourseEnrollments(requestUser, courseId, pageable));
    }

}