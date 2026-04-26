package com.example.liveklass.course.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CourseSummaryResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.dto.UpdateCourseStatusRequest;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourseController.class)
@DisplayName("CourseController 단위 테스트")
class CourseControllerTest {

        @Autowired
        MockMvc mockMvc;

        private final ObjectMapper objectMapper = new ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        @MockitoBean
        private CourseService courseService;

        private static final String USER_ID = "123";
        private static final String USER_ROLE = "CREATOR";
        private static final LocalDate START_DATE = LocalDate.of(2026, 5, 1);
        private static final LocalDate END_DATE = LocalDate.of(2026, 5, 2);

        private MockHttpServletRequestBuilder postWithUserHeaders(String url, Object body) throws Exception {
                return post(url)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("X-User-Id", USER_ID)
                                .header("X-User-Role", USER_ROLE);
        }

        private MockHttpServletRequestBuilder patchWithUserHeaders(String url, Object body) throws Exception {
                return patch(url)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("X-User-Id", USER_ID)
                                .header("X-User-Role", USER_ROLE);
        }

        private CreateCourseRequest buildCreateCourseRequest() {
                return new CreateCourseRequest(
                                "Math 101",
                                "Basic math course",
                                new BigDecimal("50.00"),
                                5,
                                START_DATE,
                                END_DATE);
        }

        private CourseDetailResponse buildCreateCourseResponse(CreateCourseRequest req) {
                return new CourseDetailResponse(
                                1L,
                                Long.parseLong(USER_ID),
                                req.title(),
                                req.description(),
                                req.price(),
                                req.capacity(),
                                req.startDate(),
                                req.endDate(),
                                CourseStatus.DRAFT,
                                0L,
                                OffsetDateTime.now(),
                                OffsetDateTime.now());
        }

        // =========================
        // POST /classes
        // =========================
        @Nested
        @DisplayName("POST /classes - 코스 등록")
        class CreateCourse {

                @Test
                @DisplayName("정상 요청 시 201 Created와 Location 헤더 및 응답 본문 반환")
                void createCourse_Success() throws Exception {
                        CreateCourseRequest request = buildCreateCourseRequest();
                        CourseDetailResponse response = buildCreateCourseResponse(request);

                        when(courseService.createCourse(any(RequestUser.class), any(CreateCourseRequest.class)))
                                        .thenReturn(response);

                        mockMvc.perform(postWithUserHeaders("/classes", request))
                                        .andExpect(status().isCreated())
                                        .andExpect(header().string("Location", "/classes/1"))
                                        .andExpect(jsonPath("$.courseId").value(1))
                                        .andExpect(jsonPath("$.title").value("Math 101"))
                                        .andExpect(jsonPath("$.creatorId").value(123))
                                        .andExpect(jsonPath("$.status").value("DRAFT"));

                        ArgumentCaptor<RequestUser> userCaptor = ArgumentCaptor.forClass(RequestUser.class);
                        ArgumentCaptor<CreateCourseRequest> requestCaptor = ArgumentCaptor
                                        .forClass(CreateCourseRequest.class);

                        verify(courseService).createCourse(userCaptor.capture(), requestCaptor.capture());

                        assertThat(userCaptor.getValue().userId()).isEqualTo(123L);
                        assertThat(userCaptor.getValue().role().name()).isEqualTo("CREATOR");
                        assertThat(requestCaptor.getValue().title()).isEqualTo("Math 101");
                }

                @Test
                @DisplayName("사용자 헤더 누락 시 401 Unauthorized 반환 및 서비스 미호출")
                void createCourse_missingHeaders() throws Exception {
                        mockMvc.perform(post("/classes")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(buildCreateCourseRequest())))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                                        .andExpect(jsonPath("$.message").exists())
                                        .andExpect(jsonPath("$.path").value("/classes"));

                        verify(courseService, never()).createCourse(any(), any());
                }

                @Test
                @DisplayName("유효성 검증 실패(제목 공백) 시 400 Bad Request 반환")
                void createCourse_validationFail() throws Exception {
                        CreateCourseRequest invalid = new CreateCourseRequest(
                                        " ",
                                        "Basic math course",
                                        new BigDecimal("50.00"),
                                        5,
                                        START_DATE,
                                        END_DATE);

                        mockMvc.perform(postWithUserHeaders("/classes", invalid))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").exists())
                                        .andExpect(jsonPath("$.path").value("/classes"));

                        verify(courseService, never()).createCourse(any(), any());
                }

