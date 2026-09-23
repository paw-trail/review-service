package com.pawtrail.review.infrastructure.persistence;

import com.pawtrail.review.domain.model.ReviewPet;
import com.pawtrail.review.domain.repository.ReviewPetRepository;
import com.pawtrail.review.infrastructure.persistence.jpa.ReviewPetJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ReviewPetRepositoryImpl implements ReviewPetRepository {

    private final ReviewPetJpaRepository jpaRepository;

    @Override
    public List<ReviewPet> saveAll(List<ReviewPet> pets) {
        return jpaRepository.saveAll(pets);
    }

    @Override
    public Map<UUID, List<ReviewPet>> findByReviewIds(Collection<UUID> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Map.of();
        }

        // 쿼리가 이미 review_id · sort_order 순으로 돌려주므로
        // 묶는 동안 순서가 유지되는 LinkedHashMap 과 리스트에 그대로 담습니다.
        return jpaRepository.findByReviewIdInOrderByReviewIdAscSortOrderAsc(reviewIds).stream()
            .collect(Collectors.groupingBy(
                ReviewPet::getReviewId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    @Override
    public List<ReviewPet> findByReviewId(UUID reviewId) {
        return jpaRepository.findByReviewIdOrderBySortOrderAsc(reviewId);
    }
}
