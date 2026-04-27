package com.example.liveklass.enrollment.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.liveklass.enrollment.entity.Enrollment;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;

import jakarta.persistence.LockModeType;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseIdAndStatusIn(Long courseId, Collection<EnrollmentStatus> statuses);

    Page<Enrollment> findByStudentIdOrderByRequestedAtDesc(Long studentId, Pageable pageable);

    boolean existsByCourseIdAndStudentIdAndStatusIn(
            Long courseId,
            Long studentId,
            Collection<EnrollmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id = :enrollmentId")
    Optional<Enrollment> findByIdForUpdate(@Param("enrollmentId") Long enrollmentId);

    Page<Enrollment> findByCourseIdOrderByRequestedAtDesc(Long courseId, Pageable pageable);
}
