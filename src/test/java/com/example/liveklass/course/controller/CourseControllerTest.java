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

import com.example.liveklass.common.config.RequestUser;
import com.example.liveklass.common.error.BusinessException;
import com.example.liveklass.common.error.ErrorCode;
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
            ArgumentCaptor<CreateCourseRequest> requestCaptor = ArgumentCaptor.forClass(CreateCourseRequest.class);

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

}
