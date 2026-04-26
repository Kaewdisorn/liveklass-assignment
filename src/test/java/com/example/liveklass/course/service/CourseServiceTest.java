package com.example.liveklass.course.service;

import static org.mockito.ArgumentMatchers.any;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseService 단위 테스트")
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    private CourseServiceImpl courseService;

    private static final RequestUser CREATOR = new RequestUser(123L, UserRole.CREATOR);
    private static final LocalDate START_DATE = LocalDate.of(2026, 5, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 5, 2);

    private CreateCourseRequest buildCreateCourseRequest() {
        return new CreateCourseRequest(
                "Math 101",
                "Basic math course",
                new BigDecimal("50.00"),
                5,
                START_DATE,
                END_DATE);
    }

    private Course buildCourse(Long id, String title, CourseStatus status) {
        Course course = new Course();
        course.setId(id);
        course.setCreatorId(CREATOR.userId());
        course.setTitle(title);
        course.setDescription("desc");
        course.setPrice(new BigDecimal("50.00"));
        course.setCapacity(5);
        course.setStartDate(START_DATE);
        course.setEndDate(END_DATE);
        course.setStatus(status);
        OffsetDateTime now = OffsetDateTime.now();
        course.setCreatedAt(now);
        course.setUpdatedAt(now);
        return course;
    }

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository, null);
    }

    // =========================
    // createCourse
    // =========================
    @Nested
    @DisplayName("createCourse()")
    class CreateCourse {

        @Test
        @DisplayName("정상 생성 시 Course 저장 후 응답 반환")
        void createCourse_success() {
            CreateCourseRequest request = buildCreateCourseRequest();

            Course saved = new Course();
            saved.setId(1L);
            saved.setCreatorId(CREATOR.userId());
            saved.setTitle(request.title());
            saved.setDescription(request.description());
            saved.setPrice(request.price());
            saved.setCapacity(request.capacity());
            saved.setStartDate(request.startDate());
            saved.setEndDate(request.endDate());
            saved.setStatus(CourseStatus.DRAFT);
            OffsetDateTime now = OffsetDateTime.now();
            saved.setCreatedAt(now);
            saved.setUpdatedAt(now);

            when(courseRepository.save(any(Course.class))).thenReturn(saved);

            CourseDetailResponse result = courseService.createCourse(CREATOR, request);

            assertThat(result.courseId()).isEqualTo(1L);
            assertThat(result.creatorId()).isEqualTo(123L);
            assertThat(result.title()).isEqualTo("Math 101");
            assertThat(result.status()).isEqualTo(CourseStatus.DRAFT);

            ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);

            verify(courseRepository).save(captor.capture());

            Course toSave = captor.getValue();

            assertThat(toSave.getCreatorId()).isEqualTo(123L);
            assertThat(toSave.getStatus()).isEqualTo(CourseStatus.DRAFT);

        }

        @Test
        @DisplayName("CREATOR가 아닌 경우 생성 불가")
        void createCourse_invalidRole() {
            RequestUser student = new RequestUser(456L, UserRole.STUDENT);

            CreateCourseRequest request = buildCreateCourseRequest();

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> courseService.createCourse(student, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);

            verify(courseRepository, never()).save(any());
        }

    }

    // =========================
    // getCourse
    // =========================
    @Nested
    @DisplayName("getCourse()")
    class GetCourse {

        @Test
        @DisplayName("존재하는 courseId로 조회 시 응답 반환")
        void getCourse_success() {
            Course course = new Course();
            course.setId(1L);
            course.setCreatorId(123L);
            course.setTitle("Math 101");
            course.setStatus(CourseStatus.DRAFT);
            OffsetDateTime now = OffsetDateTime.now();
            course.setCreatedAt(now);
            course.setUpdatedAt(now);

            when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

            CourseDetailResponse result = courseService.getCourse(1L);

            assertThat(result.courseId()).isEqualTo(1L);
            assertThat(result.title()).isEqualTo("Math 101");
        }

        @Test
        @DisplayName("존재하지 않는 courseId로 조회 시 NOT_FOUND")
        void getCourse_notFound() {
            when(courseRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> courseService.getCourse(99L));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);

            verify(courseRepository).findById(99L);
        }
    }

    // =========================
    // getCourses
    // =========================
    @Nested
    @DisplayName("getCourses()")
    class GetCourses {

        @Test
        @DisplayName("status=null 시 전체 코스 목록 반환")
        void getCourses_withNullStatus_returnsAll() {
            Course c1 = buildCourse(1L, "Math 101", CourseStatus.DRAFT);
            Course c2 = buildCourse(2L, "Science 101", CourseStatus.OPEN);
            when(courseRepository.findAll()).thenReturn(List.of(c1, c2));

            List<CourseSummaryResponse> result = courseService.getCourses(null);

            assertThat(result).hasSize(2);
            verify(courseRepository).findAll();
            verify(courseRepository, never()).findByStatus(any());
        }

        @Test
        @DisplayName("status 지정 시 해당 상태 코스만 반환")
        void getCourses_withStatus_returnsFiltered() {
            Course c1 = buildCourse(1L, "Math 101", CourseStatus.DRAFT);
            when(courseRepository.findByStatus(CourseStatus.DRAFT)).thenReturn(List.of(c1));

            List<CourseSummaryResponse> result = courseService.getCourses(CourseStatus.DRAFT);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(CourseStatus.DRAFT);
            verify(courseRepository).findByStatus(CourseStatus.DRAFT);
            verify(courseRepository, never()).findAll();
        }
    }

    // =========================
    // updateCourseStatus
    // =========================
    @Nested
    @DisplayName("updateCourseStatus()")
    class UpdateCourseStatus {

        @Test
        @DisplayName("DRAFT에서 OPEN으로 상태 전환 성공")
        void updateCourseStatus_draftToOpen_succeeds() {
            Course course = buildCourse(1L, "Math 101", CourseStatus.DRAFT);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

            CourseDetailResponse result = courseService.updateCourseStatus(
                    CREATOR, 1L, new UpdateCourseStatusRequest(CourseStatus.OPEN));

            assertThat(result.status()).isEqualTo(CourseStatus.OPEN);
        }

        @Test
        @DisplayName("OPEN에서 CLOSED로 상태 전환 성공")
        void updateCourseStatus_openToClosed_succeeds() {
            Course course = buildCourse(1L, "Math 101", CourseStatus.OPEN);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

            CourseDetailResponse result = courseService.updateCourseStatus(
                    CREATOR, 1L, new UpdateCourseStatusRequest(CourseStatus.CLOSED));

            assertThat(result.status()).isEqualTo(CourseStatus.CLOSED);
        }

        @Test
        @DisplayName("DRAFT에서 CLOSED로 잘못된 상태 전환 시 예외 발생")
        void updateCourseStatus_invalidTransition_throws() {
            Course course = buildCourse(1L, "Math 101", CourseStatus.DRAFT);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> courseService.updateCourseStatus(
                            CREATOR, 1L, new UpdateCourseStatusRequest(CourseStatus.CLOSED)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
        }

        @Test
        @DisplayName("STUDENT 역할로 상태 변경 시 FORBIDDEN 예외 발생")
        void updateCourseStatus_asStudent_throws() {
            RequestUser student = new RequestUser(456L, UserRole.STUDENT);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> courseService.updateCourseStatus(
                            student, 1L, new UpdateCourseStatusRequest(CourseStatus.OPEN)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
            verify(courseRepository, never()).findById(any());
        }

        @Test
        @DisplayName("존재하지 않는 courseId로 상태 변경 시 NOT_FOUND 예외 발생")
        void updateCourseStatus_courseNotFound_throws() {
            when(courseRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> courseService.updateCourseStatus(
                            CREATOR, 99L, new UpdateCourseStatusRequest(CourseStatus.OPEN)));

            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        }
    }

}