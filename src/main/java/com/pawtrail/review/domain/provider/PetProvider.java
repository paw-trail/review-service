package com.pawtrail.review.domain.provider;

import com.pawtrail.review.domain.provider.dto.PetSnapshot;

import java.util.Optional;
import java.util.UUID;

// 후기를 쓸 때 반려동물 정보를 받아 오는 계약입니다.
//
// 받아 온 값은 후기 행에 복사해 두고 다시 묻지 않습니다.
// 반려동물의 체중이나 크기는 나중에 바뀌는데, 후기는 "그때 그 아이로 다녀온 기록" 이라
// 지금 값으로 다시 그리면 글의 뜻이 달라집니다.
public interface PetProvider {

    // 그 계정의 반려동물이면 스냅샷을, 아니면 비어 있는 값을 줍니다.
    //
    // 소유자 확인은 pet-service 가 합니다.
    // 게이트웨이가 넣은 X-User-Id 로 자기 것만 걸러 내보내므로
    // 응답이 비어 있다는 것은 없거나 남의 반려동물이라는 뜻입니다.
    //
    // 호출 자체가 실패하면 비어 있는 값이 아니라 예외를 올립니다.
    // 장소 이름처럼 나중에 채울 수 있는 값이 아니라, 지금 복사하지 못하면
    // 영영 빈 채로 남는 값이기 때문입니다.
    Optional<PetSnapshot> findOwnedPet(UUID accountId, UUID petId);
}
