package com.pawtrail.review.infrastructure.persistence;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewStatistics;
import com.pawtrail.review.infrastructure.persistence.jpa.PlaceReviewJpaRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewStatsProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlaceReviewRepositoryImpl implements PlaceReviewRepository {

    private final PlaceReviewJpaRepository jpaRepository;

    @Override
    public PlaceReview save(PlaceReview review) {
        return jpaRepository.save(review);
    }

    @Override
    public Optional<PlaceReview> findActiveById(UUID reviewId) {
        return jpaRepository.findByIdAndDeletedAtIsNull(reviewId);
    }

    @Override
    public Optional<PlaceReview> findActiveByIdForUpdate(UUID reviewId) {
        return jpaRepository.findActiveByIdForUpdate(reviewId);
    }

    @Override
    public ReviewPage<PlaceReview> findActiveByPlaceId(UUID placeId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<PlaceReview> result = jpaRepository.findByPlaceIdAndDeletedAtIsNull(
            placeId,
            pageRequest
        );
        return new ReviewPage<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    @Override
    public List<PlaceReview> findActiveByAccountId(UUID accountId, ReviewSort sort) {
        Sort springSort = switch (sort) {
            case RECENT -> Sort.by(Sort.Order.desc("visitedAt"), Sort.Order.desc("createdAt"));
            case OLDEST -> Sort.by(Sort.Order.asc("visitedAt"), Sort.Order.asc("createdAt"));
            case RATING_DESC -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("visitedAt"));
            case RATING_ASC -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("visitedAt"));
        };
        return jpaRepository.findByAccountIdAndDeletedAtIsNull(accountId, springSort);
    }

    @Override
    public List<PlaceReview> findAllByAccountIdForUpdate(UUID accountId) {
        return jpaRepository.findAllByAccountIdForUpdate(accountId);
    }

    @Override
    public List<PlaceReview> findActiveByAccountIdAndVisitedAtBetween(
        UUID accountId,
        LocalDate from,
        LocalDate to
    ) {
        return jpaRepository
            .findByAccountIdAndDeletedAtIsNullAndVisitedAtBetweenOrderByVisitedAtAscCreatedAtAsc(
                accountId,
                from,
                to
            );
    }

    @Override
    public long countActiveByAccountId(UUID accountId) {
        return jpaRepository.countByAccountIdAndDeletedAtIsNull(accountId);
    }

    @Override
    public List<ReviewStatistics> findActiveStatisticsByPlaceIds(Collection<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findActiveStatisticsByPlaceIds(placeIds).stream()
            .map(this::toStatistics)
            .toList();
    }

    @Override
    public void hardDeleteAll(Collection<PlaceReview> reviews) {
        if (!reviews.isEmpty()) {
            jpaRepository.deleteAll(reviews);
        }
    }

    private ReviewStatistics toStatistics(ReviewStatsProjection projection) {
        BigDecimal average = BigDecimal.valueOf(projection.getRatingAverage())
            .setScale(1, RoundingMode.HALF_UP);
        return new ReviewStatistics(
            projection.getPlaceId(),
            average,
            projection.getReviewCount()
        );
    }
}
