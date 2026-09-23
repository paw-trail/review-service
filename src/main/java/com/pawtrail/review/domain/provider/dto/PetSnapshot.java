package com.pawtrail.review.domain.provider.dto;

import java.math.BigDecimal;

// 후기 행에 복사해 두는 값입니다.
//
// 표의 칸과 하나씩 맞습니다.
//   breedName  → pet_breed_at_visit  (40자)
//   weightKg   → pet_weight_at_visit (4자리, 소수 1자리)
//   breedSize  → pet_size_at_visit   (12자)
public record PetSnapshot(
    String breedName,
    BigDecimal weightKg,
    String breedSize
) {
}
