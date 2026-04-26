package com.example.liveklass.enrollment.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveklass.enrollment.dto.EnrollmentResponse;
import com.example.liveklass.enrollment.dto.PagedEnrollmentResponse;
import com.example.liveklass.enrollment.enums.EnrollmentStatus;
import com.example.liveklass.enrollment.service.EnrollmentService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnrollmentController.class)
@DisplayName("EnrollmentController 단위 테스트")
class EnrollmentControllerTest {

        @Autowired
        MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        @MockitoBean
        private EnrollmentService enrollmentService;

        private static final String STUDENT_ID = "10";
        private static final String STUDENT_ROLE = "STUDENT";
        private static final String CREATOR_ID = "1";
        private static final String CREATOR_ROLE = "CREATOR";

        private MockHttpServletRequestBuilder postWithStudentHeaders(String url, Object body) throws Exception {
                return post(url)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("X-User-Id", STUDENT_ID)
                                .header("X-User-Role", STUDENT_ROLE);
        }

        private MockHttpServletRequestBuilder postWithCreatorHeaders(String url) {
                return post(url)
                                .header("X-User-Id", CREATOR_ID)
                                .header("X-User-Role", CREATOR_ROLE);
        }

        private MockHttpServletRequestBuilder postWithStudentHeaders(String url) {
                return post(url)
                                .header("X-User-Id", STUDENT_ID)
                                .header("X-User-Role", STUDENT_ROLE);
        }

        private EnrollmentResponse buildEnrollmentResponse(EnrollmentStatus status) {
                return new EnrollmentResponse(
                                100L,
                                5L,
                                Long.parseLong(STUDENT_ID),
                                status,
                                OffsetDateTime.now(),
                                OffsetDateTime.now());
        }

        // =========================
        // POST /enrollments
        // =========================
        @Nested
        @DisplayName("POST /enrollments - 수강 신청")
        class CreateEnrollment {

                @Test
                @DisplayName("정상 요청 시 200 OK와 PENDING 응답 반환")
                void createEnrollment_success() throws Exception {
                        CreateEnrollmentRequest request = new CreateEnrollmentRequest(5L);
                        EnrollmentResponse response = buildEnrollmentResponse(EnrollmentStatus.PENDING);

                        when(enrollmentService.createEnrollment(any(RequestUser.class),
                                        any(CreateEnrollmentRequest.class)))
                                        .thenReturn(response);

                        mockMvc.perform(postWithStudentHeaders("/enrollments", request))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.enrollmentId").value(100))
                                        .andExpect(jsonPath("$.courseId").value(5))
                                        .andExpect(jsonPath("$.studentId").value(10))
                                        .andExpect(jsonPath("$.status").value("PENDING"));

                        ArgumentCaptor<RequestUser> userCaptor = ArgumentCaptor.forClass(RequestUser.class);
                        ArgumentCaptor<CreateEnrollmentRequest> requestCaptor = ArgumentCaptor
                                        .forClass(CreateEnrollmentRequest.class);

                        verify(enrollmentService).createEnrollment(userCaptor.capture(), requestCaptor.capture());

                        assertThat(userCaptor.getValue().userId()).isEqualTo(10L);
                        assertThat(requestCaptor.getValue().courseId()).isEqualTo(5L);
                }

