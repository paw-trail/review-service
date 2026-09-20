package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.PlaceProvider;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import com.pawtrail.review.infrastructure.provider.internal.dto.InternalApiResponse;
import com.pawtrail.review.infrastructure.provider.internal.dto.PlaceInternalResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PlaceProviderImpl implements PlaceProvider {

    private final RestClient internalRestClient;

    public PlaceProviderImpl(
        @Qualifier("internalRestClientBuilder") RestClient.Builder builder
    ) {
        this.internalRestClient = builder.baseUrl("lb://place-service").build();
    }

    @Override
    public Map<UUID, PlaceSummary> getPlaces(Collection<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        String ids = placeIds.stream()
            .distinct()
            .sorted()
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
                throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR);
            }

            return response.data().stream()
                .filter(place -> place != null && place.placeId() != null)
                .map(place -> new PlaceSummary(place.placeId(), place.name()))
                .collect(Collectors.toUnmodifiableMap(
                    PlaceSummary::placeId,
                    Function.identity(),
                    (first, ignored) -> first
                ));
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new CustomException(CommonErrorCode.EXTERNAL_API_ERROR, exception);
        }
    }
}
