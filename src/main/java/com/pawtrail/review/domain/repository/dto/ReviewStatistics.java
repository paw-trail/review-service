package com.pawtrail.review.domain.repository.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReviewStatistics(
    UUID placeId,
    BigDecimal ratingAverage,
    long reviewCount
) {
}
