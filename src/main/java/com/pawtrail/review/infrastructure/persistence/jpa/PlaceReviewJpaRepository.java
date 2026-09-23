package com.pawtrail.review.infrastructure.persistence.jpa;

import com.pawtrail.review.domain.model.PlaceReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlaceReviewJpaRepository extends JpaRepository<PlaceReview, UUID> {

    Optional<PlaceReview> findByIdAndDeletedAtIsNull(UUID reviewId);

    Page<PlaceReview> findByPlaceIdAndDeletedAtIsNull(UUID placeId, Pageable pageable);

    // 사진만 보기를 켰을 때 쓰는 조회입니다.
    //
    // photoCount 는 엔티티의 @Formula 칸이며 cardinality(photos) 로 계산됩니다.
    // 0 보다 큰 것만 고르므로 사진이 한 장도 없는 후기는 목록과 건수에서 함께 빠집니다.
    //
    // 조건을 파라미터 하나로 묶지 않고 메서드를 나눈 이유는
    // 정렬이 Pageable 로 들어와 두 경우 모두 같은 정렬 규칙을 그대로 쓸 수 있기 때문입니다.
    Page<PlaceReview> findByPlaceIdAndDeletedAtIsNullAndPhotoCountGreaterThan(
        UUID placeId,
        int minimumPhotoCount,
        Pageable pageable
    );

    Page<PlaceReview> findByAccountIdAndDeletedAtIsNull(UUID accountId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from PlaceReview review "
        + "where review.accountId = :accountId order by review.id")
    List<PlaceReview> findAllByAccountIdForUpdate(@Param("accountId") UUID accountId);

    List<PlaceReview> findByAccountIdAndDeletedAtIsNullAndVisitedAtBetweenOrderByVisitedAtAscCreatedAtAsc(
        UUID accountId,
        LocalDate from,
        LocalDate to
    );

    long countByAccountIdAndDeletedAtIsNull(UUID accountId);

    // 장소 상세 상단에 띄우는 요약입니다.
    //
    // 사진만 보기와 쪽 번호에 관계없이 그 장소의 살아 있는 후기 전부를 셉니다.
    // 상단 평균은 장소의 성격을 나타내는 값이고,
    // 검색 카드와 즐겨찾기에 나가는 평점(/internal/reviews/stats)도 같은 기준이어야
    // 화면끼리 숫자가 어긋나지 않기 때문입니다.
    //
    // group by 가 없는 집계라 후기가 하나도 없어도 행 하나가 돌아옵니다.
    // 이때 평균 넷은 null 이고 건수는 0 이며, 그 처리는 저장소 구현이 합니다.
    @Query("select avg(review.rating) as ratingAverage, "
        + "avg(review.facilityScore) as facilityAverage, "
        + "avg(review.ruleScore) as ruleAverage, "
        + "avg(review.moodScore) as moodAverage, "
        + "count(review) as reviewCount from PlaceReview review "
        + "where review.placeId = :placeId and review.deletedAt is null")
    ReviewSummaryProjection findActiveSummaryByPlaceId(@Param("placeId") UUID placeId);

    @Query("select review.placeId as placeId, avg(review.rating) as ratingAverage, "
        + "count(review) as reviewCount from PlaceReview review "
        + "where review.deletedAt is null and review.placeId in :placeIds "
        + "group by review.placeId")
    List<ReviewStatsProjection> findActiveStatisticsByPlaceIds(
        @Param("placeIds") Collection<UUID> placeIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from PlaceReview review "
        + "where review.id = :reviewId and review.deletedAt is null")
    Optional<PlaceReview> findActiveByIdForUpdate(@Param("reviewId") UUID reviewId);
}
