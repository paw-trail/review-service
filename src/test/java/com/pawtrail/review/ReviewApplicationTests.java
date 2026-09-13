package com.pawtrail.review;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewLike;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.infrastructure.persistence.jpa.PlaceReviewJpaRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewLikeJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReviewApplicationTests {

    // @ServiceConnection 이 컨테이너의 주소와 계정을 DataSource 에 자동으로 넣어 줌
    // 따라서 테스트 설정 파일에 spring.datasource 를 적지 않음
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlaceReviewJpaRepository placeReviewJpaRepository;

    @Autowired
    private ReviewLikeJpaRepository reviewLikeJpaRepository;

    @MockitoBean
    private UserProvider userProvider;

    @BeforeEach
    void cleanDatabase() {
        reviewLikeJpaRepository.deleteAll();
        placeReviewJpaRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void returnsSeededReviewFromApi() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            placeId,
            authorId,
            petId,
            LocalDate.of(2026, 9, 10),
            (short) 5,
            (short) 4,
            (short) 5,
            (short) 4,
            "산책로가 넓고 반려견과 함께 쉬기 좋았어요.",
            List.of("https://example.com/review-photo.jpg"),
            List.of("산책", "주차가능"),
            "골든리트리버",
            new BigDecimal("28.5"),
            "LARGE"
        ));
        reviewLikeJpaRepository.saveAndFlush(ReviewLike.create(review.getId(), viewerId));

        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of(
            authorId,
            new UserSummary(authorId, "테스트 작성자", "https://example.com/profile.jpg")
        ));

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("page", "0")
                .queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].reviewId").value(review.getId().toString()))
            .andExpect(jsonPath("$.data.content[0].rating").value(5))
            .andExpect(jsonPath("$.data.content[0].content")
                .value("산책로가 넓고 반려견과 함께 쉬기 좋았어요."))
            .andExpect(jsonPath("$.data.content[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.content[0].likedByMe").value(true))
            .andExpect(jsonPath("$.data.content[0].isMine").value(false))
            .andExpect(jsonPath("$.data.content[0].canDelete").value(false))
            .andExpect(jsonPath("$.data.content[0].author.nickname").value("테스트 작성자"))
            .andExpect(jsonPath("$.data.content[0].petSummary.breedName").value("골든리트리버"))
            .andExpect(jsonPath("$.data.page.number").value(0))
            .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

}
