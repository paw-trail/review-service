package com.pawtrail.review.application.dto.output;

import com.pawtrail.review.domain.model.PlaceReview;
import com.pawtrail.review.domain.model.ReviewPet;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 하루 요약이 재료로 쓰는 후기입니다.
//
// 방문일 기준으로 모읍니다. 작성 시각이 아닙니다.
// 그날 어디를 다녀왔는지를 적는 글이라 언제 썼는지는 상관이 없습니다.
//
// 사진은 담지 않습니다. 요약을 만드는 데 쓰이지 않고,
// 주소를 서명해 내보내면 쓰이지도 않을 주소를 만드는 일이 됩니다.
//
// 견종은 목록입니다. 한 후기에 여러 마리가 들어가기 때문입니다.
// 칸 이름을 단수로 두고 쉼표로 이어 붙이지 않습니다.
// 이 값은 하루 요약의 프롬프트로 들어가서, 칸 이름과 내용이 어긋나도
// 문장이 그럴듯하게 나오는 바람에 틀어진 것을 늦게 알아차리게 됩니다.
public record ReviewPeriodOutput(
    UUID reviewId,
    UUID placeId,
    LocalDate visitedAt,
    short rating,
    String content,
    List<String> petBreedsAtVisit
) {

    // 견종을 정하지 않은 아이는 빈 칸으로 남아 있어 걸러 냅니다.
    // 목록에 null 이 섞이면 받는 쪽이 그 자리를 그대로 문장에 넣습니다.
    public static ReviewPeriodOutput from(PlaceReview review, List<ReviewPet> pets) {
        return new ReviewPeriodOutput(
            review.getId(),
            review.getPlaceId(),
            review.getVisitedAt(),
            review.getRating(),
            review.getContent(),
            pets.stream()
                .map(ReviewPet::getBreedName)
                .filter(breedName -> breedName != null && !breedName.isBlank())
                .toList()
        );
    }
}
