package com.pawtrail.review.infrastructure.persistence;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.repository.PlaceReviewRepository;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewStatistics;
import com.pawtrail.review.domain.repository.dto.ReviewSummary;
import com.pawtrail.review.infrastructure.persistence.jpa.PlaceReviewJpaRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewStatsProjection;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewSummaryProjection;
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

    // 평균을 소수 몇째 자리까지 둘지입니다.
    // 화면과 검색 카드가 모두 한 자리로 보여 주므로 여기서 맞춰 내보냅니다.
    private static final int AVERAGE_SCALE = 1;

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
    public ReviewPage<PlaceReview> findActiveByPlaceId(
        UUID placeId,
        ReviewSort sort,
        boolean photoOnly,
        int page,
        int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, toPlaceSort(sort));

        Page<PlaceReview> result = photoOnly
            ? jpaRepository.findByPlaceIdAndDeletedAtIsNullAndPhotoCountGreaterThan(
                placeId,
                0,
                pageRequest
            )
            : jpaRepository.findByPlaceIdAndDeletedAtIsNull(placeId, pageRequest);

        return toReviewPage(result);
    }

    @Override
    public ReviewSummary findActiveSummaryByPlaceId(UUID placeId) {
        ReviewSummaryProjection projection = jpaRepository.findActiveSummaryByPlaceId(placeId);

        // 집계 쿼리라 행은 언제나 하나 돌아오지만, 후기가 없으면 평균이 null 입니다.
        if (projection == null || projection.getReviewCount() == 0) {
            return ReviewSummary.empty();
        }

        return new ReviewSummary(
            toAverage(projection.getRatingAverage()),
            toAverage(projection.getFacilityAverage()),
            toAverage(projection.getRuleAverage()),
            toAverage(projection.getMoodAverage()),
            projection.getReviewCount()
        );
    }

    @Override
    public ReviewPage<PlaceReview> findActiveByAccountId(
        UUID accountId,
        ReviewSort sort,
        int page,
        int size
    ) {
        Sort springSort = switch (sort) {
            case RECENT -> Sort.by(
                Sort.Order.desc("visitedAt"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            );
            case OLDEST -> Sort.by(
                Sort.Order.asc("visitedAt"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")
            );
            case RATING_DESC -> Sort.by(
                Sort.Order.desc("rating"),
                Sort.Order.desc("visitedAt"),
                Sort.Order.desc("id")
            );
            case RATING_ASC -> Sort.by(
                Sort.Order.asc("rating"),
                Sort.Order.desc("visitedAt"),
                Sort.Order.desc("id")
            );
        };

        Page<PlaceReview> result = jpaRepository.findByAccountIdAndDeletedAtIsNull(
            accountId,
            PageRequest.of(page, size, springSort)
        );

        return toReviewPage(result);
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

    // 장소 상세의 정렬입니다.
    //
    // 내 후기와 달리 작성 시각이 기준입니다.
    // 장소 쪽은 "최근에 올라온 글" 을 보여 주는 자리이고
    // 내 후기 쪽은 "언제 다녀왔는지" 가 기준이라 방문일로 정렬합니다.
    //
    // 마지막에 언제나 id 를 붙이는 이유는 같은 값이 여럿일 때 순서가 흔들리지 않게 하기
    // 위해서입니다. 순서가 흔들리면 쪽을 넘길 때 같은 후기가 두 번 보이거나 빠집니다.
    //
    // OLDEST 는 내 후기에만 있는 정렬이라 여기로 들어오지 않습니다.
    // 서비스가 먼저 걸러 400 으로 돌려보냅니다.
    private Sort toPlaceSort(ReviewSort sort) {
        return switch (sort) {
            case RECENT -> Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            );
            case RATING_DESC -> Sort.by(
                Sort.Order.desc("rating"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            );
            case RATING_ASC -> Sort.by(
                Sort.Order.asc("rating"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
            );
            case OLDEST -> Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("id")
            );
        };
    }

    private ReviewPage<PlaceReview> toReviewPage(Page<PlaceReview> result) {
        return new ReviewPage<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private BigDecimal toAverage(Double value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(AVERAGE_SCALE);
        }
        return BigDecimal.valueOf(value).setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);
    }

    private ReviewStatistics toStatistics(ReviewStatsProjection projection) {
        BigDecimal average = BigDecimal.valueOf(projection.getRatingAverage())
            .setScale(AVERAGE_SCALE, RoundingMode.HALF_UP);

        return new ReviewStatistics(
            projection.getPlaceId(),
            average,
            projection.getReviewCount()
        );
    }
}
