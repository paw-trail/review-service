package com.pawtrail.review.domain.repository;

import com.pawtrail.review.domain.model.ReviewPet;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 후기에 딸린 반려동물을 다루는 계약입니다.
//
// 후기 저장소와 나눈 이유는 부모 엔티티에 매달지 않았기 때문입니다.
// 목록 화면이 후기 여러 건을 한 번에 그리므로 id 목록으로 한 번에 받아 옵니다.
public interface ReviewPetRepository {

    List<ReviewPet> saveAll(List<ReviewPet> pets);

    // 후기 여러 건의 아이를 한 번에 받아 옵니다.
    //
    // 후기 id 로 묶어 돌려주며, 각 목록은 sort_order 순입니다.
    // 후기에 아이가 하나도 없으면 그 id 는 맵에 없습니다.
    // (V21 이관 뒤에 만들어진 후기는 반드시 한 마리 이상입니다)
    Map<UUID, List<ReviewPet>> findByReviewIds(Collection<UUID> reviewIds);

    // 후기 한 건의 아이입니다. sort_order 순입니다.
    List<ReviewPet> findByReviewId(UUID reviewId);
}
