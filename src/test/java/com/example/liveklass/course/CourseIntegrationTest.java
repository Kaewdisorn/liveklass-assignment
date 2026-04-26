package com.example.liveklass.course;

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
import com.example.liveklass.enrollment.repository.EnrollmentRepository;
import com.example.liveklass.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Course 통합 테스트 (Controller - Service - Repository)")
class CourseIntegrationTest extends IntegrationTestSupport {

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
    private static final String STUDENT_ID = "2";
    private static final String CREATOR_ROLE = "CREATOR";
    private static final String STUDENT_ROLE = "STUDENT";
    private static final LocalDate START_DATE = LocalDate.of(2026, 6, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 6, 30);

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private CreateCourseRequest buildCreateRequest() {
        return buildCreateRequest("Math 101");
    }

    private CreateCourseRequest buildCreateRequest(String title) {
        return new CreateCourseRequest(
                title,
                "Basic math course",
                new BigDecimal("50.00"),
                10,
                START_DATE,
                END_DATE);
    }

    private CourseDetailResponse createCourseViaHttp(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/classes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(buildCreateRequest(title)))
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

    // =========================
    // POST /classes
    // =========================
    @Nested
    @DisplayName("POST /classes - 코스 생성")
    class CreateCourse {

        @Test
        @DisplayName("크리에이터로 코스 생성 시 201 반환 및 DB 저장 확인")
        void createCourse_asCreator_returns201AndPersists() throws Exception {
            MvcResult result = mockMvc.perform(post("/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(buildCreateRequest()))
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.courseId").isNumber())
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.title").value("Math 101"))
                    .andExpect(jsonPath("$.creatorId").value(Long.parseLong(CREATOR_ID)))
                    .andReturn();

            CourseDetailResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), CourseDetailResponse.class);
            assertThat(courseRepository.existsById(response.courseId())).isTrue();
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void createCourse_missingAuthHeaders_returns401() throws Exception {
            mockMvc.perform(post("/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(buildCreateRequest())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("학생 역할로 코스 생성 시 403 반환")
        void createCourse_asStudent_returns403() throws Exception {
            mockMvc.perform(post("/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(buildCreateRequest()))
                    .header("X-User-Id", STUDENT_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("필수 필드 누락 시 400 반환")
        void createCourse_missingTitle_returns400() throws Exception {
            String body = """
                    {"description":"desc","price":50.00,"capacity":10,
                     "startDate":"2026-06-01","endDate":"2026-06-30"}
                    """;
            mockMvc.perform(post("/classes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================
    // GET /classes
    // =========================
    @Nested
    @DisplayName("GET /classes - 코스 목록 조회")
    class GetCourses {

        @Test
        @DisplayName("전체 코스 목록 반환 (인증 불필요)")
        void getCourses_returnsAllCourses() throws Exception {
            createCourseViaHttp("Course A");
            createCourseViaHttp("Course B");

            mockMvc.perform(get("/classes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("status 파라미터로 필터링 반환")
        void getCourses_filterByStatus_returnsDraftOnly() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Draft Course");
            openCourseViaHttp(created.courseId());
            createCourseViaHttp("Another Draft");

            mockMvc.perform(get("/classes").param("status", "DRAFT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Another Draft"));
        }

        @Test
        @DisplayName("코스 없을 시 빈 배열 반환")
        void getCourses_empty_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/classes"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // =========================
    // GET /classes/{courseId}
    // =========================
    @Nested
    @DisplayName("GET /classes/{courseId} - 코스 상세 조회")
    class GetCourse {

        @Test
        @DisplayName("존재하는 코스 조회 시 상세 정보 반환")
        void getCourse_exists_returnsDetail() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");

            mockMvc.perform(get("/classes/" + created.courseId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.courseId").value(created.courseId()))
                    .andExpect(jsonPath("$.title").value("Math 101"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.activeEnrollmentCount").value(0));
        }

        @Test
        @DisplayName("존재하지 않는 courseId 조회 시 404 반환")
        void getCourse_notFound_returns404() throws Exception {
            mockMvc.perform(get("/classes/9999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    // =========================
    // PATCH /classes/{courseId}/status
    // =========================
    @Nested
    @DisplayName("PATCH /classes/{courseId}/status - 코스 상태 변경")
    class UpdateCourseStatus {

        @Test
        @DisplayName("DRAFT -> OPEN 성공")
        void updateStatus_draftToOpen_returns200() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");

            mockMvc.perform(patch("/classes/" + created.courseId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        @DisplayName("OPEN -> CLOSED 성공")
        void updateStatus_openToClosed_returns200() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");
            openCourseViaHttp(created.courseId());

            mockMvc.perform(patch("/classes/" + created.courseId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new UpdateCourseStatusRequest(CourseStatus.CLOSED)))
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        @DisplayName("잘못된 상태 전환 시 409 반환 (DRAFT → CLOSED)")
        void updateStatus_invalidTransition_returns409() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");

            mockMvc.perform(patch("/classes/" + created.courseId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new UpdateCourseStatusRequest(CourseStatus.CLOSED)))
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        }

        @Test
        @DisplayName("학생 역할로 상태 변경 시 403 반환")
        void updateStatus_byStudent_returns403() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");

            mockMvc.perform(patch("/classes/" + created.courseId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                    .header("X-User-Id", STUDENT_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("인증 헤더 없을 시 401 반환")
        void updateStatus_missingAuth_returns401() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");

            mockMvc.perform(patch("/classes/" + created.courseId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new UpdateCourseStatusRequest(CourseStatus.OPEN))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("다른 크리에이터가 상태 변경 시 403 반환")
        void updateStatus_byDifferentCreator_returns403() throws Exception {
            CourseDetailResponse created = createCourseViaHttp("Math 101");
            mockMvc.perform(patch("/classes/" + created.courseId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                    .header("X-User-Id", "999")
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // ================================================
    // GET /classes/{courseId}/enrollments
    // ================================================
    @Nested
    @DisplayName("GET /classes/{courseId}/enrollments - 강의별 수강생 목록 조회")
    class GetCourseEnrollments {

        @Test
        @DisplayName("시나리오 A: 강의 소유 CREATOR가 수강생 목록 조회 시 해당 신청이 목록에 포함됨")
        void scenarioA_ownerCreator_seesEnrollment() throws Exception {
            CourseDetailResponse course = createCourseViaHttp("Enrollment List Test");
            openCourseViaHttp(course.courseId());

            // 학생 수강 신청
            mockMvc.perform(post("/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(new CreateEnrollmentRequest(course.courseId())))
                    .header("X-User-Id", STUDENT_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isOk());

            // 소유 CREATOR 조회
            mockMvc.perform(get("/classes/" + course.courseId() + "/enrollments")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].studentId").value(Long.parseLong(STUDENT_ID)))
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("시나리오 B: 다른 CREATOR가 조회 시 403 반환")
        void scenarioB_differentCreator_returns403() throws Exception {
            CourseDetailResponse course = createCourseViaHttp("Another Creator Test");

            mockMvc.perform(get("/classes/" + course.courseId() + "/enrollments")
                    .header("X-User-Id", "999")
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("시나리오 C: STUDENT가 조회 시 403 반환")
        void scenarioC_student_returns403() throws Exception {
            CourseDetailResponse course = createCourseViaHttp("Student Access Test");

            mockMvc.perform(get("/classes/" + course.courseId() + "/enrollments")
                    .header("X-User-Id", STUDENT_ID)
                    .header("X-User-Role", STUDENT_ROLE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("시나리오 D: 존재하지 않는 courseId 조회 시 404 반환")
        void scenarioD_unknownCourse_returns404() throws Exception {
            mockMvc.perform(get("/classes/9999999/enrollments")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        }

        @Test
        @DisplayName("수강 신청이 없는 강의 조회 시 빈 목록 반환")
        void scenarioE_noneEnrolled_returnsEmptyContent() throws Exception {
            CourseDetailResponse course = createCourseViaHttp("Empty Course");

            mockMvc.perform(get("/classes/" + course.courseId() + "/enrollments")
                    .header("X-User-Id", CREATOR_ID)
                    .header("X-User-Role", CREATOR_ROLE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }
}
