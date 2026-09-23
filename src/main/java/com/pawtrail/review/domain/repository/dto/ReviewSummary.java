package com.pawtrail.review.domain.repository.dto;

import java.math.BigDecimal;

// 장소 하나의 후기 요약입니다.
//
// 평균 넷은 소수 첫째 자리까지만 둡니다.
// 화면이 4.8 / 5 처럼 한 자리로 보여 주고,
// 검색 카드에 나가는 평점(/internal/reviews/stats)도 같은 자리에서 반올림하므로
// 두 곳의 숫자가 같아야 하기 때문입니다.
public record ReviewSummary(
    BigDecimal ratingAverage,
    BigDecimal facilityAverage,
    BigDecimal ruleAverage,
    BigDecimal moodAverage,
    long reviewCount
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(1);

    // 후기가 하나도 없는 장소의 요약입니다.
    //
    // 평균을 null 로 두지 않는 이유는 화면이 그대로 그려야 하기 때문입니다.
    // 건수가 0 인지로 후기 없음을 알 수 있으므로 평균은 0.0 으로 둡니다.
    public static ReviewSummary empty() {
        return new ReviewSummary(ZERO, ZERO, ZERO, ZERO, 0L);
    }
}
