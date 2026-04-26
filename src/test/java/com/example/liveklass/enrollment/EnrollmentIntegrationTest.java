package com.example.liveklass.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.dto.UpdateCourseStatusRequest;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.repository.CourseRepository;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.repository.EnrollmentRepository;
import com.example.liveklass.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Enrollment 통합 테스트 (Controller - Service - Repository)")
class EnrollmentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String CREATOR_ID = "1";
    private static final String STUDENT1_ID = "10";
    private static final String STUDENT2_ID = "11";
    private static final String CREATOR_ROLE = "CREATOR";
    private static final String STUDENT_ROLE = "STUDENT";

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private CourseDetailResponse createCourseViaHttp(int capacity) throws Exception {
        CreateCourseRequest req = new CreateCourseRequest(
                "Test Course",
                "desc",
                new BigDecimal("50.00"),
                capacity,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30));
        MvcResult result = mockMvc.perform(post("/classes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req))
                .header("X-User-Id", CREATOR_ID)
                .header("X-User-Role", CREATOR_ROLE))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CourseDetailResponse.class);
    }

    private CourseDetailResponse openCourseViaHttp(Long courseId) throws Exception {
        MvcResult result = mockMvc.perform(patch("/classes/" + courseId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                .header("X-User-Id", CREATOR_ID)
                .header("X-User-Role", CREATOR_ROLE))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CourseDetailResponse.class);
    }

    private Long openCourse(int capacity) throws Exception {
        CourseDetailResponse created = createCourseViaHttp(capacity);
        CourseDetailResponse opened = openCourseViaHttp(created.courseId());
        return opened.courseId();
    }

    private EnrollmentResponse enrollViaHttp(String studentId, Long courseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateEnrollmentRequest(courseId)))
                .header("X-User-Id", studentId)
                .header("X-User-Role", STUDENT_ROLE))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EnrollmentResponse.class);
    }

    // =========================
    // POST /enrollments
    // =========================
    @Nested
    @DisplayName("POST /enrollments - 강의 신청")
    class CreateEnrollment {

        @Test
        @DisplayName("학생이 열린 코스에 신청 시 200 반환 및 PENDING 상태")
        void createEnrollment_asStudent_returns200AndPending() throws Exception {
            Long courseId = openCourse(10);

            MvcResult result = mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(courseId)))
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enrollmentId").isNumber())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.studentId").value(Long.parseLong(STUDENT1_ID)))
                    .andExpect(jsonPath("$.courseId").value(courseId))
                    .andReturn();

            EnrollmentResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), EnrollmentResponse.class);
            assertThat(enrollmentRepository.existsById(response.enrollmentId())).isTrue();
        }

        @Test
        @DisplayName("동일 학생이 동일 코스에 중복 신청 시 409 반환")
        void createEnrollment_duplicate_returns409() throws Exception {
            Long courseId = openCourse(10);
            enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(courseId)))
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
        }

        @Test
        @DisplayName("DRAFT 상태 코스에 신청 시 409 반환")
        void createEnrollment_courseNotOpen_returns409() throws Exception {
            CourseDetailResponse draft = createCourseViaHttp(10);

            mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(draft.courseId())))
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COURSE_NOT_OPEN"));
        }

        @Test
        @DisplayName("정원 초과 시 409 반환")
        void createEnrollment_courseFull_returns409() throws Exception {
            Long courseId = openCourse(1);
            enrollViaHttp(STUDENT1_ID, courseId);

            EnrollmentResponse first = enrollmentRepository
                    .findAll()
                    .stream()
                    .filter(e -> e.getStudentId().equals(Long.parseLong(STUDENT1_ID)))
                    .findFirst()
                    .map(e -> new EnrollmentResponse(
                            e.getId(), e.getCourseId(), e.getStudentId(), e.getStatus(),
                            e.getRequestedAt(), e.getUpdatedAt()))
                    .orElseThrow();

            mockMvc.perform(post("/enrollments/" + first.enrollmentId() + "/confirm")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(courseId)))
                    .header("X-User-Id", STUDENT2_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COURSE_FULL"));
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void createEnrollment_missingAuth_returns401() throws Exception {
            Long courseId = openCourse(10);

            mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(courseId))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    // =========================
    // POST /enrollments/{enrollmentId}/confirm
    // =========================
    @Nested
    @DisplayName("POST /enrollments/{enrollmentId}/confirm - 신청 승인")
    class ConfirmEnrollment {

        @Test
        @DisplayName("크리에이터가 신청 승인 시 200 반환 및 CONFIRMED 상태")
        void confirmEnrollment_asCreator_returns200AndConfirmed() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/confirm")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.enrollmentId").value(enrollment.enrollmentId()));
        }

        @Test
        @DisplayName("학생이 승인 요청 시 403 반환")
        void confirmEnrollment_asStudent_returns403() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/confirm")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("존재하지 않는 신청 승인 시 404 반환")
        void confirmEnrollment_notFound_returns404() throws Exception {
            mockMvc.perform(post("/enrollments/9999999/confirm")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void confirmEnrollment_missingAuth_returns401() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/confirm"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("다른 크리에이터가 승인 요청 시 403 반환")
        void confirmEnrollment_byDifferentCreator_returns403() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/confirm")
                    .header("X-User-Id", "999")
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // =========================
    // POST /enrollments/{enrollmentId}/cancel
    // =========================
    @Nested
    @DisplayName("POST /enrollments/{enrollmentId}/cancel - 신청 취소")
    class CancelEnrollment {

        @Test
        @DisplayName("학생이 자신의 신청 취소 시 200 반환 및 CANCELLED 상태")
        void cancelEnrollment_byStudent_returns200AndCancelled() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.enrollmentId").value(enrollment.enrollmentId()));
        }

        @Test
        @DisplayName("크리에이터가 신청 취소 시 200 반환 및 CANCELLED 상태")
        void cancelEnrollment_byCreator_returns200AndCancelled() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("다른 학생이 취소 요청 시 403 반환")
        void cancelEnrollment_byOtherStudent_returns403() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT2_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("취소된 신청을 재취소 시 409 반환")
        void cancelEnrollment_alreadyCancelled_returns409() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void cancelEnrollment_missingAuth_returns401() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================
    // GET /enrollments/me
    // =========================
    @Nested
    @DisplayName("GET /enrollments/me - 내 신청 목록 조회")
    class GetMyEnrollments {

        @Test
        @DisplayName("자신의 신청 목록만 반환")
        void getMyEnrollments_returnsOnlyOwnEnrollments() throws Exception {
            Long courseId = openCourse(10);
            enrollViaHttp(STUDENT1_ID, courseId);
            enrollViaHttp(STUDENT2_ID, courseId);

            mockMvc.perform(get("/enrollments/me")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].studentId").value(Long.parseLong(STUDENT1_ID)));
        }

        @Test
        @DisplayName("신청 없을 시 빈 배열 반환")
        void getMyEnrollments_none_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/enrollments/me")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("취소된 신청도 목록에 포함")
        void getMyEnrollments_includesCancelled() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/enrollments/me")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("CANCELLED"));
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void getMyEnrollments_missingAuth_returns401() throws Exception {
            mockMvc.perform(get("/enrollments/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
