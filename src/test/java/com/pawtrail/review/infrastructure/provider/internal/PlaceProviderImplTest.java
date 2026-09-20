package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.dto.PlaceSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PlaceProviderImplTest {

    @Test
    void fetchesRequestedPlacesInSingleBatch() {
        List<UUID> placeIds = List.of(new UUID(0, 2), new UUID(0, 1), new UUID(0, 2));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlaceProviderImpl provider = new PlaceProviderImpl(builder);
        List<UUID> expectedIds = placeIds.stream().distinct().sorted().toList();

        server.expect(requestTo(
                "lb://place-service/internal/places?ids=" + encodedIds(expectedIds)
            ))
            .andRespond(withSuccess(
                responseBody(expectedIds.getFirst(), expectedIds.getLast()),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, PlaceSummary> places = provider.getPlaces(placeIds);

        assertEquals("첫 번째 장소", places.get(expectedIds.getFirst()).name());
        assertEquals("마지막 장소", places.get(expectedIds.getLast()).name());
        assertEquals(2, places.size());
        server.verify();
    }

    @Test
    void returnsEmptyMapWithoutRequestForEmptyIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlaceProviderImpl provider = new PlaceProviderImpl(builder);

        assertEquals(Map.of(), provider.getPlaces(List.of()));
        server.verify();
    }

    @Test
    void ignoresNullPlaceEntriesAndUsesUnknownNameForBlankName() {
        UUID placeId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlaceProviderImpl provider = new PlaceProviderImpl(builder);

        server.expect(requestTo(
                "lb://place-service/internal/places?ids=" + placeId
            ))
            .andRespond(withSuccess(
                """
                {"code":"SUCCESS","message":"성공","data":[null,
                  {"placeId":"%s","name":" "}
                ],"traceId":null}
                """.formatted(placeId),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, PlaceSummary> places = provider.getPlaces(List.of(placeId));

        assertEquals("알 수 없음", places.get(placeId).name());
        server.verify();
    }

    @Test
    void rejectsInvalidInternalResponse() {
        UUID placeId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlaceProviderImpl provider = new PlaceProviderImpl(builder);

        server.expect(requestTo(
                "lb://place-service/internal/places?ids=" + placeId
            ))
            .andRespond(withSuccess(
                "{\"code\":\"FAIL\",\"message\":\"실패\",\"data\":null}",
                MediaType.APPLICATION_JSON
            ));

        assertThrows(CustomException.class, () -> provider.getPlaces(List.of(placeId)));
        server.verify();
    }

    private String encodedIds(List<UUID> placeIds) {
        return placeIds.stream()
            .map(UUID::toString)
            .collect(Collectors.joining("%2C"));
    }

    private String responseBody(UUID firstId, UUID lastId) {
        return """
            {"code":"SUCCESS","message":"성공","data":[
              {"placeId":"%s","name":"첫 번째 장소"},
              {"placeId":"%s","name":"마지막 장소"}
            ],"traceId":null}
            """.formatted(firstId, lastId);
    }
}
