package com.pawtrail.review.presentation.request;

import com.pawtrail.review.application.dto.input.ReviewCreateInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateReviewRequest(

    // 함께 다녀온 아이들입니다. 한 마리부터 다섯 마리까지 받습니다.
    //
    // 최소 1인 이유는 아무도 고르지 않은 후기가 "누구와 다녀왔는지" 를 잃기 때문입니다.
    // 상한 5는 사진 상한과 같은 수이며, 말이 안 되는 크기의 요청을 여기서 끊습니다.
    //
    // 보낸 순서가 화면에 나오는 순서입니다.
    // 화면이 배지를 이어 붙이고 자리가 모자라면 뒤를 줄이므로 먼저 고른 아이가 먼저 보입니다.
    @NotNull(message = "어떤 아이와 다녀왔는지 골라 주세요")
    @Size(min = 1, max = 5, message = "반려동물은 1마리부터 5마리까지 고를 수 있습니다")
    List<@NotNull(message = "반려동물이 비어 있습니다") UUID> petIds,

    // 오늘까지만 받습니다. 다녀온 날이라 미래일 수 없습니다.
    // 서버 시계의 날짜로 보며 컨테이너는 TZ=Asia/Seoul 이라 서울 날짜입니다.
    @NotNull(message = "방문일을 골라 주세요")
    @PastOrPresent(message = "방문일은 오늘까지 고를 수 있습니다")
    LocalDate visitedAt,

    @NotNull(message = "별점을 매겨 주세요")
    @Min(value = 1, message = "별점은 1점부터 5점까지입니다")
    @Max(value = 5, message = "별점은 1점부터 5점까지입니다")
    Short rating,

    @NotNull(message = "시설 점수를 매겨 주세요")
    @Min(value = 1, message = "시설 점수는 1점부터 5점까지입니다")
    @Max(value = 5, message = "시설 점수는 1점부터 5점까지입니다")
    Short facilityScore,

    @NotNull(message = "규정 점수를 매겨 주세요")
    @Min(value = 1, message = "규정 점수는 1점부터 5점까지입니다")
    @Max(value = 5, message = "규정 점수는 1점부터 5점까지입니다")
    Short ruleScore,

    @NotNull(message = "분위기 점수를 매겨 주세요")
    @Min(value = 1, message = "분위기 점수는 1점부터 5점까지입니다")
    @Max(value = 5, message = "분위기 점수는 1점부터 5점까지입니다")
    Short moodScore,

    @NotBlank(message = "내용을 적어 주세요")
    @Size(max = 1000, message = "내용은 1000자까지 쓸 수 있습니다")
    String content,

    // 사진 주소입니다. 업로드 주소를 발급받을 때 함께 받은 fileUrl 을 그대로 보냅니다.
    // 서버가 여기서 키를 뽑아 저장하며, 본인 자리의 주소가 아니면 받지 않습니다.
    @Size(max = 5, message = "사진은 5장까지 올릴 수 있습니다")
    List<@NotBlank(message = "사진 주소가 비어 있습니다") String> photos,

    // 추천 태그 목록에 있는 값만 받습니다. 그 확인은 서비스가 합니다.
    // 여기 상한은 말이 안 되는 크기의 요청을 일찍 끊으려는 것입니다.
    @Size(max = 20, message = "태그가 너무 많습니다")
    List<@NotBlank(message = "태그가 비어 있습니다") String> tags
) {

    public ReviewCreateInput toInput() {
        return new ReviewCreateInput(
            petIds == null ? List.of() : List.copyOf(petIds),
            visitedAt,
            rating,
            facilityScore,
            ruleScore,
            moodScore,
            content,
            photos == null ? List.of() : List.copyOf(photos),
            tags == null ? List.of() : List.copyOf(tags)
        );
    }
}
