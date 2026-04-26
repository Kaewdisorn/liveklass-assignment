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

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    @DisplayName("마지막 좌석 경쟁 시 한 명만 성공하고 나머지는 409를 반환한다")
    void lastSeatRace() throws Exception {
        int threads = 10;
        Long courseId = createOpenCourse(1).courseId();

        List<Callable<Integer>> requests = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            String studentId = String.valueOf(100 + index);
            requests.add(() -> enroll(studentId, courseId));
        }

        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses).filteredOn(status -> status == HTTP_OK).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == HTTP_CONFLICT).hasSize(threads - 1);
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 학생의 중복 요청 시 활성 수강 신청은 하나만 생성된다")
    void sameStudentDoubleSubmit() throws Exception {
        int threads = 5;
        Long courseId = createOpenCourse(10).courseId();
        String studentId = "200";

        List<Callable<Integer>> requests = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            requests.add(() -> enroll(studentId, courseId));
        }

        List<Integer> statuses = runConcurrently(requests);

        assertThat(statuses).filteredOn(status -> status == HTTP_OK).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == HTTP_CONFLICT).hasSize(threads - 1);
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("승인과 취소가 동시에 요청되면 최종 상태는 확정 또는 취소 중 하나가 된다")
    void confirmVsCancelRace() throws Exception {
        Long courseId = createOpenCourse(5).courseId();
        String studentId = "300";

        EnrollmentResponse createdEnrollment = createEnrollment(studentId, courseId);

        List<Integer> statuses = runConcurrently(List.of(
                () -> confirm(createdEnrollment.enrollmentId()),
                () -> cancel(studentId, createdEnrollment.enrollmentId())));

        assertThat(statuses).hasSize(2);
        assertThat(statuses).allMatch(status -> status == HTTP_OK || status == HTTP_CONFLICT);
        assertThat(statuses).filteredOn(status -> status == HTTP_OK).isNotEmpty();

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

    private int enroll(String studentId, Long courseId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(new CreateEnrollmentRequest(courseId)))
                .header("X-User-Id", studentId)
                .header("X-User-Role", STUDENT_ROLE))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private int confirm(Long enrollmentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", enrollmentId)
                .header("X-User-Id", CREATOR_ID)
                .header("X-User-Role", CREATOR_ROLE))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private int cancel(String studentId, Long enrollmentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", enrollmentId)
                .header("X-User-Id", studentId)
                .header("X-User-Role", STUDENT_ROLE))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private List<Integer> runConcurrently(List<Callable<Integer>> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(requests.size());

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> request : requests) {
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return request.call();
                }));
            }

            ready.await();
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(getFutureValue(future));
            }
            return statuses;
        } finally {
            executorService.shutdownNow();
        }
    }

    private Integer getFutureValue(Future<Integer> future) throws Exception {
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

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}