package com.pawtrail.review.infrastructure.provider.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInternalResponse(
    UUID accountId,
    String nickname,
    String profileImageUrl
) {
}
