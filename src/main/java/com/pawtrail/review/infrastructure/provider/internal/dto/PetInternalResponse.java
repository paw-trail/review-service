package com.pawtrail.review.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

// pet-service 의 /internal/pets 응답 중 후기가 쓰는 칸만 받습니다.
//
// 저쪽 응답에는 이름 · 캐리어 · 예방접종 같은 칸이 더 있지만
// 후기에 복사하는 것은 견종 이름 · 체중 · 크기 셋뿐입니다.
// 모르는 칸은 ignoreUnknown 이 흘려보냅니다.
@JsonIgnoreProperties(ignoreUnknown = true)
public record PetInternalResponse(
    UUID petId,
    BigDecimal weightKg,
    String breedSize,
    String breedName
) {
}
