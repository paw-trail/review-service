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

    List<PlaceReview> findByAccountIdAndDeletedAtIsNull(UUID accountId, org.springframework.data.domain.Sort sort);

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
