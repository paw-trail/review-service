package com.pawtrail.review.application.dto.output;

import com.pawtrail.common.response.PageResponse;
import com.pawtrail.review.domain.repository.dto.ReviewPage;
import com.pawtrail.review.domain.repository.dto.ReviewSummary;

import java.util.List;

// 장소 상세의 후기 응답입니다.
//
// content 와 page 는 다른 목록 API 와 같은 모양이며 summary 만 더 붙습니다.
// 요약을 따로 부르게 하지 않는 이유는 화면이 목록과 평균을 늘 함께 그리기 때문입니다.
// 나눠 두면 화면이 요청을 두 번 보내고, 그 사이에 후기가 들어오면 둘이 어긋납니다.
public record PlaceReviewListOutput(
    List<ReviewDetailOutput> content,
    PageResponse.PageInfo page,
    ReviewSummaryOutput summary
) {

    public static PlaceReviewListOutput of(
        List<ReviewDetailOutput> content,
        ReviewPage<?> reviews,
        ReviewSummary summary
    ) {
        return new PlaceReviewListOutput(
            content,
            new PageResponse.PageInfo(
                reviews.number(),
                reviews.size(),
                reviews.totalElements(),
                reviews.totalPages()
            ),
            ReviewSummaryOutput.from(summary)
        );
    }
}
