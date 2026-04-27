package com.example.liveklass.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.sql.DataSource;

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
import com.example.liveklass.enrollment.entity.Enrollment;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;
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

    @Autowired
    DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String CREATOR_ID = "1";
    private static final String STUDENT1_ID = "10";
    private static final String STUDENT2_ID = "11";
    private static final String STUDENT3_ID = "12";
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

    private EnrollmentResponse confirmViaHttp(String creatorId, Long enrollmentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments/" + enrollmentId + "/confirm")
                .header("X-User-Id", creatorId)
                .header("X-User-Role", CREATOR_ROLE))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EnrollmentResponse.class);
    }

    private void backdateUpdatedAt(Long enrollmentId, int daysAgo) throws Exception {
        try (var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement(
                        "UPDATE enrollment SET updated_at = NOW() - INTERVAL '" + daysAgo + " days' WHERE id = ?")) {
            stmt.setLong(1, enrollmentId);
            stmt.executeUpdate();
        }
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
        @DisplayName("정원 초과 시 200 OK 와 WAITLISTED 상태 반환")
        void createEnrollment_courseFull_returnsWaitlisted() throws Exception {
            Long courseId = openCourse(1);
            enrollViaHttp(STUDENT1_ID, courseId);

            // 두 번째 신청은 대기자로 등록되어야 하며, 거부되지 않아야 함
            MvcResult result = mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(courseId)))
                    .header("X-User-Id", STUDENT2_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WAITLISTED"))
                    .andReturn();

            EnrollmentResponse waitlisted = objectMapper.readValue(
                    result.getResponse().getContentAsString(), EnrollmentResponse.class);
            assertThat(waitlisted.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
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
        @DisplayName("다른 크리에이터가 취소 요청 시 403 반환")
        void cancelEnrollment_byDifferentCreator_returns403() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", "999")
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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

        @Test
        @DisplayName("CONFIRMED 신청 창 내 학생 취소 - 200")
        void cancelEnrollment_confirmedWithinWindow_success() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);
            confirmViaHttp(CREATOR_ID, enrollment.enrollmentId());

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("CONFIRMED 신청 창 만료 후 학생 취소 - 409 CANCELLATION_WINDOW_EXPIRED")
        void cancelEnrollment_confirmedWindowExpired_returns409() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);
            confirmViaHttp(CREATOR_ID, enrollment.enrollmentId());
            backdateUpdatedAt(enrollment.enrollmentId(), 8);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCELLATION_WINDOW_EXPIRED"));
        }

        @Test
        @DisplayName("창 만료 후 CREATOR 취소 - 200 (창 제한 없음)")
        void cancelEnrollment_expiredWindow_creatorSucceeds() throws Exception {
            Long courseId = openCourse(10);
            EnrollmentResponse enrollment = enrollViaHttp(STUDENT1_ID, courseId);
            confirmViaHttp(CREATOR_ID, enrollment.enrollmentId());
            backdateUpdatedAt(enrollment.enrollmentId(), 8);

            mockMvc.perform(post("/enrollments/" + enrollment.enrollmentId() + "/cancel")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
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
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].studentId").value(Long.parseLong(STUDENT1_ID)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("신청 없을 시 빈 content 반환")
        void getMyEnrollments_none_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/enrollments/me")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
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
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void getMyEnrollments_missingAuth_returns401() throws Exception {
            mockMvc.perform(get("/enrollments/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("3건 중 page=0&size=2 요청 시 2건, totalElements=3, totalPages=2, last=false 반환")
        void getMyEnrollments_multiPage_firstPage() throws Exception {
            Long courseId = openCourse(10);
            enrollViaHttp(STUDENT1_ID, courseId);
            enrollViaHttp(STUDENT1_ID, openCourse(10));
            enrollViaHttp(STUDENT1_ID, openCourse(10));

            mockMvc.perform(get("/enrollments/me")
                    .param("page", "0")
                    .param("size", "2")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.last").value(false))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2));
        }

        @Test
        @DisplayName("3건 중 page=1&size=2 요청 시 1건, last=true 반환")
        void getMyEnrollments_multiPage_lastPage() throws Exception {
            Long courseId = openCourse(10);
            enrollViaHttp(STUDENT1_ID, courseId);
            enrollViaHttp(STUDENT1_ID, openCourse(10));
            enrollViaHttp(STUDENT1_ID, openCourse(10));

            mockMvc.perform(get("/enrollments/me")
                    .param("page", "1")
                    .param("size", "2")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.last").value(true))
                    .andExpect(jsonPath("$.page").value(1));
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void getMyEnrollments_defaultSortDesc() throws Exception {
            Long course1 = openCourse(10);
            Long course2 = openCourse(10);
            EnrollmentResponse first = enrollViaHttp(STUDENT1_ID, course1);
            EnrollmentResponse second = enrollViaHttp(STUDENT1_ID, course2);

            mockMvc.perform(get("/enrollments/me")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].enrollmentId").value(second.enrollmentId()))
                    .andExpect(jsonPath("$.content[1].enrollmentId").value(first.enrollmentId()));
        }
    }

    // =========================
    // Waitlist 플로우
    // =========================
    @Nested
    @DisplayName("Waitlist 플로우")
    class WaitlistFlow {

        @Test
        @DisplayName("정원 1, 학생 2명 신청 → 첫 번째 PENDING, 두 번째 WAITLISTED")
        void secondEnrollment_courseFull_becomesWaitlisted() throws Exception {
            Long courseId = openCourse(1);
            EnrollmentResponse first = enrollViaHttp(STUDENT1_ID, courseId);
            EnrollmentResponse second = enrollViaHttp(STUDENT2_ID, courseId);

            assertThat(first.status()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(second.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        }

        @Test
        @DisplayName("WAITLISTED 중복 신청 시 409 DUPLICATE_ENROLLMENT")
        void enrollAgain_alreadyWaitlisted_returns409() throws Exception {
            Long courseId = openCourse(1);
            enrollViaHttp(STUDENT1_ID, courseId); // 펜딩 자리
            enrollViaHttp(STUDENT2_ID, courseId); // 대기자

            mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(courseId)))
                    .header("X-User-Id", STUDENT2_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
        }

        @Test
        @DisplayName("PENDING 취소 시 WAITLISTED → PENDING 자동 승격")
        void cancelPending_promotesWaitlisted() throws Exception {
            Long courseId = openCourse(1);
            EnrollmentResponse pending = enrollViaHttp(STUDENT1_ID, courseId);
            EnrollmentResponse waitlisted = enrollViaHttp(STUDENT2_ID, courseId);

            assertThat(waitlisted.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

            mockMvc.perform(post("/enrollments/" + pending.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            Enrollment promoted = enrollmentRepository.findById(waitlisted.enrollmentId()).orElseThrow();
            assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("WAITLISTED 취소 시 자동 승격 없음")
        void cancelWaitlisted_noPromotion() throws Exception {
            Long courseId = openCourse(1);
            EnrollmentResponse pending = enrollViaHttp(STUDENT1_ID, courseId);
            EnrollmentResponse waitlisted = enrollViaHttp(STUDENT2_ID, courseId);

            mockMvc.perform(post("/enrollments/" + waitlisted.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT2_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));

            Enrollment original = enrollmentRepository.findById(pending.enrollmentId()).orElseThrow();
            assertThat(original.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("순서 보장 — WAITLISTED 중 먼저 신청한 학생이 먼저 승격")
        void promotion_orderByRequestedAt() throws Exception {
            Long courseId = openCourse(1);
            EnrollmentResponse seat = enrollViaHttp(STUDENT1_ID, courseId); // PENDING
            EnrollmentResponse wl1 = enrollViaHttp(STUDENT2_ID, courseId); // 대기자 #1 (older)
            EnrollmentResponse wl2 = enrollViaHttp(STUDENT3_ID, courseId); // 대기자 #2 (newer)

            assertThat(wl1.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
            assertThat(wl2.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

            mockMvc.perform(post("/enrollments/" + seat.enrollmentId() + "/cancel")
                    .header("X-User-Id", STUDENT1_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk());

            Enrollment promoted = enrollmentRepository.findById(wl1.enrollmentId()).orElseThrow();
            Enrollment stillWaiting = enrollmentRepository.findById(wl2.enrollmentId()).orElseThrow();

            assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(stillWaiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        }
    }
}
