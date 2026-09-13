package com.pawtrail.review.infrastructure.persistence.jpa;

import com.pawtrail.review.domain.model.ReviewLike;
import com.pawtrail.review.domain.model.ReviewLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface ReviewLikeJpaRepository extends JpaRepository<ReviewLike, ReviewLikeId> {

    @Query("select reviewLike.id.reviewId from ReviewLike reviewLike "
        + "where reviewLike.id.accountId = :accountId "
        + "and reviewLike.id.reviewId in :reviewIds")
    Set<UUID> findLikedReviewIds(
        @Param("accountId") UUID accountId,
        @Param("reviewIds") Collection<UUID> reviewIds
    );

    @Modifying
    @Query("delete from ReviewLike reviewLike where reviewLike.id.reviewId = :reviewId")
    void deleteAllByReviewId(@Param("reviewId") UUID reviewId);

    @Query("select reviewLike.id.reviewId from ReviewLike reviewLike "
        + "where reviewLike.id.accountId = :accountId")
    Set<UUID> findReviewIdsByAccountId(@Param("accountId") UUID accountId);

    @Modifying
    @Query("delete from ReviewLike reviewLike where reviewLike.id.accountId = :accountId")
    void deleteAllByAccountId(@Param("accountId") UUID accountId);

    @Modifying
    @Query("delete from ReviewLike reviewLike where reviewLike.id.reviewId in :reviewIds")
    void deleteAllByReviewIds(@Param("reviewIds") Collection<UUID> reviewIds);

    long countByIdReviewId(UUID reviewId);
}