                @Test
                @DisplayName("사용자 헤더 누락 시 401 Unauthorized 반환 및 서비스 미호출")
                void createEnrollment_missingHeaders() throws Exception {
                        mockMvc.perform(post("/enrollments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(new CreateEnrollmentRequest(5L))))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                                        .andExpect(jsonPath("$.path").value("/enrollments"));

                        verify(enrollmentService, never()).createEnrollment(any(), any());
                }

                @Test
                @DisplayName("courseId 누락(null) 시 400 Bad Request 반환")
                void createEnrollment_nullCourseId() throws Exception {
                        mockMvc.perform(postWithStudentHeaders("/enrollments", new CreateEnrollmentRequest(null)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").exists());

                        verify(enrollmentService, never()).createEnrollment(any(), any());
                }

                @Test
                @DisplayName("수강 신청 불가 코스(COURSE_NOT_OPEN) 시 409 Conflict 반환")
                void createEnrollment_courseNotOpen() throws Exception {
                        when(enrollmentService.createEnrollment(any(), any()))
                                        .thenThrow(new BusinessException(ErrorCode.COURSE_NOT_OPEN,
                                                        "Course is not open for enrollment."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments", new CreateEnrollmentRequest(5L)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.COURSE_NOT_OPEN.name()))
                                        .andExpect(jsonPath("$.message").value("Course is not open for enrollment."));

                        verify(enrollmentService).createEnrollment(any(), any());
                }

                @Test
                @DisplayName("중복 수강 신청(DUPLICATE_ENROLLMENT) 시 409 Conflict 반환")
                void createEnrollment_duplicateEnrollment() throws Exception {
                        when(enrollmentService.createEnrollment(any(), any()))
                                        .thenThrow(
                                                        new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT,
                                                                        "Active enrollment already exists."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments", new CreateEnrollmentRequest(5L)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_ENROLLMENT.name()))
                                        .andExpect(jsonPath("$.message").value("Active enrollment already exists."));

                        verify(enrollmentService).createEnrollment(any(), any());
                }

                @Test
                @DisplayName("정원 초과(COURSE_FULL) 시 409 Conflict 반환")
                void createEnrollment_courseFull() throws Exception {
                        when(enrollmentService.createEnrollment(any(), any()))
                                        .thenThrow(new BusinessException(ErrorCode.COURSE_FULL,
                                                        "Course capacity has been reached."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments", new CreateEnrollmentRequest(5L)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.COURSE_FULL.name()))
                                        .andExpect(jsonPath("$.message").value("Course capacity has been reached."));

                        verify(enrollmentService).createEnrollment(any(), any());
                }

                @Test
                @DisplayName("존재하지 않는 코스 요청 시 404 Not Found 반환")
                void createEnrollment_courseNotFound() throws Exception {
                        when(enrollmentService.createEnrollment(any(), any()))
                                        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Course not found."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments", new CreateEnrollmentRequest(5L)))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

                        verify(enrollmentService).createEnrollment(any(), any());
                }
        }

        // =========================
        // POST /enrollments/{enrollmentId}/confirm
        // =========================
        @Nested
        @DisplayName("POST /enrollments/{enrollmentId}/confirm - 수강 신청 승인")
        class ConfirmEnrollment {

                @Test
                @DisplayName("크리에이터가 정상 승인 시 200 OK와 CONFIRMED 응답 반환")
                void confirmEnrollment_success() throws Exception {
                        EnrollmentResponse response = buildEnrollmentResponse(EnrollmentStatus.CONFIRMED);

                        when(enrollmentService.confirmEnrollment(any(RequestUser.class), eq(100L)))
                                        .thenReturn(response);

                        mockMvc.perform(postWithCreatorHeaders("/enrollments/100/confirm"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.enrollmentId").value(100))
                                        .andExpect(jsonPath("$.status").value("CONFIRMED"));

                        ArgumentCaptor<RequestUser> userCaptor = ArgumentCaptor.forClass(RequestUser.class);
                        verify(enrollmentService).confirmEnrollment(userCaptor.capture(), eq(100L));

                        assertThat(userCaptor.getValue().userId()).isEqualTo(1L);
                }

                @Test
                @DisplayName("사용자 헤더 누락 시 401 Unauthorized 반환 및 서비스 미호출")
                void confirmEnrollment_missingHeaders() throws Exception {
                        mockMvc.perform(post("/enrollments/100/confirm"))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

                        verify(enrollmentService, never()).confirmEnrollment(any(), any());
                }

                @Test
                @DisplayName("존재하지 않는 수강 신청 승인 시 404 Not Found 반환")
                void confirmEnrollment_notFound() throws Exception {
                        when(enrollmentService.confirmEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found."));

                        mockMvc.perform(postWithCreatorHeaders("/enrollments/100/confirm"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()))
                                        .andExpect(jsonPath("$.message").value("Enrollment not found."))
                                        .andExpect(jsonPath("$.path").value("/enrollments/100/confirm"));

                        verify(enrollmentService).confirmEnrollment(any(), eq(100L));
                }

                @Test
                @DisplayName("PENDING 아닌 신청 승인 시 409 Conflict 반환")
                void confirmEnrollment_invalidStateTransition() throws Exception {
                        when(enrollmentService.confirmEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(
                                                        ErrorCode.INVALID_STATE_TRANSITION,
                                                        "Only PENDING enrollments can be confirmed."));

                        mockMvc.perform(postWithCreatorHeaders("/enrollments/100/confirm"))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_STATE_TRANSITION.name()))
                                        .andExpect(jsonPath("$.message")
                                                        .value("Only PENDING enrollments can be confirmed."));

                        verify(enrollmentService).confirmEnrollment(any(), eq(100L));
                }

                @Test
                @DisplayName("학생 역할로 승인 시 서비스에서 403 Forbidden 반환")
                void confirmEnrollment_forbiddenForStudent() throws Exception {
                        when(enrollmentService.confirmEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(ErrorCode.FORBIDDEN,
                                                        "Creator role is required."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments/100/confirm"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));

                        verify(enrollmentService).confirmEnrollment(any(), eq(100L));
                }

                @Test
                @DisplayName("코스 소유자가 아닌 크리에이터 승인 시 403 Forbidden 반환")
                void confirmEnrollment_forbiddenForNonOwnerCreator() throws Exception {
                        when(enrollmentService.confirmEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(ErrorCode.FORBIDDEN,
                                                        "Only the course creator can confirm enrollments."));

                        mockMvc.perform(postWithCreatorHeaders("/enrollments/100/confirm"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()))
                                        .andExpect(jsonPath("$.message")
                                                        .value("Only the course creator can confirm enrollments."));

                        verify(enrollmentService).confirmEnrollment(any(), eq(100L));
                }
        }

        // =========================
        // POST /enrollments/{enrollmentId}/cancel
        // =========================
        @Nested
        @DisplayName("POST /enrollments/{enrollmentId}/cancel - 수강 취소")
        class CancelEnrollment {

                @Test
                @DisplayName("크리에이터가 수강 취소 시 200 OK와 CANCELLED 응답 반환")
                void cancelEnrollment_byCreator_success() throws Exception {
                        EnrollmentResponse response = buildEnrollmentResponse(EnrollmentStatus.CANCELLED);

                        when(enrollmentService.cancelEnrollment(any(RequestUser.class), eq(100L)))
                                        .thenReturn(response);

                        mockMvc.perform(postWithCreatorHeaders("/enrollments/100/cancel"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.enrollmentId").value(100))
                                        .andExpect(jsonPath("$.status").value("CANCELLED"));

                        verify(enrollmentService).cancelEnrollment(any(), eq(100L));
                }

                @Test
                @DisplayName("학생 본인이 수강 취소 시 200 OK와 CANCELLED 응답 반환")
                void cancelEnrollment_byStudent_success() throws Exception {
                        EnrollmentResponse response = buildEnrollmentResponse(EnrollmentStatus.CANCELLED);

                        when(enrollmentService.cancelEnrollment(any(RequestUser.class), eq(100L)))
                                        .thenReturn(response);

                        mockMvc.perform(postWithStudentHeaders("/enrollments/100/cancel"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.status").value("CANCELLED"));

                        ArgumentCaptor<RequestUser> userCaptor = ArgumentCaptor.forClass(RequestUser.class);
                        verify(enrollmentService).cancelEnrollment(userCaptor.capture(), eq(100L));

                        assertThat(userCaptor.getValue().userId()).isEqualTo(10L);
                }

                @Test
                @DisplayName("사용자 헤더 누락 시 401 Unauthorized 반환 및 서비스 미호출")
                void cancelEnrollment_missingHeaders() throws Exception {
                        mockMvc.perform(post("/enrollments/100/cancel"))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

                        verify(enrollmentService, never()).cancelEnrollment(any(), any());
                }

                @Test
                @DisplayName("권한 없는 사용자 취소 시도 시 403 Forbidden 반환")
                void cancelEnrollment_forbidden() throws Exception {
                        when(enrollmentService.cancelEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(ErrorCode.FORBIDDEN,
                                                        "You cannot cancel this enrollment."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments/100/cancel"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()))
                                        .andExpect(jsonPath("$.message").value("You cannot cancel this enrollment."));

                        verify(enrollmentService).cancelEnrollment(any(), eq(100L));
                }

                @Test
                @DisplayName("이미 취소된 수강 신청 재취소 시 409 Conflict 반환")
                void cancelEnrollment_alreadyCancelled() throws Exception {
                        when(enrollmentService.cancelEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(
                                                        ErrorCode.INVALID_STATE_TRANSITION,
                                                        "Enrollment is already cancelled."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments/100/cancel"))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_STATE_TRANSITION.name()))
                                        .andExpect(jsonPath("$.message").value("Enrollment is already cancelled."));

                        verify(enrollmentService).cancelEnrollment(any(), eq(100L));
                }

                @Test
                @DisplayName("존재하지 않는 수강 신청 취소 시 404 Not Found 반환")
                void cancelEnrollment_notFound() throws Exception {
                        when(enrollmentService.cancelEnrollment(any(), eq(100L)))
                                        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found."));

                        mockMvc.perform(postWithStudentHeaders("/enrollments/100/cancel"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

                        verify(enrollmentService).cancelEnrollment(any(), eq(100L));
                }
        }

        // =========================
        // GET /enrollments/me
        // =========================
        @Nested
        @DisplayName("GET /enrollments/me - 내 수강 신청 목록 조회")
        class GetMyEnrollments {

                @Test
                @DisplayName("정상 조회 시 200 OK와 페이징 응답 반환")
                void getMyEnrollments_success() throws Exception {
                        List<EnrollmentResponse> items = List.of(
                                        buildEnrollmentResponse(EnrollmentStatus.PENDING),
                                        buildEnrollmentResponse(EnrollmentStatus.CONFIRMED));
                        PagedEnrollmentResponse response = new PagedEnrollmentResponse(items, 0, 20, 2, 1, true);

                        when(enrollmentService.getMyEnrollments(any(RequestUser.class), any(Pageable.class)))
                                        .thenReturn(response);

                        mockMvc.perform(get("/enrollments/me")
                                        .header("X-User-Id", STUDENT_ID)
                                        .header("X-User-Role", STUDENT_ROLE))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content").isArray())
                                        .andExpect(jsonPath("$.content.length()").value(2))
                                        .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                                        .andExpect(jsonPath("$.content[1].status").value("CONFIRMED"))
                                        .andExpect(jsonPath("$.totalElements").value(2))
                                        .andExpect(jsonPath("$.totalPages").value(1))
                                        .andExpect(jsonPath("$.last").value(true));

                        ArgumentCaptor<RequestUser> userCaptor = ArgumentCaptor.forClass(RequestUser.class);
                        verify(enrollmentService).getMyEnrollments(userCaptor.capture(), any(Pageable.class));
                        assertThat(userCaptor.getValue().userId()).isEqualTo(10L);
                }

                @Test
                @DisplayName("수강 신청 내역 없는 경우 200 OK와 빈 content 반환")
                void getMyEnrollments_emptyList() throws Exception {
                        PagedEnrollmentResponse response = new PagedEnrollmentResponse(List.of(), 0, 20, 0, 0, true);
                        when(enrollmentService.getMyEnrollments(any(RequestUser.class), any(Pageable.class)))
                                        .thenReturn(response);

                        mockMvc.perform(get("/enrollments/me")
                                        .header("X-User-Id", STUDENT_ID)
                                        .header("X-User-Role", STUDENT_ROLE))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content").isArray())
                                        .andExpect(jsonPath("$.content").isEmpty())
                                        .andExpect(jsonPath("$.totalElements").value(0));

                        verify(enrollmentService).getMyEnrollments(any(RequestUser.class), any(Pageable.class));
                }

                @Test
                @DisplayName("사용자 헤더 누락 시 401 Unauthorized 반환 및 서비스 미호출")
                void getMyEnrollments_missingHeaders() throws Exception {
                        mockMvc.perform(get("/enrollments/me"))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                                        .andExpect(jsonPath("$.path").value("/enrollments/me"));

                        verify(enrollmentService, never()).getMyEnrollments(any(), any());
                }
        }
}
