package com.example.liveklass.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.config.UserRole;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.entity.Course;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;
import com.example.liveklass.enrollment.dto.CourseEnrollmentResponse;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedCourseEnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedEnrollmentResponse;
import com.example.liveklass.enrollment.entity.Enrollment;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;
import com.example.liveklass.enrollment.repository.EnrollmentRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentService 단위 테스트")
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    private EnrollmentServiceImpl enrollmentService;

    private static final RequestUser STUDENT = new RequestUser(10L, UserRole.STUDENT);
    private static final RequestUser CREATOR = new RequestUser(1L, UserRole.CREATOR);
    private static final RequestUser OTHER_STUDENT = new RequestUser(99L, UserRole.STUDENT);

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentServiceImpl(enrollmentRepository, courseRepository, 7);
    }

    private Course buildOpenCourse(Long id, int capacity) {
        Course course = new Course();
        course.setId(id);
        course.setCreatorId(CREATOR.userId());
        course.setTitle("Test Course");
        course.setDescription("desc");
        course.setPrice(new BigDecimal("50.00"));
        course.setCapacity(capacity);
        course.setStartDate(LocalDate.of(2026, 6, 1));
        course.setEndDate(LocalDate.of(2026, 6, 30));
        course.setStatus(CourseStatus.OPEN);
        OffsetDateTime now = OffsetDateTime.now();
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        return course;
    }

    private Course buildDraftCourse(Long id) {
        Course course = buildOpenCourse(id, 10);
        course.setStatus(CourseStatus.DRAFT);
        return course;
    }

    private Enrollment buildEnrollment(Long id, Long courseId, Long studentId, EnrollmentStatus status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        enrollment.setStatus(status);
        OffsetDateTime now = OffsetDateTime.now();
        enrollment.setRequestedAt(now);
        enrollment.setUpdatedAt(now);
        return enrollment;
    }

    // =========================
    // createEnrollment
    // =========================
    @Nested
    @DisplayName("createEnrollment()")
    class CreateEnrollment {

        @Test
        @DisplayName("정상 수강 신청 시 PENDING 상태 응답 반환")
        void createEnrollment_success() {
            Course course = buildOpenCourse(5L, 10);
            Enrollment saved = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING);

            when(courseRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(course));
            when(enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(eq(5L), eq(STUDENT.userId()),
                    anyCollection()))
                    .thenReturn(false);
            when(enrollmentRepository.countByCourseIdAndStatusIn(eq(5L), anyCollection())).thenReturn(0L);
            when(enrollmentRepository.saveAndFlush(any(Enrollment.class))).thenReturn(saved);

            EnrollmentResponse result = enrollmentService.createEnrollment(STUDENT, new CreateEnrollmentRequest(5L));

            assertThat(result.enrollmentId()).isEqualTo(100L);
            assertThat(result.courseId()).isEqualTo(5L);
            assertThat(result.studentId()).isEqualTo(STUDENT.userId());
            assertThat(result.status()).isEqualTo(EnrollmentStatus.PENDING);

            ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
            verify(enrollmentRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getCourseId()).isEqualTo(5L);
            assertThat(captor.getValue().getStudentId()).isEqualTo(STUDENT.userId());
            assertThat(captor.getValue().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("CREATOR 역할로 수강 신청 시 FORBIDDEN 예외 발생")
        void createEnrollment_asCreator_throwsForbidden() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.createEnrollment(CREATOR, new CreateEnrollmentRequest(5L)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            verify(courseRepository, never()).findByIdForUpdate(any());
            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("존재하지 않는 코스 신청 시 NOT_FOUND 예외 발생")
        void createEnrollment_courseNotFound_throwsNotFound() {
            when(courseRepository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.createEnrollment(STUDENT, new CreateEnrollmentRequest(5L)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("OPEN 상태가 아닌 코스 신청 시 COURSE_NOT_OPEN 예외 발생")
        void createEnrollment_courseNotOpen_throwsCourseNotOpen() {
            when(courseRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(buildDraftCourse(5L)));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.createEnrollment(STUDENT, new CreateEnrollmentRequest(5L)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COURSE_NOT_OPEN);
            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("활성 수강 신청 중복 시 DUPLICATE_ENROLLMENT 예외 발생")
        void createEnrollment_duplicate_throwsDuplicateEnrollment() {
            when(courseRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(buildOpenCourse(5L, 10)));
            when(enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(eq(5L), eq(STUDENT.userId()),
                    anyCollection()))
                    .thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.createEnrollment(STUDENT, new CreateEnrollmentRequest(5L)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT);
            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("정원 초과 시 COURSE_FULL 예외 발생")
        void createEnrollment_courseFull_throwsCourseFull() {
            Course course = buildOpenCourse(5L, 1);
            when(courseRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(course));
            when(enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(eq(5L), eq(STUDENT.userId()),
                    anyCollection()))
                    .thenReturn(false);
            when(enrollmentRepository.countByCourseIdAndStatusIn(eq(5L), anyCollection())).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.createEnrollment(STUDENT, new CreateEnrollmentRequest(5L)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COURSE_FULL);
            verify(enrollmentRepository, never()).saveAndFlush(any());
        }
    }

    // =========================
    // confirmEnrollment
    // =========================
    @Nested
    @DisplayName("confirmEnrollment()")
    class ConfirmEnrollment {

        @Test
        @DisplayName("CREATOR가 PENDING 신청 승인 시 CONFIRMED 상태 반환")
        void confirmEnrollment_success() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING);
            Course course = buildOpenCourse(5L, 10);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));
            when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

            EnrollmentResponse result = enrollmentService.confirmEnrollment(CREATOR, 100L);

            assertThat(result.enrollmentId()).isEqualTo(100L);
            assertThat(result.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        }

        @Test
        @DisplayName("STUDENT 역할로 승인 시 FORBIDDEN 예외 발생")
        void confirmEnrollment_asStudent_throwsForbidden() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.confirmEnrollment(STUDENT, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            verify(enrollmentRepository, never()).findByIdForUpdate(any());
        }

        @Test
        @DisplayName("존재하지 않는 수강 신청 승인 시 NOT_FOUND 예외 발생")
        void confirmEnrollment_notFound_throwsNotFound() {
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.confirmEnrollment(CREATOR, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("PENDING 아닌 신청 승인 시 INVALID_STATE_TRANSITION 예외 발생")
        void confirmEnrollment_alreadyConfirmed_throwsInvalidStateTransition() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED);
            Course course = buildOpenCourse(5L, 10);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));
            when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.confirmEnrollment(CREATOR, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("CANCELLED 신청 승인 시 INVALID_STATE_TRANSITION 예외 발생")
        void confirmEnrollment_cancelled_throwsInvalidStateTransition() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.CANCELLED);
            Course course = buildOpenCourse(5L, 10);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));
            when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.confirmEnrollment(CREATOR, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("다른 크리에이터가 승인 시 FORBIDDEN 예외 발생")
        void confirmEnrollment_byOtherCreator_throwsForbidden() {
            RequestUser otherCreator = new RequestUser(999L, UserRole.CREATOR);
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING);
            Course course = buildOpenCourse(5L, 10); // creatorId = CREATOR.userId() = 1L
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));
            when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.confirmEnrollment(otherCreator, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    // =========================
    // cancelEnrollment
    // =========================
    @Nested
    @DisplayName("cancelEnrollment()")
    class CancelEnrollment {

        @Test
        @DisplayName("학생 본인이 PENDING 신청 취소 시 CANCELLED 상태 반환")
        void cancelEnrollment_byOwnerStudent_success() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));

            EnrollmentResponse result = enrollmentService.cancelEnrollment(STUDENT, 100L);

            assertThat(result.enrollmentId()).isEqualTo(100L);
            assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CREATOR가 수강 신청 취소 시 CANCELLED 상태 반환")
        void cancelEnrollment_byCreator_success() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));

            EnrollmentResponse result = enrollmentService.cancelEnrollment(CREATOR, 100L);

            assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("존재하지 않는 수강 신청 취소 시 NOT_FOUND 예외 발생")
        void cancelEnrollment_notFound_throwsNotFound() {
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.cancelEnrollment(STUDENT, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        }

        @Test
        @DisplayName("본인 아닌 학생이 취소 시도 시 FORBIDDEN 예외 발생")
        void cancelEnrollment_byOtherStudent_throwsForbidden() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.cancelEnrollment(OTHER_STUDENT, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("이미 취소된 수강 신청 재취소 시 INVALID_STATE_TRANSITION 예외 발생")
        void cancelEnrollment_alreadyCancelled_throwsInvalidStateTransition() {
            Enrollment enrollment = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.CANCELLED);
            when(enrollmentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(enrollment));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.cancelEnrollment(STUDENT, 100L));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("CONFIRMED 신청을 창 내에서 학생이 취소 → 성공")
        void cancelEnrollment_confirmedWithinWindow_success() {
            Enrollment enrollment = buildEnrollment(1L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED);
            enrollment.setUpdatedAt(OffsetDateTime.now().minusDays(3));
            when(enrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));

            EnrollmentResponse result = enrollmentService.cancelEnrollment(STUDENT, 1L);

            assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED 신청을 창 만료 후 학생이 취소 → CANCELLATION_WINDOW_EXPIRED")
        void cancelEnrollment_confirmedWindowExpired_throws() {
            Enrollment enrollment = buildEnrollment(1L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED);
            enrollment.setUpdatedAt(OffsetDateTime.now().minusDays(8));
            when(enrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.cancelEnrollment(STUDENT, 1L));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CANCELLATION_WINDOW_EXPIRED);
        }

        @Test
        @DisplayName("창 만료 후에도 CREATOR는 취소 가능")
        void cancelEnrollment_expiredWindow_creatorCanStillCancel() {
            Enrollment enrollment = buildEnrollment(1L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED);
            enrollment.setUpdatedAt(OffsetDateTime.now().minusDays(8));
            when(enrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));

            EnrollmentResponse result = enrollmentService.cancelEnrollment(CREATOR, 1L);

            assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("PENDING 신청은 창 만료 여부와 무관하게 학생이 취소 가능")
        void cancelEnrollment_pendingAlwaysCancellable() {
            Enrollment enrollment = buildEnrollment(1L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING);
            enrollment.setUpdatedAt(OffsetDateTime.now().minusDays(30));
            when(enrollmentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(enrollment));

            EnrollmentResponse result = enrollmentService.cancelEnrollment(STUDENT, 1L);

            assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }
    }

    // =========================
    // getMyEnrollments
    // =========================
    @Nested
    @DisplayName("getMyEnrollments()")
    class GetMyEnrollments {

        @Test
        @DisplayName("수강 신청 목록 조회 시 studentId 기준 반환")
        void getMyEnrollments_success() {
            List<Enrollment> enrollments = List.of(
                    buildEnrollment(1L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED),
                    buildEnrollment(2L, 6L, STUDENT.userId(), EnrollmentStatus.PENDING));
            Pageable pageable = PageRequest.of(0, 20);
            Page<Enrollment> page = new PageImpl<>(enrollments, pageable, enrollments.size());

            when(enrollmentRepository.findByStudentIdOrderByRequestedAtDesc(STUDENT.userId(), pageable))
                    .thenReturn(page);

            PagedEnrollmentResponse result = enrollmentService.getMyEnrollments(STUDENT, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).enrollmentId()).isEqualTo(1L);
            assertThat(result.content().get(0).status()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(result.content().get(1).enrollmentId()).isEqualTo(2L);
            assertThat(result.content().get(1).status()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.last()).isTrue();

            verify(enrollmentRepository).findByStudentIdOrderByRequestedAtDesc(STUDENT.userId(), pageable);
        }

        @Test
        @DisplayName("수강 신청 내역 없는 경우 빈 목록 반환")
        void getMyEnrollments_emptyList() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Enrollment> page = new PageImpl<>(List.of(), pageable, 0);

            when(enrollmentRepository.findByStudentIdOrderByRequestedAtDesc(STUDENT.userId(), pageable))
                    .thenReturn(page);

            PagedEnrollmentResponse result = enrollmentService.getMyEnrollments(STUDENT, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
            verify(enrollmentRepository).findByStudentIdOrderByRequestedAtDesc(STUDENT.userId(), pageable);
        }

        @Test
        @DisplayName("멀티 페이지 시나리오: page/size/totalElements/totalPages/last 필드 전부 매핑 검증")
        void getMyEnrollments_multiPageMetadataMapped() {
            Pageable pageable = PageRequest.of(1, 2);
            List<Enrollment> enrollments = List.of(
                    buildEnrollment(3L, 5L, STUDENT.userId(), EnrollmentStatus.PENDING),
                    buildEnrollment(4L, 5L, STUDENT.userId(), EnrollmentStatus.CANCELLED));
            Page<Enrollment> page = new PageImpl<>(enrollments, pageable, 5L);

            when(enrollmentRepository.findByStudentIdOrderByRequestedAtDesc(STUDENT.userId(), pageable))
                    .thenReturn(page);

            PagedEnrollmentResponse result = enrollmentService.getMyEnrollments(STUDENT, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(5L);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.last()).isFalse();
        }
    }

    // =========================
    // getCourseEnrollments
    // =========================
    @Nested
    @DisplayName("getCourseEnrollments()")
    class GetCourseEnrollments {

        @Test
        @DisplayName("강의 소유 CREATOR 요청 시 페이지 응답 반환 및 필드 매핑 검증")
        void givenCreatorOwner_whenGetCourseEnrollments_thenReturnPage() {
            Pageable pageable = PageRequest.of(0, 20);
            Course course = buildOpenCourse(5L, 10); // creatorId = CREATOR.userId() = 1L
            Enrollment e = buildEnrollment(100L, 5L, STUDENT.userId(), EnrollmentStatus.CONFIRMED);
            Page<Enrollment> page = new PageImpl<>(List.of(e), pageable, 1);

            when(courseRepository.findById(5L)).thenReturn(Optional.of(course));
            when(enrollmentRepository.findByCourseIdOrderByRequestedAtDesc(5L, pageable)).thenReturn(page);

            PagedCourseEnrollmentResponse result = enrollmentService.getCourseEnrollments(CREATOR, 5L, pageable);

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content()).hasSize(1);
            CourseEnrollmentResponse item = result.content().get(0);
            assertThat(item.enrollmentId()).isEqualTo(100L);
            assertThat(item.studentId()).isEqualTo(STUDENT.userId());
            assertThat(item.status()).isEqualTo(EnrollmentStatus.CONFIRMED);

            verify(enrollmentRepository).findByCourseIdOrderByRequestedAtDesc(5L, pageable);
        }

        @Test
        @DisplayName("STUDENT 역할 요청 시 FORBIDDEN 예외 발생")
        void givenStudentRole_whenGetCourseEnrollments_thenThrowForbidden() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.getCourseEnrollments(STUDENT, 5L, PageRequest.of(0, 20)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            verify(courseRepository, never()).findById(any());
            verify(enrollmentRepository, never()).findByCourseIdOrderByRequestedAtDesc(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 강의 조회 시 COURSE_NOT_FOUND 예외 발생")
        void givenCourseNotFound_whenGetCourseEnrollments_thenThrowNotFound() {
            when(courseRepository.findById(5L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.getCourseEnrollments(CREATOR, 5L, PageRequest.of(0, 20)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COURSE_NOT_FOUND);
            verify(enrollmentRepository, never()).findByCourseIdOrderByRequestedAtDesc(any(), any());
        }

        @Test
        @DisplayName("강의 소유자가 아닌 CREATOR 요청 시 FORBIDDEN 예외 발생")
        void givenDifferentCreator_whenGetCourseEnrollments_thenThrowForbidden() {
            RequestUser otherCreator = new RequestUser(999L, UserRole.CREATOR);
            Course course = buildOpenCourse(5L, 10); // creatorId = 1L != 999L

            when(courseRepository.findById(5L)).thenReturn(Optional.of(course));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> enrollmentService.getCourseEnrollments(otherCreator, 5L, PageRequest.of(0, 20)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            verify(enrollmentRepository, never()).findByCourseIdOrderByRequestedAtDesc(any(), any());
        }
    }
}
