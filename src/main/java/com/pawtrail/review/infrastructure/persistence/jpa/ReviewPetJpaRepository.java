package com.pawtrail.review.infrastructure.persistence.jpa;

import com.pawtrail.review.domain.model.ReviewPet;
import com.pawtrail.review.domain.model.ReviewPetId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewPetJpaRepository extends JpaRepository<ReviewPet, ReviewPetId> {

    // 후기 여러 건의 아이를 한 번에 가져옵니다.
    //
    // 정렬을 쿼리에서 합니다.
    // 후기별로 나눠 담는 쪽에서 다시 정렬하지 않아도 되고,
    // 한 후기의 아이가 많아야 다섯 줄이라 DB 가 정렬해도 비용이 들지 않습니다.
    List<ReviewPet> findByReviewIdInOrderByReviewIdAscSortOrderAsc(Collection<UUID> reviewIds);

    List<ReviewPet> findByReviewIdOrderBySortOrderAsc(UUID reviewId);
}