                @Test
                @DisplayName("비즈니스 관련 오류 발생 시 적절한 상태 코드와 에러 응답 반환")
                void createCourse_businessException() throws Exception {
                        when(courseService.createCourse(any(), any()))
                                        .thenThrow(new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT, "중복된 코스"));

                        mockMvc.perform(postWithUserHeaders("/classes", buildCreateCourseRequest()))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_ENROLLMENT.name()))
                                        .andExpect(jsonPath("$.message").value("중복된 코스"))
                                        .andExpect(jsonPath("$.path").value("/classes"));

                        verify(courseService).createCourse(any(), any());
                }

                @Test
                @DisplayName("시작일이 종료일보다 이후인 경우 400 Bad Request 반환")
                void createCourse_invalidDateRange() throws Exception {
                        CreateCourseRequest request = new CreateCourseRequest(
                                        "Math 101",
                                        "Basic math course",
                                        new BigDecimal("50.00"),
                                        5,
                                        END_DATE,
                                        START_DATE);

                        mockMvc.perform(postWithUserHeaders("/classes", request))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").value(containsString("startDate")));

                        verify(courseService, never()).createCourse(any(), any());
                }

                @Test
                @DisplayName("가격이 음수인 경우 400 Bad Request 반환")
                void createCourse_negativePrice() throws Exception {
                        CreateCourseRequest request = new CreateCourseRequest(
                                        "Math 101",
                                        "Basic math course",
                                        new BigDecimal("-10.00"),
                                        5,
                                        START_DATE,
                                        END_DATE);

                        mockMvc.perform(postWithUserHeaders("/classes", request))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.message").exists());

                        verify(courseService, never()).createCourse(any(), any());
                }
        }

        // =========================
        // GET /classes
        // =========================
        @Nested
        @DisplayName("GET /classes - 코스 목록 조회")
        class GetCourses {

                @Test
                @DisplayName("상태 필터 없이 전체 목록 조회 시 200 OK와 배열 반환")
                void getCourses_noFilter_returnsList() throws Exception {
                        CourseSummaryResponse summary = new CourseSummaryResponse(
                                        1L, "Math 101", "Basic math course",
                                        new BigDecimal("50.00"), 5, START_DATE, END_DATE, CourseStatus.DRAFT);
                        when(courseService.getCourses(null)).thenReturn(List.of(summary));

                        mockMvc.perform(get("/classes"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$[0].courseId").value(1))
                                        .andExpect(jsonPath("$[0].title").value("Math 101"));

                        verify(courseService).getCourses(null);
                }

                @Test
                @DisplayName("status 파라미터로 필터링 시 해당 상태 목록 반환")
                void getCourses_withStatusFilter_returnsList() throws Exception {
                        CourseSummaryResponse summary = new CourseSummaryResponse(
                                        1L, "Math 101", "Basic math course",
                                        new BigDecimal("50.00"), 5, START_DATE, END_DATE, CourseStatus.DRAFT);

                        when(courseService.getCourses(CourseStatus.DRAFT)).thenReturn(List.of(summary));

                        mockMvc.perform(get("/classes").param("status", "DRAFT"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$[0].status").value("DRAFT"));

                        verify(courseService).getCourses(CourseStatus.DRAFT);
                }

                @Test
                @DisplayName("코스가 없는 경우 빈 배열 반환")
                void getCourses_emptyList_returnsEmptyArray() throws Exception {
                        when(courseService.getCourses(null)).thenReturn(List.of());

                        mockMvc.perform(get("/classes"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$").isArray())
                                        .andExpect(jsonPath("$").isEmpty());

                        verify(courseService).getCourses(null);
                }
        }

        // =========================
        // GET /classes/{id}
        // =========================
        @Nested
        @DisplayName("GET /classes/{id} - 코스 조회")
        class GetCourse {

                @Test
                @DisplayName("존재하는 코스 조회 시 200 OK와 응답 본문 반환")
                void getCourse_success() throws Exception {
                        CourseDetailResponse response = buildCreateCourseResponse(buildCreateCourseRequest());

                        when(courseService.getCourse(1L)).thenReturn(response);

                        mockMvc.perform(get("/classes/1"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.courseId").value(1))
                                        .andExpect(jsonPath("$.title").value("Math 101"));

                        verify(courseService).getCourse(1L);
                }

                @Test
                @DisplayName("존재하지 않는 코스 조회 시 404 Not Found 반환")
                void getCourse_notFound() throws Exception {
                        when(courseService.getCourse(1L))
                                        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "코스를 찾을 수 없습니다"));

                        mockMvc.perform(get("/classes/1"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()))
                                        .andExpect(jsonPath("$.message").value("코스를 찾을 수 없습니다"))
                                        .andExpect(jsonPath("$.path").value("/classes/1"));

                        verify(courseService).getCourse(1L);
                }
        }

        // =========================
        // PATCH /classes/{id}/status
        // =========================
        @Nested
        @DisplayName("PATCH /classes/{id}/status - 코스 상태 변경")
        class UpdateCourseStatus {

                private CourseDetailResponse buildUpdatedResponse(CourseStatus status) {
                        return new CourseDetailResponse(
                                        1L,
                                        Long.parseLong(USER_ID),
                                        "Math 101",
                                        "Basic math course",
                                        new BigDecimal("50.00"),
                                        5,
                                        START_DATE,
                                        END_DATE,
                                        status,
                                        0L,
                                        OffsetDateTime.now(),
                                        OffsetDateTime.now());
                }

                @Test
                @DisplayName("유효한 상태 전환 시 200 OK와 응답 반환")
                void updateCourseStatus_success() throws Exception {
                        UpdateCourseStatusRequest request = new UpdateCourseStatusRequest(CourseStatus.OPEN);
                        CourseDetailResponse response = buildUpdatedResponse(CourseStatus.OPEN);

                        when(courseService.updateCourseStatus(any(RequestUser.class), eq(1L),
                                        any(UpdateCourseStatusRequest.class)))
                                        .thenReturn(response);

                        mockMvc.perform(patchWithUserHeaders("/classes/1/status", request))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.courseId").value(1))
                                        .andExpect(jsonPath("$.status").value("OPEN"));

                        verify(courseService).updateCourseStatus(any(), eq(1L), any());
                }

                @Test
                @DisplayName("사용자 헤더 누락 시 401 Unauthorized 반환")
                void updateCourseStatus_missingHeaders_returns401() throws Exception {
                        mockMvc.perform(patch("/classes/1/status")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(
                                                        new UpdateCourseStatusRequest(CourseStatus.OPEN))))
                                        .andExpect(status().isUnauthorized())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()));

                        verify(courseService, never()).updateCourseStatus(any(), any(), any());
                }

                @Test
                @DisplayName("존재하지 않는 코스 상태 변경 시 404 반환")
                void updateCourseStatus_notFound_returns404() throws Exception {
                        when(courseService.updateCourseStatus(any(), eq(99L), any()))
                                        .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "코스를 찾을 수 없습니다"));

                        mockMvc.perform(patchWithUserHeaders("/classes/99/status",
                                        new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.name()));

                        verify(courseService).updateCourseStatus(any(), eq(99L), any());
                }

                @Test
                @DisplayName("유효하지 않은 상태 전환 시 409 Conflict 반환")
                void updateCourseStatus_invalidTransition_returns409() throws Exception {
                        when(courseService.updateCourseStatus(any(), eq(1L), any()))
                                        .thenThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                                                        "잘못된 상태 전환입니다"));

                        mockMvc.perform(patchWithUserHeaders("/classes/1/status",
                                        new UpdateCourseStatusRequest(CourseStatus.CLOSED)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_STATE_TRANSITION.name()));

                        verify(courseService).updateCourseStatus(any(), eq(1L), any());
                }

                @Test
                @DisplayName("CREATOR 권한 없을 시 403 Forbidden 반환")
                void updateCourseStatus_forbidden_returns403() throws Exception {
                        when(courseService.updateCourseStatus(any(), eq(1L), any()))
                                        .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "권한이 없습니다"));

                        mockMvc.perform(patchWithUserHeaders("/classes/1/status",
                                        new UpdateCourseStatusRequest(CourseStatus.OPEN)))
                                        .andExpect(status().isForbidden())
                                        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.name()));

                        verify(courseService).updateCourseStatus(any(), eq(1L), any());
                }

                @Test
                @DisplayName("status 필드 누락 시 400 Bad Request 반환")
                void updateCourseStatus_missingStatus_returns400() throws Exception {
                        mockMvc.perform(patch("/classes/1/status")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}")
                                        .header("X-User-Id", USER_ID)
                                        .header("X-User-Role", USER_ROLE))
                                        .andExpect(status().isBadRequest());

                        verify(courseService, never()).updateCourseStatus(any(), any(), any());
                }
        }

}
