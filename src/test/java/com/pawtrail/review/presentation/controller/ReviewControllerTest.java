package com.pawtrail.review.presentation.controller;

import com.pawtrail.review.application.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerTest {

    private ReviewService reviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService)).build();
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
}
