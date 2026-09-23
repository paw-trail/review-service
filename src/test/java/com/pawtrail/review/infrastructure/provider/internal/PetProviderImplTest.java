package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.common.exception.CommonErrorCode;
import com.pawtrail.common.exception.CustomException;
import com.pawtrail.review.domain.provider.dto.PetSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PetProviderImplTest {

    private static final String URL_PREFIX = "lb://pet-service/internal/pets?ids=";

    // 한 후기에 여러 마리가 들어가므로 한 번에 묻습니다.
    // 한 마리씩 부르면 다섯 마리를 고른 후기가 작성 한 번에 호출 다섯 번을 냅니다.
    @Test
    void mapsOwnedPetsToSnapshots() {
        UUID accountId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo(URL_PREFIX + encodedIds(List.of(first, second))))
            .andRespond(withSuccess(
                """
                {"code":"SUCCESS","message":"성공","data":[
                  {"petId":"%s","name":"루나","weightKg":28.5,"breedSize":"LARGE",
                   "breedCode":"GOLDEN_RETRIEVER","breedName":"골든리트리버"},
                  {"petId":"%s","name":"콩","weightKg":3.2,"breedSize":"SMALL",
                   "breedCode":"MALTESE","breedName":"말티즈"}
                ],"traceId":null}
                """.formatted(first, second),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, PetSnapshot> snapshots = provider.findOwnedPets(
            accountId,
            List.of(first, second)
        );

        assertEquals(2, snapshots.size());
        assertEquals("골든리트리버", snapshots.get(first).breedName());
        assertEquals("LARGE", snapshots.get(first).breedSize());
        assertEquals(0, new BigDecimal("28.5").compareTo(snapshots.get(first).weightKg()));
        assertEquals("말티즈", snapshots.get(second).breedName());
        assertEquals("SMALL", snapshots.get(second).breedSize());
        server.verify();
    }

    // 같은 아이를 두 번 물을 이유가 없어 부르기 전에 한 번 거릅니다.
    @Test
    void asksEachPetOnlyOnce() {
        UUID petId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo(URL_PREFIX + petId))
            .andRespond(withSuccess(
                """
                {"code":"SUCCESS","message":"성공","data":[
                  {"petId":"%s","weightKg":3.2,"breedSize":"SMALL","breedName":"말티즈"}
                ],"traceId":null}
                """.formatted(petId),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, PetSnapshot> snapshots = provider.findOwnedPets(
            UUID.randomUUID(),
            List.of(petId, petId)
        );

        assertEquals(1, snapshots.size());
        server.verify();
    }

    // 물어볼 것이 없으면 부르지 않습니다.
    @Test
    void doesNotCallWhenThereAreNoPetIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        assertTrue(provider.findOwnedPets(UUID.randomUUID(), List.of()).isEmpty());

        server.verify();
    }

    // pet-service 가 헤더의 계정으로 걸러 내보내므로
    // 물어본 id 중 돌아오지 않은 것은 없거나 남의 반려동물입니다.
    // 무엇이 빠졌는지 보고 작성을 실패시키는 일은 서비스가 합니다.
    @Test
    void omitsPetsThatAreNotOwned() {
        UUID mine = UUID.randomUUID();
        UUID someoneElses = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo(URL_PREFIX + encodedIds(List.of(mine, someoneElses))))
            .andRespond(withSuccess(
                """
                {"code":"SUCCESS","message":"성공","data":[
                  {"petId":"%s","weightKg":28.5,"breedSize":"LARGE","breedName":"골든리트리버"}
                ],"traceId":null}
                """.formatted(mine),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, PetSnapshot> snapshots = provider.findOwnedPets(
            UUID.randomUUID(),
            List.of(mine, someoneElses)
        );

        assertEquals(1, snapshots.size());
        assertTrue(snapshots.containsKey(mine));
        assertFalse(snapshots.containsKey(someoneElses));
        server.verify();
    }

    // 묻지 않은 아이가 섞여 와도 후기에 붙지 않습니다.
    @Test
    void ignoresPetsThatWereNotAsked() {
        UUID asked = UUID.randomUUID();
        UUID notAsked = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo(URL_PREFIX + asked))
            .andRespond(withSuccess(
                """
                {"code":"SUCCESS","message":"성공","data":[
                  {"petId":"%s","weightKg":28.5,"breedSize":"LARGE","breedName":"골든리트리버"},
                  {"petId":"%s","weightKg":3.2,"breedSize":"SMALL","breedName":"말티즈"}
                ],"traceId":null}
                """.formatted(asked, notAsked),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, PetSnapshot> snapshots = provider.findOwnedPets(
            UUID.randomUUID(),
            List.of(asked)
        );

        assertEquals(1, snapshots.size());
        assertTrue(snapshots.containsKey(asked));
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

        server.expect(requestTo(URL_PREFIX + petId))
            .andRespond(withServerError());

        CustomException exception = assertThrows(CustomException.class, () ->
            provider.findOwnedPets(UUID.randomUUID(), List.of(petId)));

        assertEquals(CommonErrorCode.EXTERNAL_API_ERROR, exception.getErrorCode());
        server.verify();
    }

    @Test
    void throwsWhenInternalResponseIsInvalid() {
        UUID petId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PetProviderImpl provider = new PetProviderImpl(builder);

        server.expect(requestTo(URL_PREFIX + petId))
            .andRespond(withSuccess(
                "{\"code\":\"FAIL\",\"message\":\"실패\",\"data\":null}",
                MediaType.APPLICATION_JSON
            ));

        CustomException exception = assertThrows(CustomException.class, () ->
            provider.findOwnedPets(UUID.randomUUID(), List.of(petId)));

        assertEquals(CommonErrorCode.EXTERNAL_API_ERROR, exception.getErrorCode());
        server.verify();
    }

    // 쉼표는 요청 주소에서 %2C 로 인코딩되어 나갑니다.
    private String encodedIds(List<UUID> petIds) {
        return petIds.stream()
            .map(UUID::toString)
            .collect(Collectors.joining("%2C"));
    }
}
