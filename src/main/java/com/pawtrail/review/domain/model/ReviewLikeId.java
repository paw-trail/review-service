package com.pawtrail.review.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ReviewLikeId implements Serializable {

    @Column(name = "review_id", columnDefinition = "uuid")
    private UUID reviewId;

    @Column(name = "account_id", columnDefinition = "uuid")
    private UUID accountId;

    public static ReviewLikeId of(UUID reviewId, UUID accountId) {
        return new ReviewLikeId(reviewId, accountId);
    }
}
