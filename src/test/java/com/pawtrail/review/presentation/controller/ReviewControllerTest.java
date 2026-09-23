package com.pawtrail.review.presentation.controller;

import com.pawtrail.common.enums.Role;
import com.pawtrail.common.exception.handler.GlobalExceptionHandler;
import com.pawtrail.common.security.principal.CustomUserPrincipal;
import com.pawtrail.review.application.dto.output.UploadUrlOutput;
import com.pawtrail.review.application.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerTest {

    private ReviewService reviewService;
    private MockMvc mockMvc;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        reviewService = mock(ReviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(currentUserResolver())
            .build();
    }

    @Test
    void returnsReviewTags() throws Exception {
        List<String> tags = List.of("음수대 제공완료", "주차 편함");
        when(reviewService.findTags()).thenReturn(tags);

        mockMvc.perform(get("/api/v1/reviews/tags"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0]").value("음수대 제공완료"))
            .andExpect(jsonPath("$.data[1]").value("주차 편함"));
    }

    @ParameterizedTest
    @CsvSource({
        "review.jpg,image/jpeg",
        "review.png,image/png"
    })
    void returnsUploadUrlForSupportedImage(String fileName, String contentType) throws Exception {
        UploadUrlOutput output = new UploadUrlOutput(
            "https://upload.example.com",
            "https://download.example.com",
            900
        );
        when(reviewService.createUploadUrl(accountId, fileName, contentType, 1024L)).thenReturn(output);

        mockMvc.perform(post("/api/v1/reviews/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fileName":"%s","contentType":"%s","contentLength":1024}
                    """.formatted(fileName, contentType)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.uploadUrl").value(output.uploadUrl()))
            .andExpect(jsonPath("$.data.fileUrl").value(output.fileUrl()))
            .andExpect(jsonPath("$.data.expiresIn").value(output.expiresIn()));

        verify(reviewService).createUploadUrl(accountId, fileName, contentType, 1024L);
    }

    @Test
    void rejectsUnsupportedImageContentType() throws Exception {
        mockMvc.perform(post("/api/v1/reviews/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fileName":"review.gif","contentType":"image/gif","contentLength":1024}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(reviewService);
    }

    @Test
    void rejectsBlankImageFileName() throws Exception {
        mockMvc.perform(post("/api/v1/reviews/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fileName":" ","contentType":"image/jpeg","contentLength":1024}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(reviewService);
    }

    @Test
    void rejectsImageFileNameOverTwoHundredFiftyFiveCharacters() throws Exception {
        String longFileName = "a".repeat(252) + ".png";

        mockMvc.perform(post("/api/v1/reviews/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fileName":"%s","contentType":"image/png","contentLength":1024}
                    """.formatted(longFileName)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(reviewService);
    }

    @Test
    void rejectsMissingImageContentLength() throws Exception {
        mockMvc.perform(post("/api/v1/reviews/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"fileName":"review.jpg","contentType":"image/jpeg"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(reviewService);
    }

    private HandlerMethodArgumentResolver currentUserResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType() == CustomUserPrincipal.class;
            }

            @Override
            public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
            ) {
                return new CustomUserPrincipal(accountId, Role.USER);
            }
        };
    }
}
