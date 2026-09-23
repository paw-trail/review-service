package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.repository.dto.ReviewSummary;

import java.math.BigDecimal;

// 장소 상세 상단에 뜨는 값입니다.
//
// reviewCount 는 그 장소의 후기 전부를 센 값이라
// 사진만 보기를 켠 목록의 page.totalElements 와 다를 수 있습니다.
// 두 값은 뜻이 다릅니다. 이쪽은 장소의 후기 수, 저쪽은 지금 보고 있는 목록의 길이입니다.
public record ReviewSummaryOutput(
    BigDecimal ratingAvg,
    BigDecimal facilityAvg,
    BigDecimal ruleAvg,
    BigDecimal moodAvg,
    long reviewCount
) {

    public static ReviewSummaryOutput from(ReviewSummary summary) {
        return new ReviewSummaryOutput(
            summary.ratingAverage(),
            summary.facilityAverage(),
            summary.ruleAverage(),
            summary.moodAverage(),
            summary.reviewCount()
        );
    }
}
