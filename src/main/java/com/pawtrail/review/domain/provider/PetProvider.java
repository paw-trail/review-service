package com.pawtrail.review.domain.provider;

import com.pawtrail.review.domain.provider.dto.PetSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// 후기를 쓸 때 반려동물 정보를 받아 오는 계약입니다.
//
// 받아 온 값은 후기 행에 복사해 두고 다시 묻지 않습니다.
// 반려동물의 체중이나 크기는 나중에 바뀌는데, 후기는 "그때 그 아이로 다녀온 기록" 이라
// 지금 값으로 다시 그리면 글의 뜻이 달라집니다.
public interface PetProvider {

    // 그 계정의 반려동물만 골라 돌려줍니다.
    //
    // 여러 마리를 한 번에 묻습니다. 한 마리씩 부르면 다섯 마리를 고른 후기가
    // 작성 한 번에 호출 다섯 번을 냅니다.
    //
    // 돌아온 맵에 없는 id 는 없거나 남의 반려동물입니다.
    // 소유자 확인은 pet-service 가 합니다.
    // 게이트웨이가 넣은 X-User-Id 로 자기 것만 걸러 내보내므로
    // 빠진 id 는 그 계정의 것이 아니라는 뜻입니다.
    // 무엇이 빠졌는지 보고 작성을 실패시키는 일은 서비스가 합니다.
    //
    // 호출 자체가 실패하면 빈 맵이 아니라 예외를 올립니다.
    // 장소 이름처럼 나중에 채울 수 있는 값이 아니라, 지금 복사하지 못하면
    // 영영 빈 채로 남는 값이기 때문입니다.
    Map<UUID, PetSnapshot> findOwnedPets(UUID accountId, Collection<UUID> petIds);
}
