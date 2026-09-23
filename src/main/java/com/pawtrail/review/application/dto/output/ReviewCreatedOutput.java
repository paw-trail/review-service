package com.pawtrail.review.application.dto.output;

import java.util.UUID;

// 작성 응답입니다. 만들어진 후기의 식별자만 돌려줍니다.
//
// 카드 전체를 돌려주지 않는 이유는 작성자 닉네임이 다른 서비스에 있기 때문입니다.
// 카드를 채우려면 쓰기 도중에 user-service 를 불러야 하는데,
// 쓰기에서는 다른 서비스를 부르지 않는다는 원칙과 어긋납니다.
// 화면도 작성을 마치면 장소 상세로 돌아가 목록을 다시 부릅니다.
public record ReviewCreatedOutput(UUID reviewId) {
}
