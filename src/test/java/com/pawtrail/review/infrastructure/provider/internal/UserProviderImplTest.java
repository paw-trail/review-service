package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.review.domain.provider.dto.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserProviderImplTest {

    @Test
    void fetchesRequestedUsersInSingleBatch() {
        List<UUID> accountIds = List.of(new UUID(0, 1), new UUID(0, 2));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserProviderImpl provider = new UserProviderImpl(builder);

        UUID firstId = accountIds.getFirst();
        UUID lastId = accountIds.getLast();

        server.expect(requestTo(
                "lb://user-service/internal/users?ids=" + encodedIds(accountIds)
            ))
            .andRespond(withSuccess(responseBody(firstId, lastId), MediaType.APPLICATION_JSON));

        Map<UUID, UserSummary> users = provider.getUsers(accountIds);

        assertEquals("첫 번째 사용자", users.get(firstId).nickname());
        assertEquals("마지막 사용자", users.get(lastId).nickname());
        assertEquals(2, users.size());
        server.verify();
    }

    @Test
    void returnsEmptyMapWithoutRequestForEmptyIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserProviderImpl provider = new UserProviderImpl(builder);

        assertEquals(Map.of(), provider.getUsers(List.of()));
        server.verify();
    }

    // 작성자를 못 받아 와도 예외를 올리지 않습니다.
    // 빈 맵을 돌려주면 부르는 쪽이 작성자만 "알 수 없음" 으로 채우고 목록은 그대로 나갑니다.
    @Test
    void returnsEmptyMapWhenInternalResponseIsInvalid() {
        UUID accountId = UUID.randomUUID();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserProviderImpl provider = new UserProviderImpl(builder);

        server.expect(requestTo(
                "lb://user-service/internal/users?ids=" + accountId
            ))
            .andRespond(withSuccess(
                "{\"code\":\"FAIL\",\"message\":\"실패\",\"data\":null}",
                MediaType.APPLICATION_JSON
            ));

        assertTrue(provider.getUsers(List.of(accountId)).isEmpty());
        server.verify();
    }

    @Test
    void splitsRequestIntoChunksOfOneHundred() {
        List<UUID> accountIds = IntStream.rangeClosed(1, 150)
            .mapToObj(number -> new UUID(0, number))
            .toList();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserProviderImpl provider = new UserProviderImpl(builder);

        List<UUID> firstChunk = accountIds.subList(0, 100);
        List<UUID> secondChunk = accountIds.subList(100, 150);

        server.expect(requestTo(
                "lb://user-service/internal/users?ids=" + encodedIds(firstChunk)
            ))
            .andRespond(withSuccess(
                singleEntryBody(firstChunk.getFirst(), "첫 묶음 사용자"),
                MediaType.APPLICATION_JSON
            ));
        server.expect(requestTo(
                "lb://user-service/internal/users?ids=" + encodedIds(secondChunk)
            ))
            .andRespond(withSuccess(
                singleEntryBody(secondChunk.getFirst(), "둘째 묶음 사용자"),
                MediaType.APPLICATION_JSON
            ));

        Map<UUID, UserSummary> users = provider.getUsers(accountIds);

        assertEquals(2, users.size());
        assertEquals("첫 묶음 사용자", users.get(firstChunk.getFirst()).nickname());
        assertEquals("둘째 묶음 사용자", users.get(secondChunk.getFirst()).nickname());
        server.verify();
    }

    // 한 묶음이라도 실패하면 반쯤 채워진 목록 대신 전부 비웁니다.
    @Test
    void returnsEmptyMapWhenOneChunkFails() {
        List<UUID> accountIds = IntStream.rangeClosed(1, 150)
            .mapToObj(number -> new UUID(0, number))
            .toList();

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserProviderImpl provider = new UserProviderImpl(builder);

        List<UUID> firstChunk = accountIds.subList(0, 100);
        List<UUID> secondChunk = accountIds.subList(100, 150);

        server.expect(requestTo(
                "lb://user-service/internal/users?ids=" + encodedIds(firstChunk)
            ))
            .andRespond(withSuccess(
                singleEntryBody(firstChunk.getFirst(), "첫 묶음 사용자"),
                MediaType.APPLICATION_JSON
            ));
        server.expect(requestTo(
                "lb://user-service/internal/users?ids=" + encodedIds(secondChunk)
            ))
            .andRespond(withServerError());

        assertTrue(provider.getUsers(accountIds).isEmpty());
        server.verify();
    }

    private String encodedIds(List<UUID> accountIds) {
        return accountIds.stream()
            .map(UUID::toString)
            .collect(Collectors.joining("%2C"));
    }

    private String singleEntryBody(UUID accountId, String nickname) {
        return """
            {"code":"SUCCESS","message":"성공","data":[
              {"accountId":"%s","nickname":"%s","profileImageUrl":null}
            ],"traceId":null}
            """.formatted(accountId, nickname);
    }

    private String responseBody(UUID firstId, UUID lastId) {
        return """
            {"code":"SUCCESS","message":"성공","data":[
              {"accountId":"%s","nickname":"첫 번째 사용자","profileImageUrl":null},
              {"accountId":"%s","nickname":"마지막 사용자","profileImageUrl":null}
            ],"traceId":null}
            """.formatted(firstId, lastId);
    }
}
