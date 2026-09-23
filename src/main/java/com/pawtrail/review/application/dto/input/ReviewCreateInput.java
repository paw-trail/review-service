package com.pawtrail.review.application.dto.input;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 후기 작성에 들어오는 값입니다.
//
// petIds 는 보낸 순서를 그대로 들고 있습니다.
// 그 순서가 review_pet 의 sort_order 가 되고 화면의 배지 순서가 됩니다.
// 같은 아이를 두 번 보냈을 때 한 번만 남기는 일은 서비스가 합니다.
//
// photos 는 아직 주소이고 키가 아닙니다.
// 본인 자리의 주소인지 보고 키로 바꾸는 일은 서비스가 합니다.
public record ReviewCreateInput(
    List<UUID> petIds,
    LocalDate visitedAt,
    short rating,
    short facilityScore,
    short ruleScore,
    short moodScore,
    String content,
    List<String> photos,
    List<String> tags
) {

    public ReviewCreateInput {
        petIds = petIds == null ? List.of() : List.copyOf(petIds);
        photos = photos == null ? List.of() : List.copyOf(photos);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
