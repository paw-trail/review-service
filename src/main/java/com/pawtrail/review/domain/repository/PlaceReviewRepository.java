package com.pawtrail.review.domain.repository;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewStatistics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PlaceReviewRepository {

    PlaceReview save(PlaceReview review);

    Optional<PlaceReview> findActiveById(UUID reviewId);

    Optional<PlaceReview> findActiveByIdForUpdate(UUID reviewId);

    ReviewPage<PlaceReview> findActiveByPlaceId(UUID placeId, int page, int size);

    ReviewPage<PlaceReview> findActiveByAccountId(
        UUID accountId,
        ReviewSort sort,
        int page,
        int size
    );

    List<PlaceReview> findAllByAccountIdForUpdate(UUID accountId);

    List<PlaceReview> findActiveByAccountIdAndVisitedAtBetween(
        UUID accountId,
        LocalDate from,
        LocalDate to
    );

    long countActiveByAccountId(UUID accountId);

    List<ReviewStatistics> findActiveStatisticsByPlaceIds(Collection<UUID> placeIds);

    void hardDeleteAll(Collection<PlaceReview> reviews);
}
