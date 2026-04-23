package com.example.liveklass.course.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.liveklass.course.entity.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

}
