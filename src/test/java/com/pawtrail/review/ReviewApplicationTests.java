package com.pawtrail.review;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewLike;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.infrastructure.config.ReviewProperties;
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
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
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
    private PlaceReviewRepository placeReviewRepository;

    @Autowired
    private ReviewLikeJpaRepository reviewLikeJpaRepository;

    @Autowired
    private ReviewProperties reviewProperties;

    @MockitoBean
    private UserProvider userProvider;

    @MockitoBean
    private PlaceProvider placeProvider;

    // 사진 주소 서명은 목으로 세웁니다.
    // 실제 서명에는 AWS 자격 증명이 필요한데 테스트 JVM 에는 없고,
    // 여기서 보려는 것은 목록과 요약이지 서명 자체가 아닙니다.
    @MockitoBean
    private StorageProvider storageProvider;

    @BeforeEach
    void cleanDatabase() {
        reviewLikeJpaRepository.deleteAll();
        placeReviewJpaRepository.deleteAll();

        when(storageProvider.presignDownload(anyString()))
            .thenAnswer(invocation -> "https://signed.example.com/" + invocation.getArgument(0));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void returnsEmptyPageWhenCurrentUserHasNoReviews() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/me")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content.length()").value(0))
            .andExpect(jsonPath("$.data.page.number").value(0))
            .andExpect(jsonPath("$.data.page.size").value(20))
            .andExpect(jsonPath("$.data.page.totalElements").value(0))
            .andExpect(jsonPath("$.data.page.totalPages").value(0));

        verifyNoInteractions(placeProvider);
    }

    @Test
    void returnsMineOneHundredAtATime() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        List<PlaceReview> reviews = IntStream.rangeClosed(1, 201)
            .mapToObj(number -> PlaceReview.create(
                placeId,
                accountId,
                petId,
                LocalDate.of(2026, 9, 10),
                (short) 5,
                (short) 4,
                (short) 5,
                (short) 4,
                "내 리뷰 페이징 테스트 " + number,
                List.of(),
                List.of(),
                "골든리트리버",
                new BigDecimal("28.5"),
                "LARGE"
            ))
            .toList();
        placeReviewJpaRepository.saveAllAndFlush(reviews);
        when(placeProvider.getPlaces(anyCollection())).thenReturn(Map.of(
            placeId,
            new PlaceSummary(placeId, "테스트 장소")
        ));

        mockMvc.perform(get("/api/v1/reviews/me")
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .queryParam("page", "0")
                .queryParam("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(100))
            .andExpect(jsonPath("$.data.content[0].placeName").value("테스트 장소"))
            .andExpect(jsonPath("$.data.page.number").value(0))
            .andExpect(jsonPath("$.data.page.size").value(100))
            .andExpect(jsonPath("$.data.page.totalElements").value(201))
            .andExpect(jsonPath("$.data.page.totalPages").value(3));

        mockMvc.perform(get("/api/v1/reviews/me")
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .queryParam("page", "2")
                .queryParam("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.page.number").value(2))
            .andExpect(jsonPath("$.data.page.totalElements").value(201))
            .andExpect(jsonPath("$.data.page.totalPages").value(3));
    }

    @Test
    void usesReviewIdAsFinalRecentSortCondition() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();
        LocalDate visitedAt = LocalDate.of(2026, 9, 10);

        List<PlaceReview> reviews = placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, accountId, petId, visitedAt,
                (short) 5, (short) 4, (short) 5, (short) 4,
                "첫 번째 리뷰", List.of(), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            ),
            PlaceReview.create(
                placeId, accountId, petId, visitedAt,
                (short) 5, (short) 4, (short) 5, (short) 4,
                "두 번째 리뷰", List.of(), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            )
        ));
        UUID expectedFirstId = reviews.stream()
            .map(PlaceReview::getId)
            .max(UUID::compareTo)
            .orElseThrow();
        when(placeProvider.getPlaces(anyCollection())).thenReturn(Map.of(
            placeId,
            new PlaceSummary(placeId, "테스트 장소")
        ));

        mockMvc.perform(get("/api/v1/reviews/me")
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .queryParam("sort", "recent"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].reviewId")
                .value(expectedFirstId.toString()));
    }

    @Test
    void rejectsMyReviewPageSizeOverOneHundred() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/me")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .queryParam("size", "101"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConfiguredReviewTags() throws Exception {
        List<String> expectedTags = List.of(
            "음수대 제공완료",
            "반려견 방석 완비",
            "반려견 놀이터 추천",
            "야외석 넓음",
            "주차 편함"
        );

        assertThat(reviewProperties.tags()).containsExactlyElementsOf(expectedTags);

        mockMvc.perform(get("/api/v1/reviews/tags")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.length()").value(expectedTags.size()))
            .andExpect(jsonPath("$.data[0]").value(expectedTags.get(0)))
            .andExpect(jsonPath("$.data[1]").value(expectedTags.get(1)))
            .andExpect(jsonPath("$.data[2]").value(expectedTags.get(2)))
            .andExpect(jsonPath("$.data[3]").value(expectedTags.get(3)))
            .andExpect(jsonPath("$.data[4]").value(expectedTags.get(4)));
    }

    @Test
    void rejectsReviewPageSizeOverOneHundred() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .queryParam("page", "0")
                .queryParam("size", "101"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNextHundredReviewsForLoadMore() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        List<PlaceReview> reviews = IntStream.rangeClosed(1, 101)
            .mapToObj(number -> PlaceReview.create(
                placeId,
                authorId,
                petId,
                LocalDate.of(2026, 9, 10),
                (short) 5,
                (short) 4,
                (short) 5,
                (short) 4,
                "페이지네이션 테스트 리뷰 " + number,
                List.of(),
                List.of(),
                "골든리트리버",
                new BigDecimal("28.5"),
                "LARGE"
            ))
            .toList();
        placeReviewJpaRepository.saveAllAndFlush(reviews);
        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("page", "0")
                .queryParam("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(100))
            .andExpect(jsonPath("$.data.page.number").value(0))
            .andExpect(jsonPath("$.data.page.totalElements").value(101))
            .andExpect(jsonPath("$.data.page.totalPages").value(2));

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("page", "1")
                .queryParam("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.page.number").value(1))
            .andExpect(jsonPath("$.data.page.totalElements").value(101))
            .andExpect(jsonPath("$.data.page.totalPages").value(2));
    }

    @Test
    void sortsPlaceReviewsByRatingAndFiltersByPhoto() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, authorId, petId, LocalDate.of(2026, 9, 10),
                (short) 5, (short) 5, (short) 5, (short) 5,
                "사진 있는 후기", List.of("reviews/" + authorId + "/a.jpg"), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            ),
            PlaceReview.create(
                placeId, authorId, petId, LocalDate.of(2026, 9, 11),
                (short) 3, (short) 3, (short) 3, (short) 3,
                "사진 없는 후기", List.of(), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            ),
            PlaceReview.create(
                placeId, authorId, petId, LocalDate.of(2026, 9, 12),
                (short) 1, (short) 1, (short) 1, (short) 1,
                "사진 있는 낮은 점수 후기", List.of("reviews/" + authorId + "/b.jpg"), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            )
        ));
        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of());

        // 별점 낮은 순
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("sort", "rating_asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(3))
            .andExpect(jsonPath("$.data.content[0].rating").value(1))
            .andExpect(jsonPath("$.data.content[2].rating").value(5));

        // 별점 높은 순
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("sort", "rating_desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].rating").value(5));

        // 사진만 보기를 켜면 목록과 건수는 줄지만 요약은 장소 전체 기준 그대로임
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("photoOnly", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(2))
            .andExpect(jsonPath("$.data.page.totalElements").value(2))
            .andExpect(jsonPath("$.data.summary.reviewCount").value(3))
            .andExpect(jsonPath("$.data.summary.ratingAvg").value(3.0));
    }

    @Test
    void returnsPlaceSummaryWithFourAveragesAndCount() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, authorId, petId, LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 3, (short) 2,
                "첫 번째 후기", List.of(), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            ),
            PlaceReview.create(
                placeId, authorId, petId, LocalDate.of(2026, 9, 11),
                (short) 4, (short) 3, (short) 2, (short) 1,
                "두 번째 후기", List.of(), List.of(),
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            )
        ));
        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page.size").value(20))
            .andExpect(jsonPath("$.data.summary.ratingAvg").value(4.5))
            .andExpect(jsonPath("$.data.summary.facilityAvg").value(3.5))
            .andExpect(jsonPath("$.data.summary.ruleAvg").value(2.5))
            .andExpect(jsonPath("$.data.summary.moodAvg").value(1.5))
            .andExpect(jsonPath("$.data.summary.reviewCount").value(2));
    }

    @Test
    void returnsZeroSummaryForPlaceWithoutReviews() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(0))
            .andExpect(jsonPath("$.data.summary.reviewCount").value(0))
            .andExpect(jsonPath("$.data.summary.ratingAvg").value(0.0));
    }

    @Test
    void rejectsUnknownPlaceReviewSort() throws Exception {
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .queryParam("sort", "oldest"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
        ReviewLike reviewLike = reviewLikeJpaRepository.saveAndFlush(
            ReviewLike.create(review.getId(), viewerId)
        );

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

        reviewLikeJpaRepository.deleteById(reviewLike.getId());
        reviewLikeJpaRepository.flush();

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER")
                .queryParam("page", "0")
                .queryParam("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].reviewId").value(review.getId().toString()))
            .andExpect(jsonPath("$.data.content[0].likeCount").value(0))
            .andExpect(jsonPath("$.data.content[0].likedByMe").value(false));
    }

    @Test
    void deletingReviewCascadesReviewLikes() {
        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 9, 10),
            (short) 5,
            (short) 4,
            (short) 5,
            (short) 4,
            "삭제 cascade 검증용 리뷰입니다.",
            List.of(),
            List.of(),
            "골든리트리버",
            new BigDecimal("28.5"),
            "LARGE"
        ));
        ReviewLike reviewLike = reviewLikeJpaRepository.saveAndFlush(
            ReviewLike.create(review.getId(), UUID.randomUUID())
        );

        placeReviewRepository.hardDeleteAll(List.of(review));
        placeReviewJpaRepository.flush();

        assertThat(placeReviewJpaRepository.existsById(review.getId())).isFalse();
        assertThat(reviewLikeJpaRepository.existsById(reviewLike.getId())).isFalse();
    }

}
