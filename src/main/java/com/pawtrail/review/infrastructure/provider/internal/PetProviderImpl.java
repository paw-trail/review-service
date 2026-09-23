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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    public Optional<PetSnapshot> findOwnedPet(UUID accountId, UUID petId) {
        if (petId == null) {
            return Optional.empty();
        }

        InternalApiResponse<List<PetInternalResponse>> response;

        try {
            response = internalRestClient.get()
                .uri("/internal/pets?ids={ids}", petId.toString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        } catch (Exception exception) {
            // 여기서는 빈 값으로 넘기지 않습니다.
            // 빈 값은 "남의 반려동물" 이라는 뜻인데, 호출이 실패한 것은 뜻이 다릅니다.
            // 둘을 섞으면 pet-service 가 잠깐 죽은 동안 작성이 403 으로 보여 원인을 못 찾습니다.
            log.warn(
                "반려동물을 받아오지 못했습니다: accountId={}, petId={}, reason={}",
                accountId,
                petId,
                exception.getMessage()
            );
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }

        if (response == null
            || !"SUCCESS".equals(response.code())
            || response.data() == null) {
            log.warn("반려동물 응답이 비어 있습니다: accountId={}, petId={}", accountId, petId);
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        // pet-service 가 헤더의 계정으로 이미 걸러 냈으므로
        // 목록이 비어 있으면 없거나 남의 반려동물입니다.
        return response.data().stream()
            .filter(pet -> pet != null && petId.equals(pet.petId()))
            .findFirst()
            .map(pet -> new PetSnapshot(pet.breedName(), pet.weightKg(), pet.breedSize()));
    }
}
