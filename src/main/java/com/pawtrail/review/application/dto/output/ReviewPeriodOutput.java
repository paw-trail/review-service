package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.model.PlaceReview;

import java.time.LocalDate;
import java.util.UUID;

// 하루 요약이 재료로 쓰는 후기입니다.
//
// 방문일 기준으로 모읍니다. 작성 시각이 아닙니다.
// 그날 어디를 다녀왔는지를 적는 글이라 언제 썼는지는 상관이 없습니다.
//
// 사진은 담지 않습니다. 요약을 만드는 데 쓰이지 않고,
// 주소를 서명해 내보내면 쓰이지도 않을 주소를 만드는 일이 됩니다.
public record ReviewPeriodOutput(
    UUID reviewId,
    UUID placeId,
    LocalDate visitedAt,
    short rating,
    String content,
    String petBreedAtVisit
) {

    public static ReviewPeriodOutput from(PlaceReview review) {
        return new ReviewPeriodOutput(
            review.getId(),
            review.getPlaceId(),
            review.getVisitedAt(),
            review.getRating(),
            review.getContent(),
            review.getPetBreedAtVisit()
        );
    }
}
