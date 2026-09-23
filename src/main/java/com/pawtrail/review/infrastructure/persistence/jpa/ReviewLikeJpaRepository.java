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

    // 이미 있으면 아무것도 하지 않고 넘어갑니다.
    //
    // 스프링 데이터의 save 를 쓰지 않는 이유가 있습니다.
    // 이 엔티티는 키를 직접 박고 들어가므로 save 가 persist 가 아니라 merge 로 가고,
    // 실제 INSERT 가 flush 나 커밋 때 나갑니다.
    // 그래서 "있는지 보고 없으면 넣는" 코드는 확인과 저장 사이에 다른 요청이 끼어들면
    // 기본키 충돌을 잡을 수 없는 자리에서 터뜨립니다.
    //
    // 넣지 않았으면 0을 돌려주며 트리거도 돌지 않아 like_count 가 어긋나지 않습니다.
    //
    // created_at 에 기본값이 없어 여기서 넣습니다.
    // localtimestamp 는 세션 시간대의 timestamp 를 주므로
    // JPA 가 @CreationTimestamp 로 넣는 값과 같은 모양입니다.
    @Modifying
    @Query(value = "insert into review_like (review_id, account_id, created_at) "
        + "values (:reviewId, :accountId, localtimestamp) "
        + "on conflict (review_id, account_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("reviewId") UUID reviewId, @Param("accountId") UUID accountId);

    // 없으면 0행입니다. 취소를 두 번 불러도 예외가 나지 않습니다.
    @Modifying
    @Query("delete from ReviewLike reviewLike "
        + "where reviewLike.id.reviewId = :reviewId "
        + "and reviewLike.id.accountId = :accountId")
    int deleteLike(@Param("reviewId") UUID reviewId, @Param("accountId") UUID accountId);

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
