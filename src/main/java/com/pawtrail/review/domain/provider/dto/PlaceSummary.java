package com.pawtrail.review.domain.provider.dto;

import java.util.UUID;

public record PlaceSummary(UUID placeId, String name) {
    private static final String UNKNOWN_NAME = "알 수 없음";

    public PlaceSummary {
        name = name == null || name.isBlank() ? UNKNOWN_NAME : name;
    }

    public static PlaceSummary unknown(UUID placeId) {
        return new PlaceSummary(placeId, UNKNOWN_NAME);
    }
}
