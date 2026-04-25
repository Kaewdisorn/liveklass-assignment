package com.example.liveklass.course.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.entity.Course;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository);
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
    }

}