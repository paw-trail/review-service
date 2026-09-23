package com.pawtrail.review.application.dto.input;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 후기 작성에 들어오는 값입니다.
//
// photos 는 아직 주소이고 키가 아닙니다.
// 본인 자리의 주소인지 보고 키로 바꾸는 일은 서비스가 합니다.
public record ReviewCreateInput(
    UUID petId,
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
        photos = photos == null ? List.of() : List.copyOf(photos);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
