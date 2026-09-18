package com.pawtrail.review.domain.provider.dto;

import java.util.UUID;

public record UserSummary(
    UUID accountId,
    String nickname,
    String profileImageUrl
) {
    private static final String UNKNOWN_NICKNAME = "알 수 없음";

    public UserSummary {
        nickname = nickname == null || nickname.isBlank() ? UNKNOWN_NICKNAME : nickname;
    }

    public static UserSummary unknown(UUID accountId) {
        return new UserSummary(accountId, UNKNOWN_NICKNAME, null);
    }
}
