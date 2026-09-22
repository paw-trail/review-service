package com.pawtrail.review.infrastructure.provider.internal;

import com.pawtrail.review.domain.provider.dto.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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

    private String encodedIds(List<UUID> accountIds) {
        return accountIds.stream()
            .map(UUID::toString)
            .collect(Collectors.joining("%2C"));
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
