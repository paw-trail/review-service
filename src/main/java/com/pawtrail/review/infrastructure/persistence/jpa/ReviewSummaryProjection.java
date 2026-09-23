package com.pawtrail.review.infrastructure.persistence.jpa;

// 장소별 요약 집계를 받는 자리입니다.
//
// 평균 넷은 후기가 하나도 없을 때 null 이 오므로 Double 로 받습니다.
// 건수는 count 라 언제나 값이 있어 long 입니다.
public interface ReviewSummaryProjection {

    Double getRatingAverage();

    Double getFacilityAverage();

    Double getRuleAverage();

    Double getMoodAverage();

    long getReviewCount();
}
