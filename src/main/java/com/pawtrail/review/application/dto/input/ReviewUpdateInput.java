package com.pawtrail.review.application.dto.input;

import java.util.List;

// 후기 수정에 들어오는 값입니다.
//
// null 은 "안 보냄" 이고 그 칸은 그대로 둡니다.
// photos 와 tags 는 빈 목록이 "비움" 이라 null 과 뜻이 다릅니다.
// 그래서 여기서는 null 을 빈 목록으로 바꾸지 않습니다.
public record ReviewUpdateInput(
    Short rating,
    Short facilityScore,
    Short ruleScore,
    Short moodScore,
    String content,
    List<String> photos,
    List<String> tags
) {

    public ReviewUpdateInput {
        photos = photos == null ? null : List.copyOf(photos);
        tags = tags == null ? null : List.copyOf(tags);
    }
}
