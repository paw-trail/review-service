package com.pawtrail.review.domain.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface ReviewLikeRepository {

    // 좋아요를 넣습니다. 이미 있으면 아무것도 하지 않습니다.
    //
    // "있는지 보고 없으면 넣기" 로 나누지 않는 이유는 그 사이에 다른 요청이 끼어들 수 있기 때문입니다.
    // 넣는 쪽에서 한 번에 처리해야 같은 사람이 두 번 눌러도 결과가 하나로 모입니다.
    void insertIfAbsent(UUID reviewId, UUID accountId);

    // 좋아요를 뺍니다. 누르지 않은 상태에서 불러도 조용히 넘어갑니다.
    void delete(UUID reviewId, UUID accountId);

    Set<UUID> findLikedReviewIds(UUID accountId, Collection<UUID> reviewIds);

    void deleteAllByReviewId(UUID reviewId);

    Set<UUID> findReviewIdsByAccountId(UUID accountId);

    void deleteAllByAccountId(UUID accountId);

    void deleteAllByReviewIds(Collection<UUID> reviewIds);

    long countByReviewId(UUID reviewId);
}
