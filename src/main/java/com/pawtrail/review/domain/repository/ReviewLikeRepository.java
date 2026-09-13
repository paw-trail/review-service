package com.pawtrail.review.domain.repository;

import com.pawtrail.review.domain.model.ReviewLike;

import java.util.UUID;
import java.util.Collection;
import java.util.Set;

public interface ReviewLikeRepository {

    boolean exists(UUID reviewId, UUID accountId);

    ReviewLike save(ReviewLike reviewLike);

    void delete(UUID reviewId, UUID accountId);

    Set<UUID> findLikedReviewIds(UUID accountId, Collection<UUID> reviewIds);

    void deleteAllByReviewId(UUID reviewId);

    Set<UUID> findReviewIdsByAccountId(UUID accountId);

    void deleteAllByAccountId(UUID accountId);

    void deleteAllByReviewIds(Collection<UUID> reviewIds);

    long countByReviewId(UUID reviewId);
}
