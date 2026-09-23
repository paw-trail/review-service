package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.infrastructure.provider.internal.dto.InternalApiResponse;
import com.pawtrail.review.infrastructure.provider.internal.dto.PlaceInternalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PlaceProviderImpl implements PlaceProvider {

    // 한 번에 물어보는 개수입니다.
    //
    // place-service 의 조회 API 가 한 번에 100개까지만 받습니다.
    // 내 후기 목록은 쪽 하나가 100개까지라 보통 한 번에 끝나지만,
    // 장소가 겹치지 않는 경우까지 생각하면 나눠 부르는 길이 있어야 합니다.
    private static final int BATCH_SIZE = 100;

    private final RestClient internalRestClient;

    public PlaceProviderImpl(
        @Qualifier("internalRestClientBuilder") RestClient.Builder builder
    ) {
        this.internalRestClient = builder.baseUrl("lb://place-service").build();
    }

    @Override
    public Map<UUID, PlaceSummary> getPlaces(Collection<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> targets = placeIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();

        if (targets.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PlaceSummary> result = new LinkedHashMap<>();

        for (int from = 0; from < targets.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, targets.size());
            Map<UUID, PlaceSummary> chunk = requestChunk(targets.subList(from, to));

            // 한 묶음이라도 실패하면 이름을 반쯤 채운 목록이 나가므로 전부 비웁니다.
            // 어떤 줄은 이름이 있고 어떤 줄은 "알 수 없음" 인 화면이 더 이상해 보입니다.
            if (chunk == null) {
                return Map.of();
            }

            result.putAll(chunk);
        }

        return Collections.unmodifiableMap(result);
    }

    // 실패를 예외로 올리지 않고 null 로 돌려주는 자리입니다.
    //
    // 후기 목록의 본체는 본문과 별점이고 장소 이름은 곁들이는 값입니다.
    // 장소 조회가 안 된다고 목록 전체를 500 으로 막으면 읽을 수 있는 것까지 못 보게 됩니다.
    // 이름이 빠진 자리는 부르는 쪽이 "알 수 없음" 으로 채웁니다.
    private Map<UUID, PlaceSummary> requestChunk(List<UUID> placeIds) {
        String ids = placeIds.stream()
            .map(UUID::toString)
            .collect(Collectors.joining(","));

        try {
            InternalApiResponse<List<PlaceInternalResponse>> response = internalRestClient.get()
                .uri("/internal/places?ids={ids}", ids)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

            if (response == null
                || !"SUCCESS".equals(response.code())
                || response.data() == null) {
                log.warn("장소 응답이 비어 있습니다: 요청 {}건", placeIds.size());
                return null;
            }

            Map<UUID, PlaceSummary> chunk = new LinkedHashMap<>();
            for (PlaceInternalResponse place : response.data()) {
                if (place == null || place.placeId() == null) {
                    continue;
                }
                chunk.putIfAbsent(place.placeId(), new PlaceSummary(place.placeId(), place.name()));
            }

            return chunk;
        } catch (Exception exception) {
            log.warn(
                "장소를 받아오지 못했습니다: 요청 {}건, reason={}",
                placeIds.size(),
                exception.getMessage()
            );
            return null;
        }
    }
}
