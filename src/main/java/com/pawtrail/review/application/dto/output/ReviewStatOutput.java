package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.repository.dto.ReviewStatistics;

import java.math.BigDecimal;

// 장소 하나의 평점과 후기 수입니다.
//
// 즐겨찾기 카드와 검색 카드가 같은 값을 씁니다.
// 평균은 소수 한 자리이며, 후기가 없는 장소는 응답에 아예 담기지 않습니다.
public record ReviewStatOutput(
    java.util.UUID placeId,
    BigDecimal ratingAvg,
    long reviewCount
) {

    public static ReviewStatOutput from(ReviewStatistics statistics) {
        return new ReviewStatOutput(
            statistics.placeId(),
            statistics.ratingAverage(),
            statistics.reviewCount()
        );
    }
}
