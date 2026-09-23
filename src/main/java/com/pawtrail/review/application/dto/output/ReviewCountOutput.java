package com.pawtrail.review.application.dto.output;

// 마이페이지의 "내가 쓴 후기" 수입니다.
//
// 지운 후기는 세지 않습니다.
public record ReviewCountOutput(long count) {
}
