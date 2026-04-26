package com.example.liveklass.enrollment.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.liveklass.enrollment.entity.Enrollment;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;

import jakarta.persistence.LockModeType;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseIdAndStatusIn(Long courseId, Collection<EnrollmentStatus> statuses);

    List<Enrollment> findByStudentIdOrderByRequestedAtDesc(Long studentId);

    boolean existsByCourseIdAndStudentIdAndStatusIn(
            Long courseId,
            Long studentId,
            Collection<EnrollmentStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id = :enrollmentId")
    Optional<Enrollment> findByIdForUpdate(@Param("enrollmentId") Long enrollmentId);
}
