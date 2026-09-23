package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.PetProvider;
import com.pawtrail.review.domain.provider.dto.PetSnapshot;
import com.pawtrail.review.infrastructure.provider.internal.dto.InternalApiResponse;
import com.pawtrail.review.infrastructure.provider.internal.dto.PetInternalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PetProviderImpl implements PetProvider {

    private final RestClient internalRestClient;

    public PetProviderImpl(
        @Qualifier("internalRestClientBuilder") RestClient.Builder builder
    ) {
        this.internalRestClient = builder.baseUrl("lb://pet-service").build();
    }

    @Override
    public Map<UUID, PetSnapshot> findOwnedPets(UUID accountId, Collection<UUID> petIds) {
        if (petIds == null || petIds.isEmpty()) {
            return Map.of();
        }

        // 같은 id 를 두 번 물을 이유가 없어 여기서 한 번 걸러 보냅니다.
        Set<UUID> unique = petIds.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (unique.isEmpty()) {
            return Map.of();
        }

        String ids = unique.stream()
            .map(UUID::toString)
            .collect(Collectors.joining(","));

        InternalApiResponse<List<PetInternalResponse>> response;

        try {
            response = internalRestClient.get()
                .uri("/internal/pets?ids={ids}", ids)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        } catch (Exception exception) {
            // 여기서는 빈 값으로 넘기지 않습니다.
            // 빈 값은 "남의 반려동물" 이라는 뜻인데, 호출이 실패한 것은 뜻이 다릅니다.
            // 둘을 섞으면 pet-service 가 잠깐 죽은 동안 작성이 403 으로 보여 원인을 못 찾습니다.
            log.warn(
                "반려동물을 받아오지 못했습니다: accountId={}, petIds={}, reason={}",
                accountId,
                ids,
                exception.getMessage()
            );
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }

        if (response == null
            || !"SUCCESS".equals(response.code())
            || response.data() == null) {
            log.warn("반려동물 응답이 비어 있습니다: accountId={}, petIds={}", accountId, ids);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        // pet-service 가 헤더의 계정으로 이미 걸러 냈으므로
        // 물어본 id 중 돌아오지 않은 것은 없거나 남의 반려동물입니다.
        //
        // 물어보지 않은 id 가 섞여 오는 경우도 걸러 냅니다.
        // 그대로 받으면 고르지도 않은 아이가 후기에 붙습니다.
        return response.data().stream()
            .filter(pet -> pet != null && pet.petId() != null && unique.contains(pet.petId()))
            .collect(Collectors.toMap(
                PetInternalResponse::petId,
                pet -> new PetSnapshot(pet.breedName(), pet.weightKg(), pet.breedSize()),
                (first, second) -> first
            ));
    }
}
