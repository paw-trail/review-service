package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.dto.PetSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PetProviderImplTest {

    @Test
    void mapsOwnedPetToSnapshot() {
        UUID accountId = UUID.randomUUID();
        UUID petId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo("lb://pet-service/internal/pets?ids=" + petId))
            .andRespond(withSuccess(
                """
                {"code":"SUCCESS","message":"성공","data":[
                  {"petId":"%s","name":"루나","weightKg":28.5,"breedSize":"LARGE",
                   "breedCode":"GOLDEN_RETRIEVER","breedName":"골든리트리버"}
                ],"traceId":null}
                """.formatted(petId),
                MediaType.APPLICATION_JSON
            ));

        Optional<PetSnapshot> snapshot = provider.findOwnedPet(accountId, petId);

        assertTrue(snapshot.isPresent());
        assertEquals("골든리트리버", snapshot.get().breedName());
        assertEquals("LARGE", snapshot.get().breedSize());
        assertEquals(0, new BigDecimal("28.5").compareTo(snapshot.get().weightKg()));
        server.verify();
    }

    // pet-service 가 헤더의 계정으로 걸러 내보내므로 빈 목록은 "내 것이 아님" 입니다.
    @Test
    void returnsEmptyWhenPetIsNotOwned() {
        UUID petId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo("lb://pet-service/internal/pets?ids=" + petId))
            .andRespond(withSuccess(
                "{\"code\":\"SUCCESS\",\"message\":\"성공\",\"data\":[],\"traceId\":null}",
                MediaType.APPLICATION_JSON
            ));

        assertTrue(provider.findOwnedPet(UUID.randomUUID(), petId).isEmpty());
        server.verify();
    }

    // 호출 실패는 빈 값과 뜻이 다릅니다.
    // 스냅샷은 나중에 채울 수 없으므로 작성을 그대로 실패시켜야 합니다.
    @Test
    void throwsWhenPetServiceCallFails() {
        UUID petId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo("lb://pet-service/internal/pets?ids=" + petId))
            .andRespond(withServerError());

        CustomException exception = assertThrows(CustomException.class, () ->
            provider.findOwnedPet(UUID.randomUUID(), petId));

        assertEquals(CommonErrorCode.EXTERNAL_API_ERROR, exception.getErrorCode());
        server.verify();
    }

    @Test
    void throwsWhenInternalResponseIsInvalid() {
        UUID petId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo("lb://pet-service/internal/pets?ids=" + petId))
            .andRespond(withSuccess(
                "{\"code\":\"FAIL\",\"message\":\"실패\",\"data\":null}",
                MediaType.APPLICATION_JSON
            ));

        CustomException exception = assertThrows(CustomException.class, () ->
            provider.findOwnedPet(UUID.randomUUID(), petId));

        assertEquals(CommonErrorCode.EXTERNAL_API_ERROR, exception.getErrorCode());
        server.verify();
    }
}
