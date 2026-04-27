package com.example.liveklass.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@DisplayName("수강 신청 동시성 통합 테스트")
class EnrollmentConcurrencyTest extends IntegrationTestSupport {

    private static final int HTTP_OK = 200;
    private static final int HTTP_CONFLICT = 409;

    private static final String CREATOR_ID = "1";
    private static final String CREATOR_ROLE = "CREATOR";
    private static final String STUDENT_ROLE = "STUDENT";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    EnrollmentRepository enrollmentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private record HttpResult(int status, String body) {
    }

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    @DisplayName("정원 1 강의에 10명 동시 신청: 전원 200 성공, 1명 PENDING 나머지 WAITLISTED")
    void lastSeatRace() throws Exception {
        int threads = 10;
        Long courseId = createOpenCourse(1).courseId();

        List<Callable<HttpResult>> requests = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            String studentId = String.valueOf(100 + index);
            requests.add(() -> enroll(studentId, courseId));
        }

        List<HttpResult> results = runConcurrently(requests);

        // All requests succeed — no more COURSE_FULL errors with waitlist support
        assertThat(results).allMatch(r -> r.status() == HTTP_OK);

        List<com.example.liveklass.enrollment.entity.Enrollment> enrolled = enrollmentRepository.findAll();
        assertThat(enrolled).hasSize(threads);
        assertThat(enrolled.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING).count()).isEqualTo(1);
        assertThat(enrolled.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.WAITLISTED).count()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("정원 1 강의에 동시 10명 신청 → 1명 PENDING, 9명 WAITLISTED (응답 body 검증)")
    void concurrentEnroll_oneSeats_restWaitlisted() throws Exception {
        int threads = 10;
        Long courseId = createOpenCourse(1).courseId();

        List<Callable<HttpResult>> requests = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            String studentId = String.valueOf(500 + index);
            requests.add(() -> enroll(studentId, courseId));
        }

        List<HttpResult> results = runConcurrently(requests);

        // All requests succeed (no 409 COURSE_FULL)
        assertThat(results).allMatch(r -> r.status() == HTTP_OK);

        // Verify response body status values
        long pendingInResponse = results.stream()
                .filter(r -> {
                    try {
                        return "PENDING".equals(objectMapper.readTree(r.body()).path("status").asText());
                    } catch (Exception e) {
                        return false;
                    }
                }).count();
        long waitlistedInResponse = results.stream()
                .filter(r -> {
                    try {
                        return "WAITLISTED".equals(objectMapper.readTree(r.body()).path("status").asText());
                    } catch (Exception e) {
                        return false;
                    }
                }).count();

        assertThat(pendingInResponse).isEqualTo(1);
        assertThat(waitlistedInResponse).isEqualTo(threads - 1);

        // Also verify DB state
        List<com.example.liveklass.enrollment.entity.Enrollment> enrolled = enrollmentRepository.findAll();
        assertThat(enrolled).hasSize(threads);
        assertThat(enrolled.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING).count()).isEqualTo(1);
        assertThat(enrolled.stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.WAITLISTED).count()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("동일 학생의 중복 요청 시 활성 수강 신청은 하나만 생성된다")
    void sameStudentDoubleSubmit() throws Exception {
        int threads = 5;
        Long courseId = createOpenCourse(10).courseId();
        String studentId = "200";

        List<Callable<HttpResult>> requests = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            requests.add(() -> enroll(studentId, courseId));
        }

        List<HttpResult> results = runConcurrently(requests);

        assertThat(results).filteredOn(r -> r.status() == HTTP_OK).hasSize(1);

        List<HttpResult> failures = results.stream()
                .filter(r -> r.status() == HTTP_CONFLICT)
                .toList();
        assertThat(failures).hasSize(threads - 1);
        for (HttpResult failure : failures) {
            assertThat(errorCode(failure.body()))
                    .isIn("DUPLICATE_ENROLLMENT");
        }

        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("승인과 취소가 동시에 요청되면 최종 상태는 확정 또는 취소 중 하나가 된다")
    void confirmVsCancelRace() throws Exception {
        Long courseId = createOpenCourse(5).courseId();
        String studentId = "300";

        EnrollmentResponse createdEnrollment = createEnrollment(studentId, courseId);

        List<HttpResult> results = runConcurrently(List.of(
                () -> confirm(createdEnrollment.enrollmentId()),
                () -> cancel(studentId, createdEnrollment.enrollmentId())));

        assertThat(results).hasSize(2);

        // Both confirm-then-cancel (both 200) and cancel-then-confirm (200 + 409) are
        // valid outcomes.
        // Either way the final state must be deterministic — no corrupted/inconsistent
        // state.
        long successCount = results.stream().filter(r -> r.status() == HTTP_OK).count();
        assertThat(successCount).isGreaterThanOrEqualTo(1);

        if (successCount == 1) {
            // Cancel ran first: confirm tried on CANCELLED enrollment →
            // INVALID_STATE_TRANSITION
            HttpResult failure = results.stream().filter(r -> r.status() == HTTP_CONFLICT)
                    .findFirst().orElseThrow();
            assertThat(errorCode(failure.body())).isEqualTo("INVALID_STATE_TRANSITION");
        }
        // If successCount == 2: confirm ran first (PENDING→CONFIRMED), then cancel
        // succeeded
        // (CONFIRMED→CANCELLED within window). Both are valid transitions.

        Enrollment finalEnrollment = enrollmentRepository.findById(createdEnrollment.enrollmentId()).orElseThrow();
        assertThat(finalEnrollment.getStatus()).isIn(EnrollmentStatus.CONFIRMED, EnrollmentStatus.CANCELLED);
    }

    private CourseDetailResponse createOpenCourse(int capacity) throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "Concurrent Course",
                "desc",
                new BigDecimal("10.00"),
                capacity,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30));

        MvcResult created = mockMvc.perform(post("/classes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .header("X-User-Id", CREATOR_ID)
                .header("X-User-Role", CREATOR_ROLE))
                .andExpect(status().isCreated())
                .andReturn();

        CourseDetailResponse course = objectMapper.readValue(
                created.getResponse().getContentAsString(),
                CourseDetailResponse.class);

        MvcResult opened = mockMvc.perform(patch("/classes/{courseId}/status", course.courseId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                .header("X-User-Id", CREATOR_ID)
                .header("X-User-Role", CREATOR_ROLE))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(opened.getResponse().getContentAsString(), CourseDetailResponse.class);
    }

    private EnrollmentResponse createEnrollment(String studentId, Long courseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateEnrollmentRequest(courseId)))
                .header("X-User-Id", studentId)
                .header("X-User-Role", STUDENT_ROLE))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), EnrollmentResponse.class);
    }

    private HttpResult enroll(String studentId, Long courseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateEnrollmentRequest(courseId)))
                .header("X-User-Id", studentId)
                .header("X-User-Role", STUDENT_ROLE))
                .andReturn();
        return new HttpResult(result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private HttpResult confirm(Long enrollmentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", CREATOR_ID)
                .header("X-User-Role", CREATOR_ROLE))
                .andReturn();
        return new HttpResult(result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private HttpResult cancel(String studentId, Long enrollmentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", studentId)
                .header("X-User-Role", STUDENT_ROLE))
                .andReturn();
        return new HttpResult(result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private List<HttpResult> runConcurrently(List<Callable<HttpResult>> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requests.size());

        try {
            List<Future<HttpResult>> futures = new ArrayList<>();
            for (Callable<HttpResult> request : requests) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return request.call();
                }));
            }

            ready.await();
            start.countDown();

            List<HttpResult> results = new ArrayList<>();
            for (Future<HttpResult> future : futures) {
                results.add(getFutureValue(future));
            }
            return results;
        } finally {
            executorService.shutdownNow();
        }
    }

    private HttpResult getFutureValue(Future<HttpResult> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception wrapped) {
                throw wrapped;
            }
            throw exception;
        }
    }

    private String errorCode(String body) throws Exception {
        if (body == null || body.isBlank())
            return null;
        return objectMapper.readTree(body).path("code").asText(null);
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}