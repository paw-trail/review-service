package com.pawtrail.review.domain.repository;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.enums.ReviewSort;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewStatistics;
import com.pawtrail.review.domain.repository.dto.ReviewSummary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PlaceReviewRepository {

    PlaceReview save(PlaceReview review);

    Optional<PlaceReview> findActiveById(UUID reviewId);

    Optional<PlaceReview> findActiveByIdForUpdate(UUID reviewId);

    // 장소 상세의 후기 목록입니다.
    //
    // photoOnly 가 참이면 사진이 있는 후기만 세고 보여 줍니다.
    // 이때 이 목록의 건수는 줄지만 아래 요약의 건수는 줄지 않습니다.
    ReviewPage<PlaceReview> findActiveByPlaceId(
        UUID placeId,
        ReviewSort sort,
        boolean photoOnly,
        int page,
        int size
    );

    // 장소 상세 상단의 요약입니다.
    //
    // 목록과 달리 사진만 보기와 쪽 번호를 보지 않고 그 장소의 후기 전부를 셉니다.
    ReviewSummary findActiveSummaryByPlaceId(UUID placeId);

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
