package com.pawtrail.review.infrastructure.persistence;

import com.pawtrail.review.domain.model.ReviewLike;
import com.pawtrail.review.domain.model.ReviewLikeId;
import com.pawtrail.review.domain.repository.ReviewLikeRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewLikeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewLikeRepositoryImpl implements ReviewLikeRepository {

    private final ReviewLikeJpaRepository jpaRepository;

    @Override
    public boolean exists(UUID reviewId, UUID accountId) {
        return jpaRepository.existsById(ReviewLikeId.of(reviewId, accountId));
    }

    @Override
    public ReviewLike save(ReviewLike reviewLike) {
        return jpaRepository.save(reviewLike);
    }

    @Override
    public void delete(UUID reviewId, UUID accountId) {
        jpaRepository.deleteById(ReviewLikeId.of(reviewId, accountId));
    }

    @Override
    public Set<UUID> findLikedReviewIds(UUID accountId, Collection<UUID> reviewIds) {
        if (reviewIds.isEmpty()) {
            return Set.of();
        }
        return jpaRepository.findLikedReviewIds(accountId, reviewIds);
    }

    @Override
    public void deleteAllByReviewId(UUID reviewId) {
        jpaRepository.deleteAllByReviewId(reviewId);
    }

    @Override
    public Set<UUID> findReviewIdsByAccountId(UUID accountId) {
        return jpaRepository.findReviewIdsByAccountId(accountId);
    }

    @Override
    public void deleteAllByAccountId(UUID accountId) {
        jpaRepository.deleteAllByAccountId(accountId);
    }

    @Override
    public void deleteAllByReviewIds(Collection<UUID> reviewIds) {
        if (!reviewIds.isEmpty()) {
            jpaRepository.deleteAllByReviewIds(reviewIds);
        }
    }

    @Override
    public long countByReviewId(UUID reviewId) {
        return jpaRepository.countByIdReviewId(reviewId);
    }
}
