package com.pawtrail.review.presentation.request;

import com.pawtrail.review.application.dto.input.ReviewUpdateInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

// 후기 수정 요청입니다.
//
// 보낸 칸만 바뀝니다. 안 보낸 칸은 지금 값을 그대로 둡니다.
// 그래서 모든 칸이 없어도 되며, 비어 있는 요청은 아무것도 바꾸지 않습니다.
//
// photos 와 tags 는 빈 배열을 보내면 "비움" 입니다.
// 안 보낸 것(바꾸지 않음)과 빈 배열(다 지움)이 다르므로 화면은 둘을 가려 보내야 합니다.
//
// 방문일과 반려동물은 여기 없습니다.
// 후기는 "그때 그 아이로 다녀온 기록" 이라 그 둘이 바뀌면 다른 글이 됩니다.
// 보내더라도 이 요청에 없는 칸이므로 그냥 흘려보냅니다.
public record UpdateReviewRequest(

    @Min(value = 1, message = "별점은 1점부터 5점까지입니다")
    @Max(value = 5, message = "별점은 1점부터 5점까지입니다")
    Short rating,

    @Min(value = 1, message = "시설 점수는 1점부터 5점까지입니다")
    @Max(value = 5, message = "시설 점수는 1점부터 5점까지입니다")
    Short facilityScore,

    @Min(value = 1, message = "규정 점수는 1점부터 5점까지입니다")
    @Max(value = 5, message = "규정 점수는 1점부터 5점까지입니다")
    Short ruleScore,

    @Min(value = 1, message = "분위기 점수는 1점부터 5점까지입니다")
    @Max(value = 5, message = "분위기 점수는 1점부터 5점까지입니다")
    Short moodScore,

    @Size(min = 1, max = 1000, message = "내용은 1자 이상 1000자 이하로 적어 주세요")
    String content,

    @Size(max = 5, message = "사진은 5장까지 올릴 수 있습니다")
    List<@NotBlank(message = "사진 주소가 비어 있습니다") String> photos,

    @Size(max = 20, message = "태그가 너무 많습니다")
    List<@NotBlank(message = "태그가 비어 있습니다") String> tags
) {

    public ReviewUpdateInput toInput() {
        return new ReviewUpdateInput(
            rating,
            facilityScore,
            ruleScore,
            moodScore,
            content,
            photos,
            tags
        );
    }
}
