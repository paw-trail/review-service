package com.pawtrail.review;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewLike;
import com.pawtrail.review.domain.model.ReviewPet;
import com.pawtrail.review.domain.provider.PetProvider;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.StorageProvider;
import com.pawtrail.review.domain.provider.UserProvider;
import com.pawtrail.review.domain.provider.dto.PetSnapshot;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.domain.provider.dto.UserSummary;
import com.pawtrail.review.application.service.AccountWithdrawnService;
import com.pawtrail.review.application.service.ReviewService;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.infrastructure.config.ReviewProperties;
import com.pawtrail.review.infrastructure.persistence.jpa.PlaceReviewJpaRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewLikeJpaRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewPetJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private AccountWithdrawnService accountWithdrawnService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ReviewLikeJpaRepository reviewLikeJpaRepository;

    @Autowired
    private ReviewPetJpaRepository reviewPetJpaRepository;

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

    @MockitoBean
    private PetProvider petProvider;

    // 자식 표를 먼저 비웁니다.
    // 외래키가 ON DELETE CASCADE 라 부모만 지워도 따라 지워지지만,
    // 지우는 차례를 눈에 보이게 두는 편이 나중에 표가 늘어도 헷갈리지 않습니다.
    @BeforeEach
    void cleanDatabase() {
        reviewLikeJpaRepository.deleteAll();
        reviewPetJpaRepository.deleteAll();
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

        List<PlaceReview> reviews = IntStream.rangeClosed(1, 201)
            .mapToObj(number -> PlaceReview.create(
                placeId,
                accountId,
                LocalDate.of(2026, 9, 10),
                (short) 5,
                (short) 4,
                (short) 5,
                (short) 4,
                "내 리뷰 페이징 테스트 " + number,
                List.of(),
                List.of()
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
        LocalDate visitedAt = LocalDate.of(2026, 9, 10);

        List<PlaceReview> reviews = placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, accountId, visitedAt,
                (short) 5, (short) 4, (short) 5, (short) 4,
                "첫 번째 리뷰", List.of(), List.of()
            ),
            PlaceReview.create(
                placeId, accountId, visitedAt,
                (short) 5, (short) 4, (short) 5, (short) 4,
                "두 번째 리뷰", List.of(), List.of()
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

        List<PlaceReview> reviews = IntStream.rangeClosed(1, 101)
            .mapToObj(number -> PlaceReview.create(
                placeId,
                authorId,
                LocalDate.of(2026, 9, 10),
                (short) 5,
                (short) 4,
                (short) 5,
                (short) 4,
                "페이지네이션 테스트 리뷰 " + number,
                List.of(),
                List.of()
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

        placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, authorId, LocalDate.of(2026, 9, 10),
                (short) 5, (short) 5, (short) 5, (short) 5,
                "사진 있는 후기", List.of("reviews/" + authorId + "/a.jpg"), List.of()
            ),
            PlaceReview.create(
                placeId, authorId, LocalDate.of(2026, 9, 11),
                (short) 3, (short) 3, (short) 3, (short) 3,
                "사진 없는 후기", List.of(), List.of()
            ),
            PlaceReview.create(
                placeId, authorId, LocalDate.of(2026, 9, 12),
                (short) 1, (short) 1, (short) 1, (short) 1,
                "사진 있는 낮은 점수 후기", List.of("reviews/" + authorId + "/b.jpg"), List.of()
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

        placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, authorId, LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 3, (short) 2,
                "첫 번째 후기", List.of(), List.of()
            ),
            PlaceReview.create(
                placeId, authorId, LocalDate.of(2026, 9, 11),
                (short) 4, (short) 3, (short) 2, (short) 1,
                "두 번째 후기", List.of(), List.of()
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

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            placeId,
            authorId,
            LocalDate.of(2026, 9, 10),
            (short) 5,
            (short) 4,
            (short) 5,
            (short) 4,
            "산책로가 넓고 반려견과 함께 쉬기 좋았어요.",
            List.of("https://example.com/review-photo.jpg"),
            List.of("산책", "주차가능")
        ));
        reviewPetJpaRepository.saveAndFlush(ReviewPet.create(
            review.getId(), UUID.randomUUID(), (short) 0,
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
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
            .andExpect(jsonPath("$.data.content[0].pets.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].pets[0].breedName").value("골든리트리버"))
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
    void deletingReviewCascadesReviewLikesAndPets() {
        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            LocalDate.of(2026, 9, 10),
            (short) 5,
            (short) 4,
            (short) 5,
            (short) 4,
            "삭제 cascade 검증용 리뷰입니다.",
            List.of(),
            List.of()
        ));
        ReviewLike reviewLike = reviewLikeJpaRepository.saveAndFlush(
            ReviewLike.create(review.getId(), UUID.randomUUID())
        );
        reviewPetJpaRepository.saveAndFlush(ReviewPet.create(
            review.getId(), UUID.randomUUID(), (short) 0,
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        ));

        placeReviewRepository.hardDeleteAll(List.of(review));
        placeReviewJpaRepository.flush();

        assertThat(placeReviewJpaRepository.existsById(review.getId())).isFalse();
        assertThat(reviewLikeJpaRepository.existsById(reviewLike.getId())).isFalse();
        assertThat(reviewPetJpaRepository.findByReviewIdOrderBySortOrderAsc(review.getId()))
            .isEmpty();
    }

    // 고른 순서가 그대로 카드에 실립니다.
    @Test
    void createsReviewWithEveryPetAndReturnsThemInOrder() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID firstPet = UUID.randomUUID();
        UUID secondPet = UUID.randomUUID();
        String photoUrl = "https://test-review-images.s3.ap-northeast-2.amazonaws.com/reviews/"
            + accountId + "/photo.jpg";

        when(petProvider.findOwnedPets(accountId, List.of(firstPet, secondPet)))
            .thenReturn(Map.of(
                firstPet, new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE"),
                secondPet, new PetSnapshot("말티즈", new BigDecimal("3.2"), "SMALL")
            ));
        when(storageProvider.extractOwnedKey(photoUrl, accountId))
            .thenReturn(Optional.of("reviews/" + accountId + "/photo.jpg"));
        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petIds":["%s","%s"],"visitedAt":"2026-09-10","rating":5,
                     "facilityScore":4,"ruleScore":5,"moodScore":4,
                     "content":"좋았어요","photos":["%s"],"tags":["주차 편함"]}
                    """.formatted(firstPet, secondPet, photoUrl)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.data.reviewId").isNotEmpty());

        assertThat(placeReviewJpaRepository.findAll()).hasSize(1);

        UUID reviewId = placeReviewJpaRepository.findAll().getFirst().getId();
        List<ReviewPet> pets = reviewPetJpaRepository
            .findByReviewIdOrderBySortOrderAsc(reviewId);

        assertThat(pets).hasSize(2);
        assertThat(pets.get(0).getPetId()).isEqualTo(firstPet);
        assertThat(pets.get(0).getSortOrder()).isEqualTo((short) 0);
        assertThat(pets.get(0).getBreedName()).isEqualTo("골든리트리버");
        assertThat(pets.get(1).getPetId()).isEqualTo(secondPet);
        assertThat(pets.get(1).getSortOrder()).isEqualTo((short) 1);
        assertThat(pets.get(1).getBreedName()).isEqualTo("말티즈");

        // 목록에는 저장된 키가 아니라 서명된 주소로 나가고, 아이는 고른 차례로 실립니다.
        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].photos[0]")
                .value("https://signed.example.com/reviews/" + accountId + "/photo.jpg"))
            .andExpect(jsonPath("$.data.content[0].isMine").value(true))
            .andExpect(jsonPath("$.data.content[0].pets.length()").value(2))
            .andExpect(jsonPath("$.data.content[0].pets[0].breedName").value("골든리트리버"))
            .andExpect(jsonPath("$.data.content[0].pets[0].weightKg").value(28.5))
            .andExpect(jsonPath("$.data.content[0].pets[1].breedName").value("말티즈"));
    }

    // 한 마리라도 남의 아이면 후기 자체가 저장되지 않습니다.
    @Test
    void rejectsCreateWhenOneOfThePetsIsNotOwned() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();

        when(petProvider.findOwnedPets(accountId, List.of(mine, someoneElses)))
            .thenReturn(Map.of(
                mine, new PetSnapshot("골든리트리버", new BigDecimal("28.5"), "LARGE")
            ));

        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petIds":["%s","%s"],"visitedAt":"2026-09-10","rating":5,
                     "facilityScore":4,"ruleScore":5,"moodScore":4,
                     "content":"좋았어요","photos":[],"tags":[]}
                    """.formatted(mine, someoneElses)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PET_NOT_OWNED"));

        assertThat(placeReviewJpaRepository.findAll()).isEmpty();
        assertThat(reviewPetJpaRepository.findAll()).isEmpty();
    }

    // 아무도 고르지 않은 후기는 받지 않습니다.
    @Test
    void rejectsCreateWithoutAnyPet() throws Exception {
        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petIds":[],"visitedAt":"2026-09-10","rating":5,
                     "facilityScore":4,"ruleScore":5,"moodScore":4,
                     "content":"좋았어요","photos":[],"tags":[]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(petProvider);
    }

    @Test
    void rejectsMoreThanFivePets() throws Exception {
        String petIds = IntStream.rangeClosed(1, 6)
            .mapToObj(number -> "\"" + UUID.randomUUID() + "\"")
            .reduce((first, second) -> first + "," + second)
            .orElseThrow();

        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petIds":[%s],"visitedAt":"2026-09-10","rating":5,
                     "facilityScore":4,"ruleScore":5,"moodScore":4,
                     "content":"좋았어요","photos":[],"tags":[]}
                    """.formatted(petIds)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(petProvider);
    }

    @Test
    void rejectsFutureVisitedAt() throws Exception {
        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petIds":["%s"],"visitedAt":"%s","rating":5,
                     "facilityScore":4,"ruleScore":5,"moodScore":4,
                     "content":"좋았어요","photos":[],"tags":[]}
                    """.formatted(UUID.randomUUID(), LocalDate.now().plusDays(1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(petProvider);
    }

    @Test
    void rejectsMoreThanFivePhotos() throws Exception {
        UUID accountId = UUID.randomUUID();
        String photoUrl = "https://test-review-images.s3.ap-northeast-2.amazonaws.com/reviews/"
            + accountId + "/photo.jpg";
        String photos = ("\"" + photoUrl + "\",").repeat(6);

        mockMvc.perform(post("/api/v1/places/{placeId}/reviews", UUID.randomUUID())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"petIds":["%s"],"visitedAt":"2026-09-10","rating":5,
                     "facilityScore":4,"ruleScore":5,"moodScore":4,
                     "content":"좋았어요","photos":[%s],"tags":[]}
                    """.formatted(UUID.randomUUID(), photos.substring(0, photos.length() - 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(petProvider);
    }

    @Test
    void updatesOnlyTheFieldsThatWereSent() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            placeId, accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "처음 쓴 내용", List.of(), List.of("주차 편함")
        ));
        reviewPetJpaRepository.saveAndFlush(ReviewPet.create(
            review.getId(), petId, (short) 0,
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        ));

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", review.getId())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rating":2,"content":"다시 가 보니 달랐어요"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"));

        PlaceReview updated = placeReviewJpaRepository.findById(review.getId()).orElseThrow();

        assertThat(updated.getRating()).isEqualTo((short) 2);
        assertThat(updated.getContent()).isEqualTo("다시 가 보니 달랐어요");

        // 안 보낸 칸은 그대로입니다.
        assertThat(updated.getFacilityScore()).isEqualTo((short) 4);
        assertThat(updated.getTags()).containsExactly("주차 편함");
        assertThat(updated.getVisitedAt()).isEqualTo(LocalDate.of(2026, 9, 10));

        // 반려동물은 수정 대상이 아닙니다. 방문 당시의 값이라 그대로 남습니다.
        List<ReviewPet> pets = reviewPetJpaRepository
            .findByReviewIdOrderBySortOrderAsc(review.getId());

        assertThat(pets).hasSize(1);
        assertThat(pets.getFirst().getPetId()).isEqualTo(petId);
        assertThat(pets.getFirst().getBreedName()).isEqualTo("골든리트리버");
    }

    // 요청에 반려동물 칸이 아예 없으므로 보내도 흘려보냅니다.
    @Test
    void ignoresPetIdsSentOnUpdate() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
        ));
        reviewPetJpaRepository.saveAndFlush(ReviewPet.create(
            review.getId(), petId, (short) 0,
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        ));

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", review.getId())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rating":3,"petIds":["%s"]}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isOk());

        List<ReviewPet> pets = reviewPetJpaRepository
            .findByReviewIdOrderBySortOrderAsc(review.getId());

        assertThat(pets).hasSize(1);
        assertThat(pets.getFirst().getPetId()).isEqualTo(petId);
        verifyNoInteractions(petProvider);
    }

    // 빈 배열은 "비움" 입니다. 안 보낸 것과 다릅니다.
    @Test
    void clearsTagsWhenAnEmptyArrayIsSent() throws Exception {
        UUID accountId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of("주차 편함")
        ));

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", review.getId())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tags":[]}
                    """))
            .andExpect(status().isOk());

        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().getTags())
            .isEmpty();
    }

    @Test
    void rejectsUpdatingSomeoneElsesReview() throws Exception {
        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
        ));

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", review.getId())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rating":1}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));
    }

    // 소프트 삭제라 행은 남고, 목록과 좋아요에서만 사라집니다.
    @Test
    void softDeletesReviewAndRemovesItFromTheList() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            placeId, accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
        ));
        reviewPetJpaRepository.saveAndFlush(ReviewPet.create(
            review.getId(), UUID.randomUUID(), (short) 0,
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        ));
        reviewLikeJpaRepository.saveAndFlush(ReviewLike.create(review.getId(), UUID.randomUUID()));
        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of());

        mockMvc.perform(delete("/api/v1/reviews/{reviewId}", review.getId())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk());

        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().isDeleted())
            .isTrue();
        assertThat(reviewLikeJpaRepository.count()).isZero();

        // 행을 지우지 않으므로 자식 행도 그대로입니다.
        assertThat(reviewPetJpaRepository.findByReviewIdOrderBySortOrderAsc(review.getId()))
            .hasSize(1);

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(0))
            .andExpect(jsonPath("$.data.summary.reviewCount").value(0));
    }

    @Test
    void rejectsDeletingReviewThatIsAlreadyGone() throws Exception {
        mockMvc.perform(delete("/api/v1/reviews/{reviewId}", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    // 두 번 눌러도 한 번 누른 것과 같고, 수는 표의 트리거가 맞춥니다.
    @Test
    void likeIsIdempotentAndCountedByTheTrigger() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            placeId, authorId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
        ));
        when(userProvider.getUsers(anyCollection())).thenReturn(Map.of());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/reviews/{reviewId}/like", review.getId())
                    .header("X-User-Id", viewerId)
                    .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        }

        assertThat(reviewLikeJpaRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].likeCount").value(1))
            .andExpect(jsonPath("$.data.content[0].likedByMe").value(true));

        // 취소도 두 번 불러도 같습니다.
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(delete("/api/v1/reviews/{reviewId}/like", review.getId())
                    .header("X-User-Id", viewerId)
                    .header("X-User-Role", "USER"))
                .andExpect(status().isOk());
        }

        assertThat(reviewLikeJpaRepository.count()).isZero();

        mockMvc.perform(get("/api/v1/places/{placeId}/reviews", placeId)
                .header("X-User-Id", viewerId)
                .header("X-User-Role", "USER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].likeCount").value(0))
            .andExpect(jsonPath("$.data.content[0].likedByMe").value(false));
    }

    @Test
    void rejectsLikeOnReviewThatIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/reviews/{reviewId}/like", UUID.randomUUID())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));
    }

    @Test
    void adminCanDeleteSomeoneElsesReview() throws Exception {
        UUID adminId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "신고된 후기", List.of(), List.of()
        ));

        mockMvc.perform(delete("/api/v1/admin/reviews/{reviewId}", review.getId())
                .header("X-User-Id", adminId)
                .header("X-User-Role", "ADMIN"))
            .andExpect(status().isOk());

        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().isDeleted())
            .isTrue();
    }

    // 일반 사용자는 관리자 경로에 닿지 못합니다.
    @Test
    void rejectsAdminDeleteForNormalUser() throws Exception {
        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "신고된 후기", List.of(), List.of()
        ));

        mockMvc.perform(delete("/api/v1/admin/reviews/{reviewId}", review.getId())
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER"))
            .andExpect(status().isForbidden());

        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().isDeleted())
            .isFalse();
    }

    @Test
    void internalCountAndStatsAndPeriodAnswerTheCallers() throws Exception {
        UUID placeId = UUID.randomUUID();
        UUID otherPlaceId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        List<PlaceReview> reviews = placeReviewJpaRepository.saveAllAndFlush(List.of(
            PlaceReview.create(
                placeId, accountId, LocalDate.of(2026, 9, 10),
                (short) 5, (short) 4, (short) 5, (short) 4,
                "첫 번째", List.of(), List.of()
            ),
            PlaceReview.create(
                placeId, accountId, LocalDate.of(2026, 9, 12),
                (short) 4, (short) 4, (short) 4, (short) 4,
                "두 번째", List.of(), List.of()
            )
        ));

        // 하루 요약은 그날 함께 다녀온 아이를 모두 씁니다.
        UUID firstReviewId = reviews.stream()
            .filter(review -> review.getVisitedAt().equals(LocalDate.of(2026, 9, 10)))
            .findFirst()
            .orElseThrow()
            .getId();

        reviewPetJpaRepository.saveAllAndFlush(List.of(
            ReviewPet.create(
                firstReviewId, UUID.randomUUID(), (short) 0,
                "골든리트리버", new BigDecimal("28.5"), "LARGE"
            ),
            ReviewPet.create(
                firstReviewId, UUID.randomUUID(), (short) 1,
                "말티즈", new BigDecimal("3.2"), "SMALL"
            )
        ));

        mockMvc.perform(get("/internal/reviews/count")
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .queryParam("accountId", accountId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.count").value(2));

        // 평점은 계정을 안 보므로 헤더 없이도 답합니다. 후기 없는 장소는 빠집니다.
        mockMvc.perform(get("/internal/reviews/stats")
                .queryParam("placeIds", placeId + "," + otherPlaceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].placeId").value(placeId.toString()))
            .andExpect(jsonPath("$.data[0].ratingAvg").value(4.5))
            .andExpect(jsonPath("$.data[0].reviewCount").value(2));

        // 기간은 방문일 기준이고 양끝을 포함합니다.
        mockMvc.perform(get("/internal/reviews")
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .queryParam("accountId", accountId.toString())
                .queryParam("from", "2026-09-10")
                .queryParam("to", "2026-09-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].content").value("첫 번째"))
            .andExpect(jsonPath("$.data[0].visitedAt").value("2026-09-10"))
            .andExpect(jsonPath("$.data[0].petBreedsAtVisit.length()").value(2))
            .andExpect(jsonPath("$.data[0].petBreedsAtVisit[0]").value("골든리트리버"))
            .andExpect(jsonPath("$.data[0].petBreedsAtVisit[1]").value("말티즈"));
    }

    // 망 안이라고 남의 계정을 물을 수 있는 것은 아닙니다.
    @Test
    void internalRejectsAnotherAccount() throws Exception {
        mockMvc.perform(get("/internal/reviews/count")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Role", "USER")
                .queryParam("accountId", UUID.randomUUID().toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/internal/reviews/count")
                .queryParam("accountId", UUID.randomUUID().toString()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    // 탈퇴는 행을 남기지 않습니다. 좋아요도 양쪽 다 사라집니다.
    @Test
    void withdrawRemovesReviewsAndLikesOnBothSides() {
        UUID accountId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        String key = "reviews/" + accountId + "/photo.jpg";

        PlaceReview mine = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "내 후기", List.of(key), List.of()
        ));
        PlaceReview others = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), otherId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "남의 후기", List.of(), List.of()
        ));
        reviewPetJpaRepository.saveAndFlush(ReviewPet.create(
            mine.getId(), UUID.randomUUID(), (short) 0,
            "골든리트리버", new BigDecimal("28.5"), "LARGE"
        ));

        // 남이 내 후기에 누른 좋아요와, 내가 남의 후기에 누른 좋아요
        reviewLikeJpaRepository.saveAllAndFlush(List.of(
            ReviewLike.create(mine.getId(), otherId),
            ReviewLike.create(others.getId(), accountId)
        ));

        accountWithdrawnService.withdraw(accountId);

        assertThat(placeReviewJpaRepository.findById(mine.getId())).isEmpty();
        assertThat(reviewLikeJpaRepository.count()).isZero();
        verify(storageProvider).delete(key);

        // 행을 지우므로 자식 행도 외래키를 따라 사라집니다.
        assertThat(reviewPetJpaRepository.findByReviewIdOrderBySortOrderAsc(mine.getId()))
            .isEmpty();

        // 남의 후기는 남고 좋아요 수만 줄어듭니다.
        assertThat(placeReviewJpaRepository.findById(others.getId()).orElseThrow().getLikeCount())
            .isZero();
    }

    // 목록이 내보낸 서명된 주소를 그대로 돌려보내도 수정이 됩니다.
    //
    // 사진 두 장 중 한 장만 남기는 경우입니다.
    // 화면이 들고 있는 것은 서명된 주소뿐이라 이 주소로 키를 뽑을 수 있어야 합니다.
    @Test
    void keepsAPhotoWhenTheSignedViewUrlIsSentBack() throws Exception {
        UUID accountId = UUID.randomUUID();
        String keptKey = "reviews/" + accountId + "/kept.jpg";
        String removedKey = "reviews/" + accountId + "/removed.jpg";
        String signedKeptUrl = "https://test-review-images.s3.ap-northeast-2.amazonaws.com/"
            + keptKey + "?X-Amz-Signature=abc123";

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), accountId, LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(keptKey, removedKey), List.of()
        ));
        when(storageProvider.extractOwnedKey(signedKeptUrl, accountId))
            .thenReturn(Optional.of(keptKey));

        mockMvc.perform(patch("/api/v1/reviews/{reviewId}", review.getId())
                .header("X-User-Id", accountId)
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"photos":["%s"]}
                    """.formatted(signedKeptUrl)))
            .andExpect(status().isOk());

        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().getPhotos())
            .containsExactly(keptKey);
    }

    // 같은 사람이 두 번 눌러도 행은 하나입니다.
    //
    // 확인과 저장을 나누지 않고 저장소가 한 번에 처리하므로 기본키 충돌이 나지 않습니다.
    @Test
    void likeInsertsAtMostOneRowPerAccount() {
        UUID accountId = UUID.randomUUID();

        PlaceReview review = placeReviewJpaRepository.saveAndFlush(PlaceReview.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 9, 10),
            (short) 5, (short) 4, (short) 5, (short) 4,
            "좋았어요", List.of(), List.of()
        ));

        reviewService.like(accountId, review.getId());
        reviewService.like(accountId, review.getId());

        assertThat(reviewLikeJpaRepository.count()).isEqualTo(1);
        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().getLikeCount())
            .isEqualTo(1);

        // 취소도 두 번 부를 수 있습니다.
        reviewService.unlike(accountId, review.getId());
        reviewService.unlike(accountId, review.getId());

        assertThat(reviewLikeJpaRepository.count()).isZero();
        assertThat(placeReviewJpaRepository.findById(review.getId()).orElseThrow().getLikeCount())
            .isZero();
    }
}
