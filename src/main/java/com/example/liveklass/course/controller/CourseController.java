package com.example.liveklass.course.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.liveklass.common.config.CurrentUser;
import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.service.CourseService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/classes")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
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

    // Course 상세 조회
    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourse(@PathVariable Long courseId) {
        return ResponseEntity.ok(courseService.getCourse(courseId));
    }

    @GetMapping("/test")
    public String test() {
        return "hello";
    }
}