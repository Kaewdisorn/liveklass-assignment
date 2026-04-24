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
import com.example.liveklass.course.dto.CourseDetailResponse;
import com.example.liveklass.course.dto.CreateCourseRequest;
import com.example.liveklass.course.enums.CourseStatus;
import com.example.liveklass.course.service.CourseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
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
    }

}
